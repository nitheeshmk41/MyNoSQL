package com.mynosql.index;

import java.util.*;

/**
 * In-memory B-Tree index for fast field lookups.
 * Maps field values -> set of document _ids.
 * Supports unique and non-unique indexes, range queries.
 */
public class BTreeIndex {

    private final String fieldName;
    private final boolean unique;
    private final TreeMap<Comparable<?>, Set<String>> tree;  // value -> set of _ids

    public BTreeIndex(String fieldName, boolean unique) {
        this.fieldName = fieldName;
        this.unique = unique;
        this.tree = new TreeMap<>(BTreeIndex::compareValues);
    }

    public String getFieldName() {
        return fieldName;
    }

    public boolean isUnique() {
        return unique;
    }

    /** Add a value -> _id mapping. Throws if unique index is violated. */
    public void insert(Object value, String documentId) {
        if (value == null) return;
        @SuppressWarnings("unchecked")
        Comparable<Object> key = (Comparable<Object>) toComparable(value);

        Set<String> ids = tree.computeIfAbsent(key, k -> new LinkedHashSet<>());
        if (unique && !ids.isEmpty() && !ids.contains(documentId)) {
            throw new IllegalStateException(
                    "Unique index violation on field '" + fieldName + "' for value: " + value);
        }
        ids.add(documentId);
    }

    /** Remove a value -> _id mapping. */
    public void remove(Object value, String documentId) {
        if (value == null) return;
        @SuppressWarnings("unchecked")
        Comparable<Object> key = (Comparable<Object>) toComparable(value);
        Set<String> ids = tree.get(key);
        if (ids != null) {
            ids.remove(documentId);
            if (ids.isEmpty()) tree.remove(key);
        }
    }

    /** Exact match: find all _ids where field == value. */
    public Set<String> findEqual(Object value) {
        if (value == null) return Collections.emptySet();
        @SuppressWarnings("unchecked")
        Comparable<Object> key = (Comparable<Object>) toComparable(value);
        Set<String> ids = tree.get(key);
        return ids != null ? Collections.unmodifiableSet(ids) : Collections.emptySet();
    }

    /** Range query: find _ids where field > value. */
    @SuppressWarnings("unchecked")
    public Set<String> findGreaterThan(Object value, boolean inclusive) {
        Comparable<Object> key = (Comparable<Object>) toComparable(value);
        NavigableMap<Comparable<?>, Set<String>> sub = inclusive
                ? tree.tailMap(key, true)
                : tree.tailMap(key, false);
        return collectIds(sub);
    }

    /** Range query: find _ids where field < value. */
    @SuppressWarnings("unchecked")
    public Set<String> findLessThan(Object value, boolean inclusive) {
        Comparable<Object> key = (Comparable<Object>) toComparable(value);
        NavigableMap<Comparable<?>, Set<String>> sub = inclusive
                ? tree.headMap(key, true)
                : tree.headMap(key, false);
        return collectIds(sub);
    }

    /** Range query: find _ids where lower <= field <= upper. */
    @SuppressWarnings("unchecked")
    public Set<String> findBetween(Object lower, Object upper, boolean lowerInclusive, boolean upperInclusive) {
        Comparable<Object> lo = (Comparable<Object>) toComparable(lower);
        Comparable<Object> hi = (Comparable<Object>) toComparable(upper);
        NavigableMap<Comparable<?>, Set<String>> sub = tree.subMap(lo, lowerInclusive, hi, upperInclusive);
        return collectIds(sub);
    }

    /** Clear the entire index. */
    public void clear() {
        tree.clear();
    }

    public int size() {
        return tree.size();
    }

    // ---- Internal ----

    private Set<String> collectIds(Map<Comparable<?>, Set<String>> map) {
        Set<String> result = new LinkedHashSet<>();
        for (Set<String> ids : map.values()) {
            result.addAll(ids);
        }
        return result;
    }

    private static Object toComparable(Object value) {
        if (value instanceof Comparable<?>) return value;
        return value.toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareValues(Comparable a, Comparable b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;

        // Normalize numbers for comparison
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }

        // Same type: direct compare
        if (a.getClass() == b.getClass()) {
            return a.compareTo(b);
        }

        // Fallback: compare as strings
        return a.toString().compareTo(b.toString());
    }
}
