package com.mynosql.document;

import com.google.gson.*;

import java.util.*;

/**
 * Core document class — represents a JSON/BSON-like document.
 * Supports nested documents, arrays, and all JSON types.
 * Every document gets an auto-generated _id (ObjectId) if not provided.
 */
public class Document {

    private final Map<String, Object> fields;

    public Document() {
        this.fields = new LinkedHashMap<>();
    }

    public Document(String key, Object value) {
        this();
        put(key, value);
    }

    public Document(Map<String, Object> map) {
        this.fields = new LinkedHashMap<>(map);
    }

    /** Fluent put — returns this for chaining: new Document("name","Alice").append("age",30) */
    public Document append(String key, Object value) {
        put(key, value);
        return this;
    }

    public void put(String key, Object value) {
        fields.put(key, normalize(value));
    }

    public Object get(String key) {
        // First try exact key match (handles flat keys like "address.city" in query docs)
        if (fields.containsKey(key)) {
            return fields.get(key);
        }
        if (!key.contains(".")) {
            return null;
        }
        // Dot-notation: "address.city" -> nested lookup
        String[] parts = key.split("\\.", 2);
        Object child = fields.get(parts[0]);
        if (child instanceof Document doc) {
            return doc.get(parts[1]);
        }
        if (child instanceof Map<?, ?> map) {
            return new Document(castMap(map)).get(parts[1]);
        }
        return null;
    }

    public String getString(String key) {
        Object v = get(key);
        return v != null ? v.toString() : null;
    }

    public Integer getInteger(String key) {
        Object v = get(key);
        if (v instanceof Number n) return n.intValue();
        return null;
    }

    public Double getDouble(String key) {
        Object v = get(key);
        if (v instanceof Number n) return n.doubleValue();
        return null;
    }

    public Boolean getBoolean(String key) {
        Object v = get(key);
        if (v instanceof Boolean b) return b;
        return null;
    }

    public Document getDocument(String key) {
        Object v = get(key);
        if (v instanceof Document d) return d;
        if (v instanceof Map<?, ?> m) return new Document(castMap(m));
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<Object> getList(String key) {
        Object v = get(key);
        if (v instanceof List<?> l) return (List<Object>) l;
        return null;
    }

    public boolean containsKey(String key) {
        if (fields.containsKey(key)) return true;
        if (!key.contains(".")) return false;
        return get(key) != null;
    }

    public void remove(String key) {
        fields.remove(key);
    }

    public Set<String> keySet() {
        return fields.keySet();
    }

    public Map<String, Object> toMap() {
        return Collections.unmodifiableMap(fields);
    }

    public String getObjectId() {
        Object id = fields.get("_id");
        return id != null ? id.toString() : null;
    }

    public void ensureId() {
        if (!fields.containsKey("_id")) {
            fields.put("_id", new ObjectId().toString());
        }
    }

    // ---- JSON Serialization ----

    public String toJson() {
        return toJsonElement().toString();
    }

    public String toPrettyJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(toJsonElement());
    }

    public JsonObject toJsonElement() {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            obj.add(entry.getKey(), convertToJsonElement(entry.getValue()));
        }
        return obj;
    }

    public static Document fromJson(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return fromJsonObject(obj);
    }

    public static Document fromJsonObject(JsonObject obj) {
        Document doc = new Document();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            doc.put(entry.getKey(), convertFromJsonElement(entry.getValue()));
        }
        return doc;
    }

    // ---- Deep copy ----

    public Document deepCopy() {
        return Document.fromJson(this.toJson());
    }

    // ---- Internal helpers ----

    private Object normalize(Object value) {
        if (value instanceof Map<?, ?> m) {
            return new Document(castMap(m));
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : list) {
                normalized.add(normalize(item));
            }
            return normalized;
        }
        // Normalize Gson number quirk: whole doubles become ints
        if (value instanceof Double d) {
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                long l = d.longValue();
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                    return (int) l;
                }
                return l;
            }
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            result.put(e.getKey().toString(), e.getValue());
        }
        return result;
    }

    private static JsonElement convertToJsonElement(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof String s) return new JsonPrimitive(s);
        if (value instanceof Number n) return new JsonPrimitive(n);
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        if (value instanceof Document d) return d.toJsonElement();
        if (value instanceof List<?> list) {
            JsonArray arr = new JsonArray();
            for (Object item : list) {
                arr.add(convertToJsonElement(item));
            }
            return arr;
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject obj = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                obj.add(entry.getKey().toString(), convertToJsonElement(entry.getValue()));
            }
            return obj;
        }
        return new JsonPrimitive(value.toString());
    }

    private static Object convertFromJsonElement(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive()) {
            JsonPrimitive p = element.getAsJsonPrimitive();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) {
                double d = p.getAsDouble();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    long l = (long) d;
                    if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return (int) l;
                    return l;
                }
                return d;
            }
            return p.getAsString();
        }
        if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement e : element.getAsJsonArray()) {
                list.add(convertFromJsonElement(e));
            }
            return list;
        }
        if (element.isJsonObject()) {
            return fromJsonObject(element.getAsJsonObject());
        }
        return null;
    }

    @Override
    public String toString() {
        return toJson();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Document other)) return false;
        return fields.equals(other.fields);
    }

    @Override
    public int hashCode() {
        return fields.hashCode();
    }
}
