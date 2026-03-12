package com.mynosql.collection;

import com.mynosql.document.Document;
import com.mynosql.index.IndexManager;
import com.mynosql.query.Cursor;
import com.mynosql.query.QueryEngine;
import com.mynosql.storage.StorageEngine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * A collection of JSON documents — the main API for CRUD operations.
 * Equivalent to a MongoDB collection.
 */
public class Collection {

    private final String name;
    private final StorageEngine storage;
    private final IndexManager indexManager;

    public Collection(String name, Path dataDir) throws IOException {
        this.name = name;
        this.storage = new StorageEngine(dataDir, name);
        this.indexManager = new IndexManager();
        rebuildIndexes();
    }

    public String getName() {
        return name;
    }

    // ======== INSERT ========

    /** Insert a single document. Returns the _id. */
    public String insertOne(Document doc) throws IOException {
        String id = storage.insert(doc);
        indexManager.indexDocument(doc);
        return id;
    }

    /** Insert multiple documents. Returns list of _ids. */
    public List<String> insertMany(List<Document> docs) throws IOException {
        List<String> ids = storage.insertMany(docs);
        for (Document doc : docs) {
            indexManager.indexDocument(doc);
        }
        return ids;
    }

    // ======== FIND ========

    /** Find documents matching a query. Returns a Cursor for fluent chaining. */
    public Cursor find(Document query) {
        List<Document> all = storage.findAll();
        List<Document> filtered = QueryEngine.filter(all, query);
        return new Cursor(filtered);
    }

    /** Find all documents. */
    public Cursor find() {
        return new Cursor(storage.findAll());
    }

    /** Find one document matching a query. */
    public Document findOne(Document query) {
        return find(query).limit(1).first();
    }

    /** Find a document by its _id. */
    public Document findById(String id) {
        return storage.findById(id);
    }

    // ======== UPDATE ========

    /**
     * Update one document matching the query.
     * Supports $set, $unset, $inc, $push, $pull, $addToSet, $rename operators.
     * Returns true if a document was modified.
     */
    public boolean updateOne(Document query, Document update) throws IOException {
        Document doc = findOne(query);
        if (doc == null) return false;

        indexManager.removeDocument(doc);
        applyUpdate(doc, update);
        storage.replaceOne(doc.getObjectId(), doc);
        indexManager.indexDocument(doc);
        return true;
    }

    /**
     * Update all documents matching the query.
     * Returns count of modified documents.
     */
    public int updateMany(Document query, Document update) throws IOException {
        List<Document> matches = find(query).toList();
        int count = 0;
        for (Document doc : matches) {
            indexManager.removeDocument(doc);
            applyUpdate(doc, update);
            storage.replaceOne(doc.getObjectId(), doc);
            indexManager.indexDocument(doc);
            count++;
        }
        return count;
    }

    /** Replace one document entirely (keeps _id). */
    public boolean replaceOne(Document query, Document replacement) throws IOException {
        Document doc = findOne(query);
        if (doc == null) return false;
        String id = doc.getObjectId();

        indexManager.removeDocument(doc);
        replacement.put("_id", id);
        storage.replaceOne(id, replacement);
        indexManager.indexDocument(replacement);
        return true;
    }

    // ======== DELETE ========

    /** Delete one document matching the query. Returns true if deleted. */
    public boolean deleteOne(Document query) throws IOException {
        Document doc = findOne(query);
        if (doc == null) return false;
        indexManager.removeDocument(doc);
        return storage.deleteOne(doc.getObjectId());
    }

    /** Delete all documents matching the query. Returns count of deleted. */
    public int deleteMany(Document query) throws IOException {
        List<Document> matches = find(query).toList();
        List<String> ids = new ArrayList<>();
        for (Document doc : matches) {
            indexManager.removeDocument(doc);
            ids.add(doc.getObjectId());
        }
        return storage.deleteMany(ids);
    }

    // ======== INDEXES ========

    public void createIndex(String fieldName, boolean unique) {
        indexManager.createIndex(fieldName, unique);
        rebuildIndexes();
    }

    public void createIndex(String fieldName) {
        createIndex(fieldName, false);
    }

    public boolean dropIndex(String fieldName) {
        return indexManager.dropIndex(fieldName);
    }

    public Set<String> getIndexes() {
        return indexManager.getIndexedFields();
    }

    // ======== AGGREGATION ========

    /** Count documents matching a query. */
    public int countDocuments(Document query) {
        return find(query).count();
    }

    /** Count all documents. */
    public int countDocuments() {
        return storage.count();
    }

    /** Get distinct values for a field, optionally filtered by query. */
    public List<Object> distinct(String fieldName, Document query) {
        List<Document> docs = find(query).toList();
        Set<String> seen = new LinkedHashSet<>();
        List<Object> values = new ArrayList<>();
        for (Document doc : docs) {
            Object val = doc.get(fieldName);
            if (val != null) {
                String key = val.toString();
                if (seen.add(key)) {
                    values.add(val);
                }
            }
        }
        return values;
    }

    // ======== COLLECTION OPS ========

    /** Drop the entire collection. */
    public void drop() throws IOException {
        storage.drop();
        indexManager.clearAll();
    }

    /** Force compaction of storage file. */
    public void compact() throws IOException {
        storage.compact();
    }

    // ======== UPDATE OPERATORS ========

    @SuppressWarnings("unchecked")
    private void applyUpdate(Document doc, Document update) {
        for (String op : update.keySet()) {
            Object value = update.get(op);
            switch (op) {
                case "$set" -> {
                    if (value instanceof Document setDoc) {
                        for (String key : setDoc.keySet()) {
                            doc.put(key, setDoc.get(key));
                        }
                    }
                }
                case "$unset" -> {
                    if (value instanceof Document unsetDoc) {
                        for (String key : unsetDoc.keySet()) {
                            doc.remove(key);
                        }
                    }
                }
                case "$inc" -> {
                    if (value instanceof Document incDoc) {
                        for (String key : incDoc.keySet()) {
                            Object current = doc.get(key);
                            Object incVal = incDoc.get(key);
                            if (incVal instanceof Number incNum) {
                                double currentVal = (current instanceof Number n) ? n.doubleValue() : 0;
                                double result = currentVal + incNum.doubleValue();
                                if (result == Math.floor(result)) {
                                    doc.put(key, (int) result);
                                } else {
                                    doc.put(key, result);
                                }
                            }
                        }
                    }
                }
                case "$push" -> {
                    if (value instanceof Document pushDoc) {
                        for (String key : pushDoc.keySet()) {
                            Object current = doc.get(key);
                            List<Object> list;
                            if (current instanceof List) {
                                list = (List<Object>) current;
                            } else {
                                list = new ArrayList<>();
                                doc.put(key, list);
                            }
                            list.add(pushDoc.get(key));
                        }
                    }
                }
                case "$pull" -> {
                    if (value instanceof Document pullDoc) {
                        for (String key : pullDoc.keySet()) {
                            Object current = doc.get(key);
                            if (current instanceof List<?> list) {
                                Object pullVal = pullDoc.get(key);
                                list.removeIf(item -> QueryEngine.deepEquals(item, pullVal));
                            }
                        }
                    }
                }
                case "$addToSet" -> {
                    if (value instanceof Document addDoc) {
                        for (String key : addDoc.keySet()) {
                            Object current = doc.get(key);
                            List<Object> list;
                            if (current instanceof List) {
                                list = (List<Object>) current;
                            } else {
                                list = new ArrayList<>();
                                doc.put(key, list);
                            }
                            Object addVal = addDoc.get(key);
                            boolean found = false;
                            for (Object item : list) {
                                if (QueryEngine.deepEquals(item, addVal)) { found = true; break; }
                            }
                            if (!found) list.add(addVal);
                        }
                    }
                }
                case "$rename" -> {
                    if (value instanceof Document renameDoc) {
                        for (String oldKey : renameDoc.keySet()) {
                            String newKey = renameDoc.getString(oldKey);
                            if (doc.containsKey(oldKey) && newKey != null) {
                                Object val = doc.get(oldKey);
                                doc.remove(oldKey);
                                doc.put(newKey, val);
                            }
                        }
                    }
                }
                default -> {
                    // If not an operator, treat as $set shorthand
                    if (!op.startsWith("$")) {
                        doc.put(op, value);
                    }
                }
            }
        }
    }

    private void rebuildIndexes() {
        indexManager.rebuildAll(storage.findAll());
    }
}
