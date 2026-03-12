package com.mynosql.query;

import com.mynosql.document.Document;

import java.util.*;

/**
 * Fluent cursor for query results — supports sort, skip, limit, projection.
 * Similar to MongoDB's cursor API.
 */
public class Cursor implements Iterable<Document> {

    private List<Document> documents;
    private Document sortSpec;
    private Document projection;
    private int skipCount;
    private int limitCount;

    public Cursor(List<Document> documents) {
        this.documents = documents;
        this.skipCount = 0;
        this.limitCount = -1;
    }

    /** Sort results. Keys = field names, values = 1 (asc) or -1 (desc). */
    public Cursor sort(Document sortSpec) {
        this.sortSpec = sortSpec;
        return this;
    }

    /** Skip N results. */
    public Cursor skip(int n) {
        this.skipCount = n;
        return this;
    }

    /** Limit results to N. */
    public Cursor limit(int n) {
        this.limitCount = n;
        return this;
    }

    /** Project only certain fields. Keys = field names, values = 1 (include) or 0 (exclude). */
    public Cursor project(Document projection) {
        this.projection = projection;
        return this;
    }

    /** Materialize the cursor into a list. */
    public List<Document> toList() {
        List<Document> result = new ArrayList<>(documents);

        // Sort
        if (sortSpec != null) {
            result.sort((a, b) -> {
                for (String field : sortSpec.keySet()) {
                    int direction = 1;
                    Object dirVal = sortSpec.get(field);
                    if (dirVal instanceof Number n) {
                        direction = n.intValue() >= 0 ? 1 : -1;
                    }
                    int cmp = QueryEngine.compareValues(a.get(field), b.get(field));
                    if (cmp != 0) return cmp * direction;
                }
                return 0;
            });
        }

        // Skip
        if (skipCount > 0) {
            result = result.subList(Math.min(skipCount, result.size()), result.size());
        }

        // Limit
        if (limitCount >= 0) {
            result = result.subList(0, Math.min(limitCount, result.size()));
        }

        // Projection
        if (projection != null) {
            result = applyProjection(result);
        }

        return new ArrayList<>(result);
    }

    /** Get first result or null. */
    public Document first() {
        List<Document> list = toList();
        return list.isEmpty() ? null : list.get(0);
    }

    /** Count results (after filter, before skip/limit). */
    public int count() {
        return documents.size();
    }

    @Override
    public Iterator<Document> iterator() {
        return toList().iterator();
    }

    // ---- Projection ----

    private List<Document> applyProjection(List<Document> docs) {
        boolean isInclusion = false;
        boolean isExclusion = false;

        for (String key : projection.keySet()) {
            if ("_id".equals(key)) continue;
            Object val = projection.get(key);
            int flag = (val instanceof Number n) ? n.intValue() : 1;
            if (flag == 1) isInclusion = true;
            else isExclusion = true;
        }

        List<Document> result = new ArrayList<>();
        for (Document doc : docs) {
            Document projected = new Document();

            if (isInclusion) {
                // Include _id by default unless excluded
                Object idFlag = projection.get("_id");
                if (idFlag == null || toInt(idFlag) != 0) {
                    if (doc.get("_id") != null) projected.put("_id", doc.get("_id"));
                }
                for (String key : projection.keySet()) {
                    if ("_id".equals(key)) continue;
                    if (toInt(projection.get(key)) == 1 && doc.containsKey(key)) {
                        projected.put(key, doc.get(key));
                    }
                }
            } else {
                // Copy everything except excluded fields
                for (String key : doc.keySet()) {
                    Object flag = projection.get(key);
                    if (flag != null && toInt(flag) == 0) continue;
                    projected.put(key, doc.get(key));
                }
            }
            result.add(projected);
        }
        return result;
    }

    private int toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        return 1;
    }
}
