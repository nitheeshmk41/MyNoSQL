package com.mynosql.database;

import com.mynosql.collection.Collection;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A database — contains named collections.
 * Each database is a directory; each collection is a file within it.
 */
public class Database {

    private final String name;
    private final Path dataDir;
    private final Map<String, Collection> collections;

    public Database(String name, Path baseDir) throws IOException {
        this.name = name;
        this.dataDir = baseDir.resolve(name);
        Files.createDirectories(dataDir);
        this.collections = new ConcurrentHashMap<>();
        loadExistingCollections();
    }

    public String getName() {
        return name;
    }

    /** Get or create a collection by name. */
    public Collection getCollection(String collectionName) throws IOException {
        return collections.computeIfAbsent(collectionName, cn -> {
            try {
                return new Collection(cn, dataDir);
            } catch (IOException e) {
                throw new RuntimeException("Failed to open collection: " + cn, e);
            }
        });
    }

    /** List all collection names. */
    public Set<String> listCollections() {
        return Collections.unmodifiableSet(collections.keySet());
    }

    /** Drop a collection. */
    public boolean dropCollection(String collectionName) throws IOException {
        Collection col = collections.remove(collectionName);
        if (col != null) {
            col.drop();
            return true;
        }
        return false;
    }

    /** Drop the entire database. */
    public void drop() throws IOException {
        for (Collection col : collections.values()) {
            col.drop();
        }
        collections.clear();
        // Remove directory
        try (var stream = Files.walk(dataDir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        }
    }

    /** Get stats about the database. */
    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("db", name);
        stats.put("collections", collections.size());
        int totalDocs = 0;
        for (Collection col : collections.values()) {
            totalDocs += col.countDocuments();
        }
        stats.put("documents", totalDocs);
        return stats;
    }

    // ---- Internal ----

    private void loadExistingCollections() throws IOException {
        if (!Files.exists(dataDir)) return;
        try (var stream = Files.list(dataDir)) {
            stream.filter(p -> p.toString().endsWith(".ndjson"))
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        String colName = fileName.substring(0, fileName.length() - ".ndjson".length());
                        try {
                            collections.put(colName, new Collection(colName, dataDir));
                        } catch (IOException e) {
                            System.err.println("Warning: failed to load collection " + colName + ": " + e.getMessage());
                        }
                    });
        }
    }
}
