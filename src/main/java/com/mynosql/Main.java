package com.mynosql;

import com.mynosql.aggregation.AggregationPipeline;
import com.mynosql.collection.Collection;
import com.mynosql.database.Database;
import com.mynosql.database.DatabaseServer;
import com.mynosql.document.Document;
import com.mynosql.shell.Shell;

import java.util.*;

/**
 * Main entry point — runs a demo showcasing all features, then launches the interactive shell.
 * Use --shell to skip the demo and go straight to the shell.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        boolean shellOnly = args.length > 0 && args[0].equals("--shell");

        DatabaseServer server = new DatabaseServer("mynosql_data");

        if (!shellOnly) {
            runDemo(server);
            System.out.println("\n========================================");
            System.out.println(" Launching interactive shell...");
            System.out.println("========================================\n");
        }

        Shell shell = new Shell(server);
        shell.start();
    }

    private static void runDemo(DatabaseServer server) throws Exception {
        System.out.println("=== MyNoSQL Demo ===\n");

        // 1. Get database and collection
        Database db = server.getDatabase("myapp");
        Collection users = db.getCollection("users");
        users.drop(); // Fresh start for demo

        // 2. INSERT
        System.out.println("--- INSERT ---");

        String id1 = users.insertOne(
                new Document("name", "Alice")
                        .append("age", 30)
                        .append("email", "alice@example.com")
                        .append("role", "admin")
                        .append("scores", List.of(90, 85, 92))
                        .append("address", new Document("city", "New York").append("zip", "10001"))
        );
        System.out.println("Inserted Alice: " + id1);

        List<String> ids = users.insertMany(List.of(
                new Document("name", "Bob").append("age", 25).append("email", "bob@example.com")
                        .append("role", "user").append("scores", List.of(75, 80, 88))
                        .append("address", new Document("city", "Boston").append("zip", "02101")),
                new Document("name", "Charlie").append("age", 35).append("email", "charlie@example.com")
                        .append("role", "user").append("scores", List.of(95, 98, 100))
                        .append("address", new Document("city", "New York").append("zip", "10002")),
                new Document("name", "Diana").append("age", 28).append("email", "diana@example.com")
                        .append("role", "admin").append("scores", List.of(88, 91, 87))
                        .append("address", new Document("city", "Chicago").append("zip", "60601")),
                new Document("name", "Eve").append("age", 22).append("email", "eve@example.com")
                        .append("role", "user").append("scores", List.of(70, 65, 72))
                        .append("address", new Document("city", "Boston").append("zip", "02102"))
        ));
        System.out.println("Inserted " + ids.size() + " more users\n");

        // 3. FIND
        System.out.println("--- FIND ---");

        // Find all
        List<Document> all = users.find().toList();
        System.out.println("Total users: " + all.size());

        // Find with query
        System.out.println("\nAdmins:");
        List<Document> admins = users.find(new Document("role", "admin")).toList();
        for (Document doc : admins) {
            System.out.println("  " + doc.getString("name") + " (age: " + doc.getInteger("age") + ")");
        }

        // $gt query
        System.out.println("\nUsers older than 27:");
        List<Document> older = users.find(
                new Document("age", new Document("$gt", 27))
        ).toList();
        for (Document doc : older) {
            System.out.println("  " + doc.getString("name") + " - age " + doc.getInteger("age"));
        }

        // Dot notation (nested field)
        System.out.println("\nUsers in New York:");
        List<Document> nyUsers = users.find(
                new Document("address.city", "New York")
        ).toList();
        for (Document doc : nyUsers) {
            System.out.println("  " + doc.getString("name"));
        }

        // $or query
        System.out.println("\nUsers who are admin OR age < 25:");
        List<Document> orResult = users.find(
                new Document("$or", List.of(
                        new Document("role", "admin"),
                        new Document("age", new Document("$lt", 25))
                ))
        ).toList();
        for (Document doc : orResult) {
            System.out.println("  " + doc.getString("name") + " (role: " + doc.getString("role") + ", age: " + doc.getInteger("age") + ")");
        }

        // $in query
        System.out.println("\nUsers in Boston or Chicago:");
        List<Document> inResult = users.find(
                new Document("address.city", new Document("$in", List.of("Boston", "Chicago")))
        ).toList();
        for (Document doc : inResult) {
            System.out.println("  " + doc.getString("name") + " - " + doc.getDocument("address").getString("city"));
        }

        // $regex query
        System.out.println("\nUsers with name starting with 'C' or 'D':");
        List<Document> regexResult = users.find(
                new Document("name", new Document("$regex", "^[CD]"))
        ).toList();
        for (Document doc : regexResult) {
            System.out.println("  " + doc.getString("name"));
        }

        // Sort + limit
        System.out.println("\nTop 3 oldest users:");
        List<Document> top3 = users.find()
                .sort(new Document("age", -1))
                .limit(3)
                .toList();
        for (Document doc : top3) {
            System.out.println("  " + doc.getString("name") + " - age " + doc.getInteger("age"));
        }

        // Projection
        System.out.println("\nNames and emails only:");
        List<Document> projected = users.find()
                .project(new Document("name", 1).append("email", 1).append("_id", 0))
                .toList();
        for (Document doc : projected) {
            System.out.println("  " + doc.toJson());
        }

        // 4. UPDATE
        System.out.println("\n--- UPDATE ---");

        // $set
        users.updateOne(
                new Document("name", "Alice"),
                new Document("$set", new Document("age", 31).append("role", "superadmin"))
        );
        Document alice = users.findOne(new Document("name", "Alice"));
        System.out.println("Alice after update: age=" + alice.getInteger("age") + ", role=" + alice.getString("role"));

        // $inc
        users.updateMany(
                new Document("role", "user"),
                new Document("$inc", new Document("age", 1))
        );
        System.out.println("Incremented age of all regular users by 1");

        // $push
        users.updateOne(
                new Document("name", "Bob"),
                new Document("$push", new Document("scores", 95))
        );
        Document bob = users.findOne(new Document("name", "Bob"));
        System.out.println("Bob's scores after push: " + bob.getList("scores"));

        // $rename
        users.updateOne(
                new Document("name", "Charlie"),
                new Document("$rename", new Document("email", "contactEmail"))
        );
        Document charlie = users.findOne(new Document("name", "Charlie"));
        System.out.println("Charlie after rename: " + charlie.toJson());

        // 5. AGGREGATION
        System.out.println("\n--- AGGREGATION ---");

        // Group by role, count and average age
        AggregationPipeline pipeline = new AggregationPipeline()
                .group(new Document("_id", "$role")
                        .append("count", new Document("$sum", 1))
                        .append("avgAge", new Document("$avg", "$age"))
                        .append("names", new Document("$push", "$name")))
                .sort(new Document("count", -1));

        List<Document> aggResults = pipeline.execute(users.find().toList());
        System.out.println("Users grouped by role:");
        for (Document doc : aggResults) {
            System.out.println("  " + doc.toPrettyJson());
        }

        // Group by city
        AggregationPipeline cityPipeline = new AggregationPipeline()
                .group(new Document("_id", "$address.city")
                        .append("count", new Document("$sum", 1))
                        .append("maxAge", new Document("$max", "$age"))
                        .append("minAge", new Document("$min", "$age")))
                .sort(new Document("count", -1));

        List<Document> cityResults = cityPipeline.execute(users.find().toList());
        System.out.println("\nUsers grouped by city:");
        for (Document doc : cityResults) {
            System.out.println("  " + doc.toPrettyJson());
        }

        // Unwind + group
        AggregationPipeline scorePipeline = new AggregationPipeline()
                .unwind("$scores")
                .group(new Document("_id", "$name")
                        .append("avgScore", new Document("$avg", "$scores"))
                        .append("maxScore", new Document("$max", "$scores")))
                .sort(new Document("avgScore", -1));

        System.out.println("\nAverage scores per user:");
        List<Document> scoreResults = scorePipeline.execute(users.find().toList());
        for (Document doc : scoreResults) {
            System.out.println("  " + doc.getString("_id") + ": avg=" + doc.get("avgScore") + ", max=" + doc.get("maxScore"));
        }

        // Match + count
        AggregationPipeline countPipeline = new AggregationPipeline()
                .match(new Document("age", new Document("$gte", 28)))
                .count("olderThan28");

        List<Document> countResult = countPipeline.execute(users.find().toList());
        System.out.println("\nUsers 28+: " + countResult.get(0).toJson());

        // 6. INDEX
        System.out.println("\n--- INDEXES ---");
        users.createIndex("email", true);
        users.createIndex("role");
        users.createIndex("age");
        System.out.println("Created indexes on: email (unique), role, age");
        System.out.println("All indexes: " + users.getIndexes());

        // 7. DISTINCT + COUNT
        System.out.println("\n--- DISTINCT & COUNT ---");
        System.out.println("Distinct roles: " + users.distinct("role", new Document()));
        System.out.println("Distinct cities: " + users.distinct("address.city", new Document()));
        System.out.println("Total count: " + users.countDocuments());
        System.out.println("Admin count: " + users.countDocuments(new Document("role", "superadmin")));

        // 8. DELETE
        System.out.println("\n--- DELETE ---");
        boolean deleted = users.deleteOne(new Document("name", "Eve"));
        System.out.println("Deleted Eve: " + deleted);
        System.out.println("Total after delete: " + users.countDocuments());

        // 9. PERSISTENCE
        System.out.println("\n--- PERSISTENCE ---");
        System.out.println("Data is stored on disk at: mynosql_data/myapp/users.ndjson");
        System.out.println("Documents survive restarts. Compaction runs automatically.");

        System.out.println("\n=== Demo Complete ===");
    }
}
