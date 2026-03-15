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
    private final String promptColor;
    private final String reset;
    private final String cyan;
    private final String yellow;
    private final String red;

    public Shell(DatabaseServer server) throws IOException {
        this.server = server;
        this.currentDb = server.getDatabase("test");
        this.scanner = new Scanner(System.in);
        this.running = true;

        boolean ansiEnabled = supportsAnsi();
        this.promptColor = ansiEnabled ? "\u001B[32m" : "";
        this.reset = ansiEnabled ? "\u001B[0m" : "";
        this.cyan = ansiEnabled ? "\u001B[36m" : "";
        this.yellow = ansiEnabled ? "\u001B[33m" : "";
        this.red = ansiEnabled ? "\u001B[31m" : "";
    }

    public void start() {
        printBanner();

        while (running) {
            System.out.print(promptColor + currentDb.getName() + "> " + reset);
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
                System.out.println(red + "Error: " + e.getMessage() + reset);
                System.out.println("Try 'help' for syntax examples.");
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
            System.out.println(keyword("switched to db") + " " + value(dbName));
            return;
        }

        // show dbs
        if (line.equals("show dbs") || line.equals("show databases")) {
            printStringList("Databases", server.listDatabases(), "No databases found yet.");
            return;
        }

        // show collections
        if (line.equals("show collections") || line.equals("show tables")) {
            printStringList("Collections in " + currentDb.getName(), currentDb.listCollections(), "No collections in this database yet.");
            return;
        }

        // db.stats()
        if (line.equals("db.stats()")) {
            Map<String, Object> stats = currentDb.stats();
            printSection("Database Stats");
            System.out.println(new Document(toStringObjectMap(stats)).toPrettyJson());
            return;
        }

        // db.dropDatabase()
        if (line.equals("db.dropDatabase()")) {
            String name = currentDb.getName();
            server.dropDatabase(name);
            currentDb = server.getDatabase("test");
            printSuccess("Dropped database " + name + ". Switched back to test.");
            return;
        }

        // help
        if (line.equals("help")) {
            printHelp();
            return;
        }

        if (line.equals("help examples") || line.equals("examples") || line.equals("tutorial")) {
            printExamples();
            return;
        }

        if (line.equals("help beginner") || line.equals("beginner")) {
            printBeginnerGuide();
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

        // db["collection-name"].method(...)
        Pattern quotedDbOpPattern = Pattern.compile("^db\\[(\"|')(.*?)\\1]\\.(\\w+)\\((.*)\\)$", Pattern.DOTALL);
        Matcher quotedMatch = quotedDbOpPattern.matcher(line);
        if (quotedMatch.matches()) {
            String colName = quotedMatch.group(2);
            String method = quotedMatch.group(3);
            String args = quotedMatch.group(4).trim();
            executeCollectionOp(colName, method, args);
            return;
        }

        // db.<collection>.getIndexes() etc (no-arg methods)
        Pattern noArgPattern = Pattern.compile("^db\\.(\\w+)\\.(\\w+)$");
        Matcher m2 = noArgPattern.matcher(line);
        if (m2.matches()) {
            // Treat as method with no parens
            System.out.println(red + "Missing parentheses. Did you mean: " + line + "()" + reset);
            return;
        }

        Pattern quotedNoArgPattern = Pattern.compile("^db\\[(\"|')(.*?)\\1]\\.(\\w+)$");
        Matcher quotedNoArgMatch = quotedNoArgPattern.matcher(line);
        if (quotedNoArgMatch.matches()) {
            System.out.println(red + "Missing parentheses. Did you mean: " + line + "()" + reset);
            return;
        }

        System.out.println(yellow + "Unknown command." + reset);
        System.out.println(keyword("Try one of these:"));
        System.out.println("  " + command("help"));
        System.out.println("  " + command("help examples"));
        System.out.println("  " + command("show collections"));
        System.out.println("  " + command("db.users.find()"));
    }

    private void executeCollectionOp(String colName, String method, String args) throws IOException {
        Collection col = currentDb.getCollection(colName);

        switch (method) {
            case "insertOne" -> {
                Document doc = Document.fromJson(args);
                String id = col.insertOne(doc);
                printSuccess("Inserted 1 document into " + colName + ".");
                System.out.println("{ \"acknowledged\": true, \"insertedId\": \"" + id + "\" }");
            }
            case "insertMany" -> {
                List<Document> docs = parseDocumentArray(args);
                List<String> ids = col.insertMany(docs);
                printSuccess("Inserted " + ids.size() + " documents into " + colName + ".");
                System.out.println("{ \"acknowledged\": true, \"insertedCount\": " + ids.size() + " }");
            }
            case "find" -> {
                Document query = args.isEmpty() ? null : Document.fromJson(args);
                List<Document> results = col.find(query).toList();
                printDocuments("Query Results", results, "No documents matched.", "document(s)");
            }
            case "findOne" -> {
                Document query = args.isEmpty() ? new Document() : Document.fromJson(args);
                Document result = col.findOne(query);
                if (result != null) {
                    printSection("Single Result");
                    System.out.println(cyan + result.toPrettyJson() + reset);
                } else {
                    printInfo("No matching document found.");
                }
            }
            case "updateOne" -> {
                String[] parts = splitTwoArgs(args);
                Document filter = Document.fromJson(parts[0]);
                Document update = Document.fromJson(parts[1]);
                boolean modified = col.updateOne(filter, update);
                printSuccess(modified ? "Updated 1 document." : "No documents were updated.");
                System.out.println("{ \"acknowledged\": true, \"modifiedCount\": " + (modified ? 1 : 0) + " }");
            }
            case "updateMany" -> {
                String[] parts = splitTwoArgs(args);
                Document filter = Document.fromJson(parts[0]);
                Document update = Document.fromJson(parts[1]);
                int count = col.updateMany(filter, update);
                printSuccess("Updated " + count + " document(s).");
                System.out.println("{ \"acknowledged\": true, \"modifiedCount\": " + count + " }");
            }
            case "replaceOne" -> {
                String[] parts = splitTwoArgs(args);
                Document filter = Document.fromJson(parts[0]);
                Document replacement = Document.fromJson(parts[1]);
                boolean modified = col.replaceOne(filter, replacement);
                printSuccess(modified ? "Replaced 1 document." : "No documents were replaced.");
                System.out.println("{ \"acknowledged\": true, \"modifiedCount\": " + (modified ? 1 : 0) + " }");
            }
            case "deleteOne" -> {
                Document query = Document.fromJson(args);
                boolean deleted = col.deleteOne(query);
                printSuccess(deleted ? "Deleted 1 document." : "No documents were deleted.");
                System.out.println("{ \"acknowledged\": true, \"deletedCount\": " + (deleted ? 1 : 0) + " }");
            }
            case "deleteMany" -> {
                Document query = args.isEmpty() ? new Document() : Document.fromJson(args);
                int count = col.deleteMany(query);
                printSuccess("Deleted " + count + " document(s).");
                System.out.println("{ \"acknowledged\": true, \"deletedCount\": " + count + " }");
            }
            case "countDocuments" -> {
                Document query = args.isEmpty() ? null : Document.fromJson(args);
                int count = query != null ? col.countDocuments(query) : col.countDocuments();
                printSection("Count");
                System.out.println(count);
            }
            case "distinct" -> {
                // distinct("field", {query})
                String[] parts = splitTwoArgsLenient(args);
                String field = parts[0].trim().replaceAll("^\"|\"$", "").replaceAll("^'|'$", "");
                Document query = parts.length > 1 && !parts[1].trim().isEmpty()
                        ? Document.fromJson(parts[1]) : new Document();
                List<Object> values = col.distinct(field, query);
                printSection("Distinct Values for " + field);
                System.out.println(values);
            }
            case "createIndex" -> {
                String field = args.trim().replaceAll("^\"|\"$", "").replaceAll("^'|'$", "");
                col.createIndex(field);
                System.out.println(keyword("Index created on field:") + " " + value(field));
            }
            case "getIndexes" -> {
                Set<String> indexes = col.getIndexes();
                printSection("Indexes for " + colName);
                if (indexes.isEmpty()) {
                    printInfo("No indexes found.");
                } else {
                    for (String idx : indexes) {
                        System.out.println("  - " + value(idx));
                    }
                }
            }
            case "drop" -> {
                col.drop();
                printSuccess("Dropped collection " + colName + ".");
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
                printDocuments("Aggregation Results", results, "Pipeline returned no results.", "result(s)");
            }
            default -> System.out.println(red + "Unknown method: " + method + reset);
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
        String[] logo = {
                "  __  __       _   _       ____   ___  _      ",
                " |  \\/  |_   _| \\ | | ___ / ___| / _ \\| |     ",
                " | |\\/| | | | |  \\| |/ _ \\\\___ \\| | | | |     ",
                " | |  | | |_| | |\\  | (_) |__) | |_| | |___  ",
                " |_|  |_|\\__, |_| \\_|\\___/____/ \\__\\_\\_____| ",
                "          |___/                                   "
        };

        System.out.println(cyan + "========================================" + reset);
        System.out.println(cyan + " MyNoSQL Shell" + reset + "  v1.0.0");
        System.out.println(cyan + "========================================" + reset);
        for (String line : logo) {
            System.out.println(cyan + line + reset);
        }
        System.out.println("A beginner-friendly document database shell.");
        System.out.println(keyword("Current database:") + " " + value(currentDb.getName()));
        System.out.println(keyword("Data directory:") + " " + value(server.getBaseDir()));
        System.out.println();
        System.out.println(keyword("Quick start:"));
        System.out.println("  1. Type " + command("show collections") + " to inspect the current database.");
        System.out.println("  2. Type " + command("help examples") + " to see copy-paste commands.");
        System.out.println("  3. Type " + command("exit") + " when you want to leave.");
        System.out.println();
    }

    private void printHelp() {
        printSection("Commands");
        printHelpLine("use <db>", "Switch database");
        printHelpLine("show dbs", "List databases");
        printHelpLine("show collections", "List collections");
        printHelpLine("db.stats()", "Show database stats");
        printHelpLine("db.<col>.insertOne({...})", "Insert one document");
        printHelpLine("db.<col>.insertMany([{...}, ...])", "Insert many documents");
        printHelpLine("db.<col>.find()", "Find all documents");
        printHelpLine("db.<col>.find({...})", "Find with query");
        printHelpLine("db.<col>.findOne({...})", "Find one document");
        printHelpLine("db.<col>.updateOne({filter}, {update})", "Update one document");
        printHelpLine("db.<col>.updateMany({filter}, {update})", "Update many documents");
        printHelpLine("db.<col>.replaceOne({filter}, {doc})", "Replace one document");
        printHelpLine("db.<col>.deleteOne({...})", "Delete one document");
        printHelpLine("db.<col>.deleteMany({...})", "Delete many documents");
        printHelpLine("db.<col>.countDocuments()", "Count documents");
        printHelpLine("db.<col>.distinct(\"field\", {query})", "Show distinct values");
        printHelpLine("db.<col>.createIndex(\"field\")", "Create index");
        printHelpLine("db.<col>.getIndexes()", "List indexes");
        printHelpLine("db.<col>.aggregate([...])", "Run aggregation pipeline");
        printHelpLine("db.<col>.drop()", "Drop collection");
        printHelpLine("db.dropDatabase()", "Drop current database");
        printHelpLine("help examples", "Show copy-paste examples");
        printHelpLine("help beginner", "Show a first-use guide");
        printHelpLine("exit / quit", "Exit shell");
        System.out.println();
        System.out.println(keyword("Tip:") + " for collection names with dashes, use bracket syntax.");
        System.out.println("  Example: " + command("db[\"psgmx-forks\"].find()"));
    }

    private void printExamples() {
        System.out.println(yellow + "Examples" + reset);
        System.out.println("  " + command("use myapp"));
        System.out.println("  " + command("show collections"));
        System.out.println("  " + command("db.users.find()"));
        System.out.println("  " + command("db.users.find({\"role\": \"admin\"})"));
        System.out.println("  " + command("db.users.insertOne({\"name\": \"Sam\", \"age\": 24})"));
        System.out.println("  " + command("db.users.updateOne({\"name\": \"Sam\"}, {\"$set\": {\"age\": 25}})"));
        System.out.println("  " + command("db.users.deleteOne({\"name\": \"Sam\"})"));
        System.out.println("  " + command("db[\"psgmx-forks\"].find()"));
    }

    private void printBeginnerGuide() {
        System.out.println(yellow + "Beginner Guide" + reset);
        System.out.println("  1. Choose a database with: " + command("use myapp"));
        System.out.println("  2. Insert data with: " + command("db.users.insertOne({\"name\": \"Asha\", \"age\": 22})"));
        System.out.println("  3. Read data with: " + command("db.users.find()"));
        System.out.println("  4. Filter data with: " + command("db.users.find({\"age\": {\"$gte\": 18}})"));
        System.out.println("  5. Update data with: " + command("db.users.updateOne({\"name\": \"Asha\"}, {\"$set\": {\"age\": 23}})"));
        System.out.println("  6. Delete data with: " + command("db.users.deleteOne({\"name\": \"Asha\"})"));
        System.out.println();
        System.out.println("If a collection name contains '-' use bracket syntax:");
        System.out.println("  " + command("db[\"psgmx-forks\"].find()"));
    }

    private String command(String text) {
        return promptColor + text + reset;
    }

    private String keyword(String text) {
        return yellow + text + reset;
    }

    private String value(String text) {
        return cyan + text + reset;
    }

    private void printSection(String title) {
        System.out.println(yellow + title + reset);
        System.out.println(yellow + repeat("-", Math.max(18, title.length())) + reset);
    }

    private void printSuccess(String message) {
        System.out.println(promptColor + "OK: " + reset + message);
    }

    private void printInfo(String message) {
        System.out.println(cyan + "Info: " + reset + message);
    }

    private void printHelpLine(String commandText, String description) {
        System.out.printf("  %-48s %s%n", command(commandText), description);
    }

    private void printStringList(String title, List<String> items, String emptyMessage) {
        printSection(title);
        if (items.isEmpty()) {
            printInfo(emptyMessage);
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + value(items.get(i)));
        }
    }

    private void printDocuments(String title, List<Document> documents, String emptyMessage, String countLabel) {
        printSection(title);
        if (documents.isEmpty()) {
            printInfo(emptyMessage);
            return;
        }

        for (int i = 0; i < documents.size(); i++) {
            System.out.println(keyword("Result " + (i + 1) + ":"));
            System.out.println(cyan + documents.get(i).toPrettyJson() + reset);
        }
        System.out.println(keyword("Total:") + " " + documents.size() + " " + countLabel);
    }

    private String repeat(String value, int count) {
        return value.repeat(Math.max(0, count));
    }

    private boolean supportsAnsi() {
        if (System.getenv("NO_COLOR") != null) {
            return false;
        }

        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!osName.contains("win")) {
            return true;
        }

        if (System.getenv("WT_SESSION") != null || System.getenv("ANSICON") != null) {
            return true;
        }

        if ("ON".equalsIgnoreCase(System.getenv("ConEmuANSI"))) {
            return true;
        }

        String term = System.getenv("TERM");
        if (term == null) {
            return false;
        }

        String lowerTerm = term.toLowerCase(Locale.ROOT);
        return lowerTerm.contains("xterm")
                || lowerTerm.contains("msys")
                || lowerTerm.contains("cygwin")
                || lowerTerm.contains("mingw");
    }
}
