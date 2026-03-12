package com.mynosql.shell;

import com.mynosql.aggregation.AggregationPipeline;
import com.mynosql.collection.Collection;
import com.mynosql.database.Database;
import com.mynosql.database.DatabaseServer;
import com.mynosql.document.Document;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interactive REPL shell — similar to the mongo shell.
 *
 * Commands:
 *   use <db>                              - Switch database
 *   show dbs                              - List databases
 *   show collections                      - List collections in current db
 *   db.stats()                            - Database stats
 *   db.<col>.insertOne({...})             - Insert document
 *   db.<col>.insertMany([{...}, ...])     - Insert multiple
 *   db.<col>.find({...})                  - Query documents
 *   db.<col>.findOne({...})               - Find single doc
 *   db.<col>.updateOne({filter}, {update})- Update one
 *   db.<col>.updateMany({filter}, {update})- Update many
 *   db.<col>.deleteOne({...})             - Delete one
 *   db.<col>.deleteMany({...})            - Delete many
 *   db.<col>.countDocuments({...})        - Count
 *   db.<col>.distinct("field", {...})     - Distinct values
 *   db.<col>.createIndex("field")         - Create index
 *   db.<col>.getIndexes()                 - List indexes
 *   db.<col>.drop()                       - Drop collection
 *   db.<col>.aggregate([...])             - Aggregation pipeline
 *   db.dropDatabase()                     - Drop current database
 *   exit / quit                           - Exit shell
 */
public class Shell {

    private final DatabaseServer server;
    private Database currentDb;
    private final Scanner scanner;
    private boolean running;

    private static final String PROMPT_COLOR = "\u001B[32m";
    private static final String RESET = "\u001B[0m";
    private static final String CYAN = "\u001B[36m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";

    public Shell(DatabaseServer server) throws IOException {
        this.server = server;
        this.currentDb = server.getDatabase("test");
        this.scanner = new Scanner(System.in);
        this.running = true;
    }

    public void start() {
        printBanner();

        while (running) {
            System.out.print(PROMPT_COLOR + currentDb.getName() + "> " + RESET);
            String line;
            try {
                if (!scanner.hasNextLine()) break;
                line = scanner.nextLine().trim();
            } catch (NoSuchElementException e) {
                break;
            }
            if (line.isEmpty()) continue;

            try {
                execute(line);
            } catch (Exception e) {
                System.out.println(RED + "Error: " + e.getMessage() + RESET);
            }
        }
        System.out.println("Bye!");
    }

    private void execute(String line) throws IOException {
        // Exit
        if (line.equals("exit") || line.equals("quit")) {
            running = false;
            return;
        }

        // use <db>
        if (line.startsWith("use ")) {
            String dbName = line.substring(4).trim();
            currentDb = server.getDatabase(dbName);
            System.out.println("switched to db " + dbName);
            return;
        }

        // show dbs
        if (line.equals("show dbs") || line.equals("show databases")) {
            for (String db : server.listDatabases()) {
                System.out.println("  " + db);
            }
            return;
        }

        // show collections
        if (line.equals("show collections") || line.equals("show tables")) {
            for (String col : currentDb.listCollections()) {
                System.out.println("  " + col);
            }
            return;
        }

        // db.stats()
        if (line.equals("db.stats()")) {
            Map<String, Object> stats = currentDb.stats();
            System.out.println(new Document(toStringObjectMap(stats)).toPrettyJson());
            return;
        }

        // db.dropDatabase()
        if (line.equals("db.dropDatabase()")) {
            String name = currentDb.getName();
            server.dropDatabase(name);
            currentDb = server.getDatabase("test");
            System.out.println("{ \"dropped\": \"" + name + "\", \"ok\": 1 }");
            return;
        }

        // help
        if (line.equals("help")) {
            printHelp();
            return;
        }

        // db.<collection>.<method>(...)
        Pattern dbOpPattern = Pattern.compile("^db\\.(\\w+)\\.(\\w+)\\((.*)\\)$", Pattern.DOTALL);
        Matcher m = dbOpPattern.matcher(line);
        if (m.matches()) {
            String colName = m.group(1);
            String method = m.group(2);
            String args = m.group(3).trim();
            executeCollectionOp(colName, method, args);
            return;
        }

        // db.<collection>.getIndexes() etc (no-arg methods)
        Pattern noArgPattern = Pattern.compile("^db\\.(\\w+)\\.(\\w+)$");
        Matcher m2 = noArgPattern.matcher(line);
        if (m2.matches()) {
            // Treat as method with no parens
            System.out.println(RED + "Missing parentheses. Did you mean: " + line + "()" + RESET);
            return;
        }

        System.out.println(YELLOW + "Unknown command. Type 'help' for commands." + RESET);
    }

    private void executeCollectionOp(String colName, String method, String args) throws IOException {
        Collection col = currentDb.getCollection(colName);

        switch (method) {
            case "insertOne" -> {
                Document doc = Document.fromJson(args);
                String id = col.insertOne(doc);
                System.out.println("{ \"acknowledged\": true, \"insertedId\": \"" + id + "\" }");
            }
            case "insertMany" -> {
                List<Document> docs = parseDocumentArray(args);
                List<String> ids = col.insertMany(docs);
                System.out.println("{ \"acknowledged\": true, \"insertedCount\": " + ids.size() + " }");
            }
            case "find" -> {
                Document query = args.isEmpty() ? null : Document.fromJson(args);
                List<Document> results = col.find(query).toList();
                for (Document doc : results) {
                    System.out.println(CYAN + doc.toPrettyJson() + RESET);
                }
                System.out.println("--- " + results.size() + " document(s) ---");
            }
            case "findOne" -> {
                Document query = args.isEmpty() ? new Document() : Document.fromJson(args);
                Document result = col.findOne(query);
                if (result != null) {
                    System.out.println(CYAN + result.toPrettyJson() + RESET);
                } else {
                    System.out.println("null");
                }
            }
            case "updateOne" -> {
                String[] parts = splitTwoArgs(args);
                Document filter = Document.fromJson(parts[0]);
                Document update = Document.fromJson(parts[1]);
                boolean modified = col.updateOne(filter, update);
                System.out.println("{ \"acknowledged\": true, \"modifiedCount\": " + (modified ? 1 : 0) + " }");
            }
            case "updateMany" -> {
                String[] parts = splitTwoArgs(args);
                Document filter = Document.fromJson(parts[0]);
                Document update = Document.fromJson(parts[1]);
                int count = col.updateMany(filter, update);
                System.out.println("{ \"acknowledged\": true, \"modifiedCount\": " + count + " }");
            }
            case "replaceOne" -> {
                String[] parts = splitTwoArgs(args);
                Document filter = Document.fromJson(parts[0]);
                Document replacement = Document.fromJson(parts[1]);
                boolean modified = col.replaceOne(filter, replacement);
                System.out.println("{ \"acknowledged\": true, \"modifiedCount\": " + (modified ? 1 : 0) + " }");
            }
            case "deleteOne" -> {
                Document query = Document.fromJson(args);
                boolean deleted = col.deleteOne(query);
                System.out.println("{ \"acknowledged\": true, \"deletedCount\": " + (deleted ? 1 : 0) + " }");
            }
            case "deleteMany" -> {
                Document query = args.isEmpty() ? new Document() : Document.fromJson(args);
                int count = col.deleteMany(query);
                System.out.println("{ \"acknowledged\": true, \"deletedCount\": " + count + " }");
            }
            case "countDocuments" -> {
                Document query = args.isEmpty() ? null : Document.fromJson(args);
                int count = query != null ? col.countDocuments(query) : col.countDocuments();
                System.out.println(count);
            }
            case "distinct" -> {
                // distinct("field", {query})
                String[] parts = splitTwoArgsLenient(args);
                String field = parts[0].trim().replaceAll("^\"|\"$", "").replaceAll("^'|'$", "");
                Document query = parts.length > 1 && !parts[1].trim().isEmpty()
                        ? Document.fromJson(parts[1]) : new Document();
                List<Object> values = col.distinct(field, query);
                System.out.println(values);
            }
            case "createIndex" -> {
                String field = args.trim().replaceAll("^\"|\"$", "").replaceAll("^'|'$", "");
                col.createIndex(field);
                System.out.println("Index created on field: " + field);
            }
            case "getIndexes" -> {
                Set<String> indexes = col.getIndexes();
                for (String idx : indexes) {
                    System.out.println("  { field: \"" + idx + "\" }");
                }
            }
            case "drop" -> {
                col.drop();
                System.out.println("true");
            }
            case "aggregate" -> {
                List<Document> stages = parseDocumentArray(args);
                AggregationPipeline pipeline = new AggregationPipeline();
                pipeline.setLookupResolver(lookupCol -> {
                    try {
                        return currentDb.getCollection(lookupCol).find().toList();
                    } catch (IOException e) {
                        return List.of();
                    }
                });
                for (Document stage : stages) {
                    pipeline.addStage(stage);
                }
                List<Document> all = col.find().toList();
                List<Document> results = pipeline.execute(all);
                for (Document doc : results) {
                    System.out.println(CYAN + doc.toPrettyJson() + RESET);
                }
                System.out.println("--- " + results.size() + " result(s) ---");
            }
            default -> System.out.println(RED + "Unknown method: " + method + RESET);
        }
    }

    // ---- Parsing helpers ----

    private List<Document> parseDocumentArray(String json) {
        json = json.trim();
        if (!json.startsWith("[")) {
            throw new IllegalArgumentException("Expected JSON array: [...]");
        }
        com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
        List<Document> docs = new ArrayList<>();
        for (com.google.gson.JsonElement el : arr) {
            docs.add(Document.fromJsonObject(el.getAsJsonObject()));
        }
        return docs;
    }

    /**
     * Split args like: {...}, {...} into two JSON strings.
     * Handles nested braces correctly.
     */
    private String[] splitTwoArgs(String args) {
        int depth = 0;
        int splitAt = -1;
        boolean inString = false;
        char prev = 0;

        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '"' && prev != '\\') inString = !inString;
            if (!inString) {
                if (c == '{' || c == '[') depth++;
                else if (c == '}' || c == ']') depth--;
                else if (c == ',' && depth == 0) {
                    splitAt = i;
                    break;
                }
            }
            prev = c;
        }

        if (splitAt == -1) throw new IllegalArgumentException("Expected two arguments separated by comma");
        return new String[]{
                args.substring(0, splitAt).trim(),
                args.substring(splitAt + 1).trim()
        };
    }

    private String[] splitTwoArgsLenient(String args) {
        int depth = 0;
        int splitAt = -1;
        boolean inString = false;
        char prev = 0;

        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '"' && prev != '\\') inString = !inString;
            if (c == '\'' && prev != '\\') inString = !inString;
            if (!inString) {
                if (c == '{' || c == '[') depth++;
                else if (c == '}' || c == ']') depth--;
                else if (c == ',' && depth == 0) {
                    splitAt = i;
                    break;
                }
            }
            prev = c;
        }

        if (splitAt == -1) return new String[]{args};
        return new String[]{
                args.substring(0, splitAt).trim(),
                args.substring(splitAt + 1).trim()
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toStringObjectMap(Map<String, Object> map) {
        return map;
    }

    // ---- UI ----

    private void printBanner() {
        System.out.println(CYAN);
        System.out.println("  __  __       _   _       ____   ___  _     ");
        System.out.println(" |  \\/  |_   _| \\ | | ___ / ___| / _ \\| |    ");
        System.out.println(" | |\\/| | | | |  \\| |/ _ \\\\___ \\| | | | |    ");
        System.out.println(" | |  | | |_| | |\\  | (_) |__) | |_| | |___ ");
        System.out.println(" |_|  |_|\\__, |_| \\_|\\___/____/ \\__\\_\\|_____|");
        System.out.println("         |___/                                ");
        System.out.println(RESET);
        System.out.println(" MyNoSQL v1.0.0 — A MongoDB-clone in Java");
        System.out.println(" Type 'help' for commands. Data dir: " + server.getBaseDir());
        System.out.println();
    }

    private void printHelp() {
        System.out.println(YELLOW + "Commands:" + RESET);
        System.out.println("  use <db>                                - Switch database");
        System.out.println("  show dbs                                - List databases");
        System.out.println("  show collections                        - List collections");
        System.out.println("  db.stats()                              - Database stats");
        System.out.println("  db.<col>.insertOne({...})               - Insert one document");
        System.out.println("  db.<col>.insertMany([{...}, ...])       - Insert many documents");
        System.out.println("  db.<col>.find()                         - Find all documents");
        System.out.println("  db.<col>.find({...})                    - Find with query");
        System.out.println("  db.<col>.findOne({...})                 - Find one document");
        System.out.println("  db.<col>.updateOne({filter}, {update})  - Update one document");
        System.out.println("  db.<col>.updateMany({filter}, {update}) - Update many documents");
        System.out.println("  db.<col>.replaceOne({filter}, {doc})    - Replace one document");
        System.out.println("  db.<col>.deleteOne({...})               - Delete one document");
        System.out.println("  db.<col>.deleteMany({...})              - Delete many documents");
        System.out.println("  db.<col>.countDocuments()               - Count documents");
        System.out.println("  db.<col>.distinct(\"field\", {query})     - Distinct values");
        System.out.println("  db.<col>.createIndex(\"field\")           - Create index");
        System.out.println("  db.<col>.getIndexes()                   - List indexes");
        System.out.println("  db.<col>.aggregate([...])               - Aggregation pipeline");
        System.out.println("  db.<col>.drop()                         - Drop collection");
        System.out.println("  db.dropDatabase()                       - Drop current database");
        System.out.println("  exit / quit                             - Exit shell");
    }
}
