package com.mynosql.query;

import com.mynosql.document.Document;

import java.util.*;
import java.util.regex.Pattern;

/**
 * MongoDB-compatible query engine.
 *
 * Supported operators:
 *   Comparison: $eq, $ne, $gt, $gte, $lt, $lte, $in, $nin
 *   Logical:    $and, $or, $not, $nor
 *   Element:    $exists, $type
 *   Evaluation: $regex
 *   Array:      $all, $size, $elemMatch
 *
 * Supports dot-notation for nested field access.
 */
public class QueryEngine {

    /**
     * Evaluate whether a document matches a query filter.
     *
     * @param doc   the document to test
     * @param query the filter (e.g., {"age": {"$gt": 25}, "status": "active"})
     * @return true if the document matches
     */
    public static boolean matches(Document doc, Document query) {
        if (query == null || query.keySet().isEmpty()) return true;

        for (String key : query.keySet()) {
            Object condition = query.get(key);

            // Logical operators at top level
            switch (key) {
                case "$and" -> {
                    if (!evalAnd(doc, condition)) return false;
                    continue;
                }
                case "$or" -> {
                    if (!evalOr(doc, condition)) return false;
                    continue;
                }
                case "$nor" -> {
                    if (!evalNor(doc, condition)) return false;
                    continue;
                }
                case "$not" -> {
                    if (condition instanceof Document notQuery) {
                        if (matches(doc, notQuery)) return false;
                    }
                    continue;
                }
            }

            // Field-level match
            if (!matchesField(doc, key, condition)) return false;
        }
        return true;
    }

    /**
     * Filter a list of documents using a query.
     */
    public static List<Document> filter(List<Document> documents, Document query) {
        if (query == null || query.keySet().isEmpty()) return new ArrayList<>(documents);
        List<Document> results = new ArrayList<>();
        for (Document doc : documents) {
            if (matches(doc, query)) {
                results.add(doc);
            }
        }
        return results;
    }

    // ---- Field matching ----

    private static boolean matchesField(Document doc, String field, Object condition) {
        Object docValue = doc.get(field);

        // If condition is a Document, it may contain operators
        if (condition instanceof Document condDoc) {
            boolean hasOperators = false;
            for (String key : condDoc.keySet()) {
                if (key.startsWith("$")) {
                    hasOperators = true;
                    if (!evalOperator(docValue, key, condDoc.get(key))) return false;
                }
            }
            if (hasOperators) return true;
            // If no operators, it's an exact sub-document match
            return deepEquals(docValue, condition);
        }

        // Simple equality
        return deepEquals(docValue, condition);
    }

    // ---- Operator evaluation ----

    private static boolean evalOperator(Object docValue, String operator, Object operand) {
        return switch (operator) {
            case "$eq" -> deepEquals(docValue, operand);
            case "$ne" -> !deepEquals(docValue, operand);
            case "$gt" -> compareValues(docValue, operand) > 0;
            case "$gte" -> compareValues(docValue, operand) >= 0;
            case "$lt" -> compareValues(docValue, operand) < 0;
            case "$lte" -> compareValues(docValue, operand) <= 0;
            case "$in" -> evalIn(docValue, operand);
            case "$nin" -> !evalIn(docValue, operand);
            case "$exists" -> evalExists(docValue, operand);
            case "$type" -> evalType(docValue, operand);
            case "$regex" -> evalRegex(docValue, operand);
            case "$all" -> evalAll(docValue, operand);
            case "$size" -> evalSize(docValue, operand);
            case "$elemMatch" -> evalElemMatch(docValue, operand);
            case "$not" -> !evalOperatorGroup(docValue, operand);
            default -> false;
        };
    }

    private static boolean evalOperatorGroup(Object docValue, Object operand) {
        if (operand instanceof Document opDoc) {
            for (String key : opDoc.keySet()) {
                if (key.startsWith("$")) {
                    if (!evalOperator(docValue, key, opDoc.get(key))) return false;
                }
            }
            return true;
        }
        return deepEquals(docValue, operand);
    }

    // ---- Logical operators ----

    @SuppressWarnings("unchecked")
    private static boolean evalAnd(Document doc, Object conditions) {
        if (conditions instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Document query) {
                    if (!matches(doc, query)) return false;
                }
            }
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean evalOr(Document doc, Object conditions) {
        if (conditions instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Document query) {
                    if (matches(doc, query)) return true;
                }
            }
            return false;
        }
        return false;
    }

    private static boolean evalNor(Document doc, Object conditions) {
        if (conditions instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Document query) {
                    if (matches(doc, query)) return false;
                }
            }
            return true;
        }
        return false;
    }

    // ---- Specific operators ----

    private static boolean evalIn(Object docValue, Object operand) {
        if (!(operand instanceof List<?> list)) return false;

        // If docValue is an array, check if any element is in the list
        if (docValue instanceof List<?> docList) {
            for (Object item : docList) {
                for (Object inVal : list) {
                    if (deepEquals(item, inVal)) return true;
                }
            }
            return false;
        }

        for (Object inVal : list) {
            if (deepEquals(docValue, inVal)) return true;
        }
        return false;
    }

    private static boolean evalExists(Object docValue, Object operand) {
        boolean shouldExist = toBool(operand);
        return shouldExist ? (docValue != null) : (docValue == null);
    }

    private static boolean evalType(Object docValue, Object operand) {
        if (docValue == null) return "null".equals(operand);
        String typeName = operand.toString().toLowerCase();
        return switch (typeName) {
            case "string" -> docValue instanceof String;
            case "number", "int", "double", "long" -> docValue instanceof Number;
            case "boolean", "bool" -> docValue instanceof Boolean;
            case "object" -> docValue instanceof Document;
            case "array" -> docValue instanceof List;
            case "null" -> false; // already checked above
            default -> false;
        };
    }

    private static boolean evalRegex(Object docValue, Object operand) {
        if (docValue == null || operand == null) return false;
        String pattern = operand.toString();
        String value = docValue.toString();
        try {
            return Pattern.compile(pattern).matcher(value).find();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean evalAll(Object docValue, Object operand) {
        if (!(docValue instanceof List<?> docList) || !(operand instanceof List<?> allValues)) return false;
        for (Object required : allValues) {
            boolean found = false;
            for (Object item : docList) {
                if (deepEquals(item, required)) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    private static boolean evalSize(Object docValue, Object operand) {
        if (!(docValue instanceof List<?> list)) return false;
        if (operand instanceof Number n) {
            return list.size() == n.intValue();
        }
        return false;
    }

    private static boolean evalElemMatch(Object docValue, Object operand) {
        if (!(docValue instanceof List<?> list) || !(operand instanceof Document query)) return false;
        for (Object item : list) {
            if (item instanceof Document itemDoc) {
                if (matches(itemDoc, query)) return true;
            }
        }
        return false;
    }

    // ---- Utility ----

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int compareValues(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;

        // Numbers: compare as doubles
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue());
        }
        // Strings
        if (a instanceof String sa && b instanceof String sb) {
            return sa.compareTo(sb);
        }
        // Booleans
        if (a instanceof Boolean ba && b instanceof Boolean bb) {
            return Boolean.compare(ba, bb);
        }
        // Mixed: compare string representations
        if (a instanceof Comparable ca && b instanceof Comparable cb) {
            try {
                return ca.compareTo(cb);
            } catch (ClassCastException e) {
                return a.toString().compareTo(b.toString());
            }
        }
        return a.toString().compareTo(b.toString());
    }

    public static boolean deepEquals(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;

        // Numeric comparison (int 5 == double 5.0)
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue()) == 0;
        }

        if (a instanceof Document da && b instanceof Document db) {
            return da.equals(db);
        }
        if (a instanceof List<?> la && b instanceof List<?> lb) {
            if (la.size() != lb.size()) return false;
            for (int i = 0; i < la.size(); i++) {
                if (!deepEquals(la.get(i), lb.get(i))) return false;
            }
            return true;
        }
        return a.equals(b);
    }

    private static boolean toBool(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return value != null;
    }
}
