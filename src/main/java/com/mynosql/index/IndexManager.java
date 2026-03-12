package com.mynosql.index;

import com.mynosql.document.Document;

import java.util.*;

/**
 * Manages all indexes for a collection.
 * The _id field always has a unique index.
 */
public class IndexManager {

    private final Map<String, BTreeIndex> indexes;

    public IndexManager() {
        this.indexes = new LinkedHashMap<>();
        // _id always has a unique index
        indexes.put("_id", new BTreeIndex("_id", true));
    }

    /** Create an index on a field. */
    public void createIndex(String fieldName, boolean unique) {
        if (indexes.containsKey(fieldName)) return;
        indexes.put(fieldName, new BTreeIndex(fieldName, unique));
    }

    /** Drop an index (cannot drop _id index). */
    public boolean dropIndex(String fieldName) {
        if ("_id".equals(fieldName)) return false;
        return indexes.remove(fieldName) != null;
    }

    /** Index a document across all indexes. */
    public void indexDocument(Document doc) {
        String id = doc.getObjectId();
        for (Map.Entry<String, BTreeIndex> entry : indexes.entrySet()) {
            Object value = doc.get(entry.getKey());
            if (value != null) {
                entry.getValue().insert(value, id);
            }
        }
    }

    /** Remove a document from all indexes. */
    public void removeDocument(Document doc) {
        String id = doc.getObjectId();
        for (Map.Entry<String, BTreeIndex> entry : indexes.entrySet()) {
            Object value = doc.get(entry.getKey());
            if (value != null) {
                entry.getValue().remove(value, id);
            }
        }
    }

    /** Get index for a field, or null if not indexed. */
    public BTreeIndex getIndex(String fieldName) {
        return indexes.get(fieldName);
    }

    public boolean hasIndex(String fieldName) {
        return indexes.containsKey(fieldName);
    }

    public Set<String> getIndexedFields() {
        return Collections.unmodifiableSet(indexes.keySet());
    }

    /** Clear all indexes. */
    public void clearAll() {
        for (BTreeIndex index : indexes.values()) {
            index.clear();
        }
    }

    /** Rebuild all indexes from a set of documents. */
    public void rebuildAll(Collection<Document> documents) {
        clearAll();
        for (Document doc : documents) {
            indexDocument(doc);
        }
    }
}
