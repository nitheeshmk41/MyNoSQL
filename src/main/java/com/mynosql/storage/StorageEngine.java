package com.mynosql.storage;

import com.mynosql.document.Document;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Disk-backed storage engine for a single collection.
 *
 * Storage format: one JSON document per line (newline-delimited JSON).
 * Maintains an in-memory map (_id -> Document) for fast lookups.
 * Writes are append-only; compaction rewrites the file removing deleted/updated docs.
 */
public class StorageEngine {

    private final Path dataFile;
    private final Map<String, Document> documents;  // _id -> Document
    private final ReadWriteLock lock;
    private int dirtyCount;  // tracks deletes/updates for compaction trigger

    private static final int COMPACTION_THRESHOLD = 1000;

    public StorageEngine(Path dataDir, String collectionName) throws IOException {
        Files.createDirectories(dataDir);
        this.dataFile = dataDir.resolve(collectionName + ".ndjson");
        this.documents = new LinkedHashMap<>();
        this.lock = new ReentrantReadWriteLock();
        this.dirtyCount = 0;
        loadFromDisk();
    }

    /** Insert a document. Assigns _id if missing. Returns the _id. */
    public String insert(Document doc) throws IOException {
        doc.ensureId();
        String id = doc.getObjectId();

        lock.writeLock().lock();
        try {
            if (documents.containsKey(id)) {
                throw new IllegalArgumentException("Duplicate _id: " + id);
            }
            documents.put(id, doc.deepCopy());
            appendToDisk(doc);
        } finally {
            lock.writeLock().unlock();
        }
        return id;
    }

    /** Insert multiple documents. Returns list of _ids. */
    public List<String> insertMany(List<Document> docs) throws IOException {
        List<String> ids = new ArrayList<>();
        lock.writeLock().lock();
        try {
            StringBuilder batch = new StringBuilder();
            for (Document doc : docs) {
                doc.ensureId();
                String id = doc.getObjectId();
                if (documents.containsKey(id)) {
                    throw new IllegalArgumentException("Duplicate _id: " + id);
                }
                documents.put(id, doc.deepCopy());
                batch.append(doc.toJson()).append("\n");
                ids.add(id);
            }
            Files.writeString(dataFile, batch.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } finally {
            lock.writeLock().unlock();
        }
        return ids;
    }

    /** Find a document by _id. */
    public Document findById(String id) {
        lock.readLock().lock();
        try {
            Document doc = documents.get(id);
            return doc != null ? doc.deepCopy() : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Return all documents (copies). */
    public List<Document> findAll() {
        lock.readLock().lock();
        try {
            List<Document> result = new ArrayList<>(documents.size());
            for (Document doc : documents.values()) {
                result.add(doc.deepCopy());
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Replace a document entirely (must keep same _id). */
    public boolean replaceOne(String id, Document replacement) throws IOException {
        lock.writeLock().lock();
        try {
            if (!documents.containsKey(id)) return false;
            replacement.put("_id", id);
            documents.put(id, replacement.deepCopy());
            dirtyCount++;
            maybeCompact();
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Update fields in a document (partial update). */
    public boolean updateOne(String id, Document updateFields) throws IOException {
        lock.writeLock().lock();
        try {
            Document existing = documents.get(id);
            if (existing == null) return false;
            for (String key : updateFields.keySet()) {
                if (!key.equals("_id")) {
                    existing.put(key, updateFields.get(key));
                }
            }
            dirtyCount++;
            maybeCompact();
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Delete a document by _id. Returns true if found and deleted. */
    public boolean deleteOne(String id) throws IOException {
        lock.writeLock().lock();
        try {
            if (documents.remove(id) == null) return false;
            dirtyCount++;
            maybeCompact();
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Delete multiple documents by _ids. Returns count of deleted. */
    public int deleteMany(List<String> ids) throws IOException {
        lock.writeLock().lock();
        try {
            int count = 0;
            for (String id : ids) {
                if (documents.remove(id) != null) count++;
            }
            if (count > 0) {
                dirtyCount += count;
                maybeCompact();
            }
            return count;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Total number of documents. */
    public int count() {
        lock.readLock().lock();
        try {
            return documents.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Force compaction — rewrites the data file with only current documents. */
    public void compact() throws IOException {
        lock.writeLock().lock();
        try {
            Path tmpFile = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(tmpFile, StandardCharsets.UTF_8)) {
                for (Document doc : documents.values()) {
                    writer.write(doc.toJson());
                    writer.newLine();
                }
            }
            Files.move(tmpFile, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            dirtyCount = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Drop the collection (delete file and clear memory). */
    public void drop() throws IOException {
        lock.writeLock().lock();
        try {
            documents.clear();
            Files.deleteIfExists(dataFile);
            dirtyCount = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ---- Internal ----

    private void loadFromDisk() throws IOException {
        if (!Files.exists(dataFile)) return;

        // Read all lines, keeping last occurrence of each _id (handles append-only updates)
        try (BufferedReader reader = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    Document doc = Document.fromJson(line);
                    String id = doc.getObjectId();
                    if (id != null) {
                        documents.put(id, doc);
                    }
                } catch (Exception e) {
                    System.err.println("Warning: skipping corrupt line in " + dataFile + ": " + e.getMessage());
                }
            }
        }
    }

    private void appendToDisk(Document doc) throws IOException {
        Files.writeString(dataFile, doc.toJson() + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private void maybeCompact() throws IOException {
        if (dirtyCount >= COMPACTION_THRESHOLD) {
            compact();
        }
    }
}
