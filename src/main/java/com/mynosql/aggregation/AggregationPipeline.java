package com.mynosql.aggregation;

import com.mynosql.document.Document;
import com.mynosql.query.QueryEngine;

import java.util.*;

/**
 * MongoDB-style aggregation pipeline.
 *
 * Supported stages:
 *   $match   - Filter documents
 *   $group   - Group by field, with accumulators ($sum, $avg, $min, $max, $count, $push, $first, $last)
 *   $sort    - Sort documents
 *   $limit   - Limit output count
 *   $skip    - Skip N documents
 *   $project - Reshape / select fields
 *   $unwind  - Deconstruct an array field
 *   $count   - Count documents into a named field
 *   $lookup  - Left outer join (requires resolver function)
 *   $addFields - Add computed fields
 */
public class AggregationPipeline {

    private final List<Document> stages;
    private LookupResolver lookupResolver;

    @FunctionalInterface
    public interface LookupResolver {
        List<Document> resolve(String collection);
    }

    public AggregationPipeline() {
        this.stages = new ArrayList<>();
    }

    public AggregationPipeline setLookupResolver(LookupResolver resolver) {
        this.lookupResolver = resolver;
        return this;
    }

    public AggregationPipeline addStage(Document stage) {
        stages.add(stage);
        return this;
    }

    /** Convenience builders */
    public AggregationPipeline match(Document filter) {
        return addStage(new Document("$match", filter));
    }

    public AggregationPipeline group(Document groupSpec) {
        return addStage(new Document("$group", groupSpec));
    }

    public AggregationPipeline sort(Document sortSpec) {
        return addStage(new Document("$sort", sortSpec));
    }

    public AggregationPipeline limit(int n) {
        return addStage(new Document("$limit", n));
    }

    public AggregationPipeline skip(int n) {
        return addStage(new Document("$skip", n));
    }

    public AggregationPipeline project(Document projection) {
        return addStage(new Document("$project", projection));
    }

    public AggregationPipeline unwind(String field) {
        return addStage(new Document("$unwind", field));
    }

    public AggregationPipeline count(String outputField) {
        return addStage(new Document("$count", outputField));
    }

    public AggregationPipeline addFields(Document fields) {
        return addStage(new Document("$addFields", fields));
    }

    /** Execute the pipeline on a set of input documents. */
    public List<Document> execute(List<Document> input) {
        List<Document> current = new ArrayList<>(input);

        for (Document stage : stages) {
            for (String stageOp : stage.keySet()) {
                Object stageSpec = stage.get(stageOp);
                current = switch (stageOp) {
                    case "$match" -> execMatch(current, (Document) stageSpec);
                    case "$group" -> execGroup(current, (Document) stageSpec);
                    case "$sort" -> execSort(current, (Document) stageSpec);
                    case "$limit" -> execLimit(current, ((Number) stageSpec).intValue());
                    case "$skip" -> execSkip(current, ((Number) stageSpec).intValue());
                    case "$project" -> execProject(current, (Document) stageSpec);
                    case "$unwind" -> execUnwind(current, stageSpec.toString());
                    case "$count" -> execCount(current, stageSpec.toString());
                    case "$addFields" -> execAddFields(current, (Document) stageSpec);
                    case "$lookup" -> execLookup(current, (Document) stageSpec);
                    default -> current;
                };
                break; // Each stage document should have exactly one key
            }
        }
        return current;
    }

    // ===== Stage implementations =====

    private List<Document> execMatch(List<Document> input, Document filter) {
        return QueryEngine.filter(input, filter);
    }

    private List<Document> execGroup(List<Document> input, Document groupSpec) {
        Object idSpec = groupSpec.get("_id");
        Map<String, List<Document>> groups = new LinkedHashMap<>();

        for (Document doc : input) {
            String groupKey = resolveGroupKey(doc, idSpec);
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(doc);
        }

        List<Document> results = new ArrayList<>();
        for (Map.Entry<String, List<Document>> entry : groups.entrySet()) {
            Document result = new Document();
            result.put("_id", parseGroupKeyValue(entry.getKey(), idSpec));

            // Apply accumulators
            for (String field : groupSpec.keySet()) {
                if ("_id".equals(field)) continue;
                Object accSpec = groupSpec.get(field);
                if (accSpec instanceof Document accDoc) {
                    result.put(field, applyAccumulator(entry.getValue(), accDoc));
                }
            }
            results.add(result);
        }
        return results;
    }

    private List<Document> execSort(List<Document> input, Document sortSpec) {
        List<Document> sorted = new ArrayList<>(input);
        sorted.sort((a, b) -> {
            for (String field : sortSpec.keySet()) {
                int direction = ((Number) sortSpec.get(field)).intValue();
                int cmp = QueryEngine.compareValues(a.get(field), b.get(field));
                if (cmp != 0) return cmp * direction;
            }
            return 0;
        });
        return sorted;
    }

    private List<Document> execLimit(List<Document> input, int n) {
        return input.subList(0, Math.min(n, input.size()));
    }

    private List<Document> execSkip(List<Document> input, int n) {
        return input.subList(Math.min(n, input.size()), input.size());
    }

    private List<Document> execProject(List<Document> input, Document projection) {
        List<Document> results = new ArrayList<>();
        for (Document doc : input) {
            Document projected = new Document();
            boolean inclusion = false;
            for (String key : projection.keySet()) {
                if ("_id".equals(key)) continue;
                Object val = projection.get(key);
                if (val instanceof Number n && n.intValue() == 1) inclusion = true;
            }

            if (inclusion) {
                Object idFlag = projection.get("_id");
                if (idFlag == null || toInt(idFlag) != 0) {
                    if (doc.get("_id") != null) projected.put("_id", doc.get("_id"));
                }
                for (String key : projection.keySet()) {
                    if ("_id".equals(key)) continue;
                    Object val = projection.get(key);
                    if (val instanceof Number n && n.intValue() == 1) {
                        if (doc.containsKey(key)) projected.put(key, doc.get(key));
                    } else if (val instanceof String fieldRef) {
                        // Field reference: "$fieldName"
                        if (fieldRef.startsWith("$")) {
                            projected.put(key, doc.get(fieldRef.substring(1)));
                        } else {
                            projected.put(key, val);
                        }
                    }
                }
            } else {
                for (String key : doc.keySet()) {
                    Object flag = projection.get(key);
                    if (flag != null && toInt(flag) == 0) continue;
                    projected.put(key, doc.get(key));
                }
            }
            results.add(projected);
        }
        return results;
    }

    private List<Document> execUnwind(List<Document> input, String field) {
        String fieldName = field.startsWith("$") ? field.substring(1) : field;
        List<Document> results = new ArrayList<>();
        for (Document doc : input) {
            Object value = doc.get(fieldName);
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    Document copy = doc.deepCopy();
                    copy.put(fieldName, item);
                    results.add(copy);
                }
            } else {
                results.add(doc.deepCopy());
            }
        }
        return results;
    }

    private List<Document> execCount(List<Document> input, String outputField) {
        Document result = new Document(outputField, input.size());
        return List.of(result);
    }

    private List<Document> execAddFields(List<Document> input, Document fields) {
        List<Document> results = new ArrayList<>();
        for (Document doc : input) {
            Document copy = doc.deepCopy();
            for (String key : fields.keySet()) {
                Object val = fields.get(key);
                if (val instanceof String s && s.startsWith("$")) {
                    copy.put(key, doc.get(s.substring(1)));
                } else {
                    copy.put(key, val);
                }
            }
            results.add(copy);
        }
        return results;
    }

    private List<Document> execLookup(List<Document> input, Document lookupSpec) {
        if (lookupResolver == null) return input;

        String from = lookupSpec.getString("from");
        String localField = lookupSpec.getString("localField");
        String foreignField = lookupSpec.getString("foreignField");
        String as = lookupSpec.getString("as");

        if (from == null || localField == null || foreignField == null || as == null) return input;

        List<Document> foreignDocs = lookupResolver.resolve(from);
        List<Document> results = new ArrayList<>();

        for (Document doc : input) {
            Document copy = doc.deepCopy();
            Object localVal = copy.get(localField);
            List<Object> matched = new ArrayList<>();

            for (Document foreign : foreignDocs) {
                Object foreignVal = foreign.get(foreignField);
                if (QueryEngine.deepEquals(localVal, foreignVal)) {
                    matched.add(foreign.deepCopy());
                }
            }
            copy.put(as, matched);
            results.add(copy);
        }
        return results;
    }

    // ===== Accumulator logic =====

    private Object applyAccumulator(List<Document> docs, Document accDoc) {
        for (String accOp : accDoc.keySet()) {
            Object fieldRef = accDoc.get(accOp);
            String fieldName = (fieldRef instanceof String s && s.startsWith("$"))
                    ? s.substring(1) : null;

            return switch (accOp) {
                case "$sum" -> {
                    if (fieldRef instanceof Number n) {
                        yield n.doubleValue() * docs.size();
                    }
                    double sum = 0;
                    for (Document doc : docs) {
                        Object val = fieldName != null ? doc.get(fieldName) : null;
                        if (val instanceof Number n) sum += n.doubleValue();
                    }
                    yield sum == Math.floor(sum) ? (int) sum : sum;
                }
                case "$avg" -> {
                    double sum = 0;
                    int count = 0;
                    for (Document doc : docs) {
                        Object val = fieldName != null ? doc.get(fieldName) : null;
                        if (val instanceof Number n) { sum += n.doubleValue(); count++; }
                    }
                    yield count > 0 ? sum / count : 0;
                }
                case "$min" -> {
                    Object min = null;
                    for (Document doc : docs) {
                        Object val = fieldName != null ? doc.get(fieldName) : null;
                        if (val != null && (min == null || QueryEngine.compareValues(val, min) < 0)) {
                            min = val;
                        }
                    }
                    yield min;
                }
                case "$max" -> {
                    Object max = null;
                    for (Document doc : docs) {
                        Object val = fieldName != null ? doc.get(fieldName) : null;
                        if (val != null && (max == null || QueryEngine.compareValues(val, max) > 0)) {
                            max = val;
                        }
                    }
                    yield max;
                }
                case "$count" -> docs.size();
                case "$push" -> {
                    List<Object> values = new ArrayList<>();
                    for (Document doc : docs) {
                        Object val = fieldName != null ? doc.get(fieldName) : null;
                        if (val != null) values.add(val);
                    }
                    yield values;
                }
                case "$first" -> {
                    yield !docs.isEmpty() && fieldName != null ? docs.get(0).get(fieldName) : null;
                }
                case "$last" -> {
                    yield !docs.isEmpty() && fieldName != null
                            ? docs.get(docs.size() - 1).get(fieldName) : null;
                }
                default -> null;
            };
        }
        return null;
    }

    // ===== Helpers =====

    private String resolveGroupKey(Document doc, Object idSpec) {
        if (idSpec == null) return "__null__";
        if (idSpec instanceof String s) {
            if (s.startsWith("$")) return String.valueOf(doc.get(s.substring(1)));
            return s;
        }
        if (idSpec instanceof Document idDoc) {
            StringBuilder sb = new StringBuilder();
            for (String key : idDoc.keySet()) {
                Object ref = idDoc.get(key);
                if (ref instanceof String s && s.startsWith("$")) {
                    sb.append(key).append("=").append(doc.get(s.substring(1))).append("|");
                }
            }
            return sb.toString();
        }
        return idSpec.toString();
    }

    private Object parseGroupKeyValue(String key, Object idSpec) {
        if ("__null__".equals(key)) return null;
        if (idSpec instanceof String) return key;
        return key;
    }

    private int toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        return 1;
    }
}
