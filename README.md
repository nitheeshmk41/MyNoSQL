# MyNoSQL

A fully-featured MongoDB-clone NoSQL JSON document database built from scratch in Java. Supports querying with MongoDB-style operators, aggregation pipelines, indexing, an interactive shell, and persistent file-based storage.

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Build](#build)
  - [Run](#run)
- [Usage](#usage)
  - [Programmatic API](#programmatic-api)
  - [Interactive Shell](#interactive-shell)
- [API Reference](#api-reference)
  - [Document](#document)
  - [Collection](#collection)
  - [Query Operators](#query-operators)
  - [Update Operators](#update-operators)
  - [Cursor](#cursor)
  - [Aggregation Pipeline](#aggregation-pipeline)
  - [Database and Server](#database-and-server)
  - [Indexing](#indexing)
- [Storage Format](#storage-format)
- [Project Structure](#project-structure)
- [Examples](#examples)
- [License](#license)

---

## Features

| Category | Details |
|---|---|
| **Document Model** | JSON documents with auto-generated 12-byte ObjectId, nested documents, arrays, and dot-notation field access |
| **Storage Engine** | Newline-delimited JSON (NDJSON) files, append-only writes, automatic compaction, thread-safe with `ReadWriteLock` |
| **Indexing** | B-Tree indexes backed by `TreeMap`, unique constraints, range queries, automatic index rebuild on startup |
| **Query Engine** | 18 MongoDB-compatible query operators including comparison, logical, element, evaluation, and array operators |
| **Update Engine** | 7 update operators: `$set`, `$unset`, `$inc`, `$push`, `$pull`, `$addToSet`, `$rename` |
| **Cursor** | Fluent API with `.sort()`, `.skip()`, `.limit()`, `.project()` chaining |
| **Aggregation** | 10 pipeline stages with 8 group accumulators |
| **Interactive Shell** | Full REPL with colored output and all CRUD/aggregation commands |
| **Persistence** | Data stored on disk and survives restarts. Compaction runs automatically after threshold mutations |
| **Concurrency** | Read/write lock per collection for safe concurrent access |

---

## Architecture

```
                    +-------------------+
                    |  DatabaseServer   |   Manages multiple databases
                    +--------+----------+
                             |
                    +--------v----------+
                    |     Database      |   Contains named collections
                    +--------+----------+
                             |
                    +--------v----------+
                    |    Collection     |   CRUD API + update operators
                    +--+-----+------+--+
                       |     |      |
            +----------+  +--+--+  ++----------+
            |             |     |              |
   +--------v---+  +-----v--+  +--v-------+  +v-----------+
   |QueryEngine |  |Cursor  |  |StorageEng|  |IndexManager|
   |            |  |sort    |  |NDJSON    |  |BTreeIndex  |
   |$eq $gt $in |  |skip    |  |append    |  |range query |
   |$or $and ...|  |limit   |  |compact   |  |unique      |
   +------------+  |project |  +----------+  +------------+
                   +--------+
                        |
              +---------v-----------+
              | AggregationPipeline |
              | $match $group $sort |
              | $unwind $lookup ... |
              +---------------------+
```

---

## Getting Started

### Prerequisites

- **Java 17** or higher (tested with Java 21)

### Build

```bash
# Clone the repository
git clone <repo-url>
cd MyNoSql

# Compile
javac -cp "lib/gson-2.10.1.jar" -d out $(find src/main/java -name "*.java")
```

Or with Maven (if installed):

```bash
mvn clean compile
```

Or with the included Maven Wrapper (no global Maven install required):

```bash
# Linux/macOS
./mvnw clean compile

# Windows
mvnw.cmd clean compile
```

### Run

```bash
# Run the full demo followed by the interactive shell
java -cp "out;lib/gson-2.10.1.jar" com.mynosql.Main

# Launch only the interactive shell (skip demo)
java -cp "out;lib/gson-2.10.1.jar" com.mynosql.Main --shell
```

> **Note:** On Linux/macOS, use `:` instead of `;` as the classpath separator:
> ```bash
> java -cp "out:lib/gson-2.10.1.jar" com.mynosql.Main
> ```

### Share with Others (JAR or EXE)

#### Option 1: Portable JAR (works on any OS with Java installed)

Build:

```bash
./mvnw clean package
```

Share:

- `target/mynosql-1.0.0.jar`

Run on recipient machine:

```bash
java -jar mynosql-1.0.0.jar
# or shell only
java -jar mynosql-1.0.0.jar --shell
```

Windows helper script:

```bat
scripts\build-portable.bat
```

#### Option 2: Native Windows EXE installer

If you want users to launch via an `.exe`, build with `jpackage` (included in modern JDKs):

```bat
scripts\build-exe.bat
```

Output:

- Installer and app files in `dist/`

Notes:

- `jpackage` requires JDK 17+ and must be on `PATH`.
- The generated `.exe` is Windows-specific.
- If you need macOS/Linux native packages too, run equivalent `jpackage` commands on those OSes.

#### Option 3: Automatic release artifacts via GitHub Actions

This repository includes a workflow at `.github/workflows/release-artifacts.yml` that:

- Builds and uploads shaded JAR artifacts.
- Builds and uploads Windows EXE artifacts.
- Automatically attaches both to a GitHub Release when a release is published.

You can also run it manually from the Actions tab using `workflow_dispatch`.

---

## Usage

### Programmatic API

```java
import com.mynosql.database.DatabaseServer;
import com.mynosql.database.Database;
import com.mynosql.collection.Collection;
import com.mynosql.document.Document;
import java.util.List;

// Start server (stores data in mynosql_data/)
DatabaseServer server = new DatabaseServer("mynosql_data");

// Get a database and collection
Database db = server.getDatabase("myapp");
Collection users = db.getCollection("users");

// Insert
String id = users.insertOne(
    new Document("name", "Alice")
        .append("age", 30)
        .append("email", "alice@example.com")
        .append("tags", List.of("admin", "active"))
        .append("address", new Document("city", "New York").append("zip", "10001"))
);

// Insert many
users.insertMany(List.of(
    new Document("name", "Bob").append("age", 25),
    new Document("name", "Charlie").append("age", 35)
));

// Find with query
List<Document> admins = users.find(
    new Document("age", new Document("$gte", 30))
).sort(new Document("age", -1))
 .limit(10)
 .toList();

// Find one
Document alice = users.findOne(new Document("name", "Alice"));

// Update
users.updateOne(
    new Document("name", "Alice"),
    new Document("$set", new Document("age", 31))
);

// Delete
users.deleteOne(new Document("name", "Charlie"));
```

### Interactive Shell

```
myapp> use myapp
switched to db myapp

myapp> db.users.insertOne({"name": "Alice", "age": 30, "role": "admin"})
{ "acknowledged": true, "insertedId": "64a..." }

myapp> db.users.find({"age": {"$gte": 25}})
{
  "_id": "64a...",
  "name": "Alice",
  "age": 30,
  "role": "admin"
}
--- 1 document(s) ---

myapp> db.users.updateOne({"name": "Alice"}, {"$inc": {"age": 1}})
{ "acknowledged": true, "modifiedCount": 1 }

myapp> db.users.aggregate([{"$group": {"_id": "$role", "count": {"$sum": 1}}}])
{
  "_id": "admin",
  "count": 1
}
--- 1 result(s) ---

myapp> exit
Bye!
```

**Shell commands:**

| Command | Description |
|---|---|
| `use <db>` | Switch to a database (creates if needed) |
| `show dbs` | List all databases |
| `show collections` | List collections in current database |
| `db.stats()` | Show database statistics |
| `db.<col>.insertOne({...})` | Insert a single document |
| `db.<col>.insertMany([{...}, ...])` | Insert multiple documents |
| `db.<col>.find()` | Find all documents |
| `db.<col>.find({...})` | Find documents matching a query |
| `db.<col>.findOne({...})` | Find the first matching document |
| `db.<col>.updateOne({filter}, {update})` | Update one document |
| `db.<col>.updateMany({filter}, {update})` | Update all matching documents |
| `db.<col>.replaceOne({filter}, {doc})` | Replace one document entirely |
| `db.<col>.deleteOne({...})` | Delete one document |
| `db.<col>.deleteMany({...})` | Delete all matching documents |
| `db.<col>.countDocuments()` | Count all documents |
| `db.<col>.countDocuments({...})` | Count matching documents |
| `db.<col>.distinct("field", {...})` | Get distinct field values |
| `db.<col>.createIndex("field")` | Create a non-unique index |
| `db.<col>.getIndexes()` | List all indexes |
| `db.<col>.aggregate([{...}, ...])` | Run an aggregation pipeline |
| `db.<col>.drop()` | Drop the collection |
| `db.dropDatabase()` | Drop the current database |
| `help` | Show all available commands |
| `exit` / `quit` | Exit the shell |

---

## API Reference

### Document

The core data unit. Supports nested documents, arrays, and all JSON types.

```java
// Create
Document doc = new Document("key", "value");
Document doc = new Document("name", "Alice").append("age", 30);

// Read
String name  = doc.getString("name");
Integer age  = doc.getInteger("age");
Double score = doc.getDouble("score");
Boolean flag = doc.getBoolean("active");
Document sub = doc.getDocument("address");
List<Object> tags = doc.getList("tags");

// Dot-notation for nested access
Object city = doc.get("address.city");

// Serialize
String json = doc.toJson();             // compact
String json = doc.toPrettyJson();       // pretty-printed

// Deserialize
Document doc = Document.fromJson("{\"name\": \"Alice\"}");

// Identity
String id = doc.getObjectId();          // returns _id field
doc.ensureId();                         // auto-generates _id if missing

// Copy
Document copy = doc.deepCopy();
```

### Collection

Full CRUD operations with MongoDB-compatible semantics.

| Method | Returns | Description |
|---|---|---|
| `insertOne(Document)` | `String` (\_id) | Insert a single document |
| `insertMany(List<Document>)` | `List<String>` (\_ids) | Insert multiple documents |
| `find(Document query)` | `Cursor` | Query documents |
| `find()` | `Cursor` | Get all documents |
| `findOne(Document query)` | `Document` | Get first match |
| `findById(String id)` | `Document` | Look up by \_id |
| `updateOne(Document filter, Document update)` | `boolean` | Update first match |
| `updateMany(Document filter, Document update)` | `int` | Update all matches |
| `replaceOne(Document filter, Document replacement)` | `boolean` | Replace first match |
| `deleteOne(Document query)` | `boolean` | Delete first match |
| `deleteMany(Document query)` | `int` | Delete all matches |
| `countDocuments(Document query)` | `int` | Count matching docs |
| `countDocuments()` | `int` | Count all docs |
| `distinct(String field, Document query)` | `List<Object>` | Unique field values |
| `createIndex(String field, boolean unique)` | `void` | Create an index |
| `dropIndex(String field)` | `boolean` | Remove an index |
| `getIndexes()` | `Set<String>` | List indexed fields |
| `drop()` | `void` | Drop the collection |
| `compact()` | `void` | Force storage compaction |

### Query Operators

**Comparison:**

```java
// $eq — equals (implicit)
new Document("name", "Alice")

// $eq — explicit
new Document("name", new Document("$eq", "Alice"))

// $ne — not equals
new Document("status", new Document("$ne", "inactive"))

// $gt, $gte, $lt, $lte — range
new Document("age", new Document("$gt", 25))
new Document("age", new Document("$gte", 18).append("$lte", 65))

// $in — matches any value in array
new Document("role", new Document("$in", List.of("admin", "moderator")))

// $nin — matches none of the values
new Document("status", new Document("$nin", List.of("banned", "deleted")))
```

**Logical:**

```java
// $and — all conditions must match
new Document("$and", List.of(
    new Document("age", new Document("$gte", 18)),
    new Document("status", "active")
))

// $or — at least one must match
new Document("$or", List.of(
    new Document("role", "admin"),
    new Document("age", new Document("$gt", 30))
))

// $nor — none must match
new Document("$nor", List.of(
    new Document("status", "banned"),
    new Document("role", "guest")
))

// $not — negates a condition
new Document("age", new Document("$not", new Document("$gt", 65)))
```

**Element:**

```java
// $exists — field exists or not
new Document("email", new Document("$exists", true))

// $type — check value type ("string", "number", "boolean", "object", "array", "null")
new Document("age", new Document("$type", "number"))
```

**Evaluation:**

```java
// $regex — regular expression match
new Document("name", new Document("$regex", "^Al"))
new Document("email", new Document("$regex", ".*@gmail\\.com$"))
```

**Array:**

```java
// $all — array contains all specified values
new Document("tags", new Document("$all", List.of("java", "nosql")))

// $size — array has exact size
new Document("scores", new Document("$size", 3))

// $elemMatch — at least one array element matches all conditions
new Document("results", new Document("$elemMatch",
    new Document("score", new Document("$gte", 90))
        .append("subject", "math")
))
```

**Dot-notation for nested fields:**

```java
// Query nested document fields
new Document("address.city", "New York")
new Document("address.zip", new Document("$regex", "^100"))
```

### Update Operators

```java
// $set — set field values
new Document("$set", new Document("name", "Bob").append("age", 26))

// $unset — remove fields
new Document("$unset", new Document("tempField", ""))

// $inc — increment numeric fields
new Document("$inc", new Document("age", 1).append("score", -5))

// $push — append to array
new Document("$push", new Document("tags", "new-tag"))

// $pull — remove from array by value
new Document("$pull", new Document("tags", "old-tag"))

// $addToSet — append to array only if not already present
new Document("$addToSet", new Document("tags", "unique-tag"))

// $rename — rename a field
new Document("$rename", new Document("oldName", "newName"))
```

### Cursor

Fluent chaining for query results.

```java
List<Document> results = collection.find(query)
    .sort(new Document("age", -1))           // -1 = descending, 1 = ascending
    .skip(10)                                 // skip first 10
    .limit(5)                                 // return at most 5
    .project(new Document("name", 1)         // include name
        .append("email", 1)                  // include email
        .append("_id", 0))                   // exclude _id
    .toList();

// Get first match
Document first = collection.find(query).first();

// Count (before skip/limit)
int count = collection.find(query).count();

// Iterate
for (Document doc : collection.find(query)) {
    System.out.println(doc.getString("name"));
}
```

### Aggregation Pipeline

Supports 10 stages and 8 accumulators.

**Stages:**

| Stage | Description |
|---|---|
| `$match` | Filter documents (same syntax as find queries) |
| `$group` | Group by field with accumulators |
| `$sort` | Sort documents (1 = asc, -1 = desc) |
| `$limit` | Limit output to N documents |
| `$skip` | Skip first N documents |
| `$project` | Reshape documents (include/exclude fields) |
| `$unwind` | Deconstruct an array into individual documents |
| `$count` | Count documents into a named field |
| `$addFields` | Add or overwrite fields |
| `$lookup` | Left outer join with another collection |

**Accumulators (for `$group`):**

| Accumulator | Description |
|---|---|
| `$sum` | Sum of values (or count with `{"$sum": 1}`) |
| `$avg` | Average of values |
| `$min` | Minimum value |
| `$max` | Maximum value |
| `$count` | Count of documents in group |
| `$push` | Collect all values into an array |
| `$first` | First value in group |
| `$last` | Last value in group |

**Programmatic usage:**

```java
import com.mynosql.aggregation.AggregationPipeline;

AggregationPipeline pipeline = new AggregationPipeline()
    .match(new Document("status", "active"))
    .group(new Document("_id", "$department")
        .append("totalSalary", new Document("$sum", "$salary"))
        .append("avgSalary", new Document("$avg", "$salary"))
        .append("headcount", new Document("$sum", 1))
        .append("employees", new Document("$push", "$name")))
    .sort(new Document("totalSalary", -1))
    .limit(10);

List<Document> results = pipeline.execute(collection.find().toList());
```

**$unwind example:**

```java
// Flatten array field: each array element becomes its own document
AggregationPipeline pipeline = new AggregationPipeline()
    .unwind("$tags")
    .group(new Document("_id", "$tags")
        .append("count", new Document("$sum", 1)))
    .sort(new Document("count", -1));
```

**$lookup example (join):**

```java
AggregationPipeline pipeline = new AggregationPipeline();
pipeline.setLookupResolver(colName -> {
    try {
        return db.getCollection(colName).find().toList();
    } catch (IOException e) {
        return List.of();
    }
});
pipeline.addStage(new Document("$lookup", new Document("from", "orders")
    .append("localField", "_id")
    .append("foreignField", "userId")
    .append("as", "userOrders")));

List<Document> results = pipeline.execute(users.find().toList());
```

### Database and Server

```java
// Create a server (top-level, manages multiple databases)
DatabaseServer server = new DatabaseServer("mynosql_data");   // custom path
DatabaseServer server = new DatabaseServer();                  // default: mynosql_data/

// List databases
Set<String> dbs = server.listDatabases();

// Get or create a database
Database db = server.getDatabase("myapp");

// Database operations
Set<String> collections = db.listCollections();
Map<String, Object> stats = db.stats();
db.dropCollection("users");
db.drop();                         // drop entire database

// Drop a database from server
server.dropDatabase("myapp");
```

### Indexing

Indexes speed up queries on frequently-searched fields. The `_id` field is always indexed with a unique constraint.

```java
// Create a non-unique index
collection.createIndex("email");

// Create a unique index (rejects duplicate values)
collection.createIndex("email", true);

// List all indexes
Set<String> indexes = collection.getIndexes();
// Output: [_id, email]

// Drop an index (cannot drop _id)
collection.dropIndex("email");
```

---

## Storage Format

Each collection is stored as a single **NDJSON** (Newline-Delimited JSON) file:

```
mynosql_data/
  myapp/                          # database
    users.ndjson                  # collection
    orders.ndjson
  analytics/
    events.ndjson
```

Each line in the `.ndjson` file is one complete JSON document:

```json
{"_id":"64a1b2c3d4e5f6a7b8c9d0e1","name":"Alice","age":30,"email":"alice@example.com"}
{"_id":"64a1b2c3d4e5f6a7b8c9d0e2","name":"Bob","age":25,"email":"bob@example.com"}
```

**Compaction** runs automatically after 1000 mutations (updates/deletes) to reclaim space, or can be triggered manually with `collection.compact()`.

---

## Project Structure

```
MyNoSql/
├── pom.xml                                        # Maven build config
├── lib/
│   └── gson-2.10.1.jar                            # Gson dependency
├── src/main/java/com/mynosql/
│   ├── Main.java                                  # Entry point: demo + shell
│   ├── document/
│   │   ├── ObjectId.java                          # 12-byte unique ID generator
│   │   └── Document.java                          # JSON document model
│   ├── storage/
│   │   └── StorageEngine.java                     # NDJSON file storage engine
│   ├── index/
│   │   ├── BTreeIndex.java                        # B-Tree index implementation
│   │   └── IndexManager.java                      # Multi-index manager
│   ├── query/
│   │   ├── QueryEngine.java                       # Query operator evaluation
│   │   └── Cursor.java                            # Sort/skip/limit/project cursor
│   ├── collection/
│   │   └── Collection.java                        # Collection CRUD + updates
│   ├── database/
│   │   ├── Database.java                          # Database container
│   │   └── DatabaseServer.java                    # Server managing databases
│   ├── aggregation/
│   │   └── AggregationPipeline.java               # Aggregation framework
│   └── shell/
│       └── Shell.java                             # Interactive REPL
└── README.md
```

---

## Examples

### E-Commerce Product Catalog

```java
Database db = server.getDatabase("shop");
Collection products = db.getCollection("products");

// Insert products
products.insertMany(List.of(
    new Document("name", "Laptop").append("price", 999.99)
        .append("category", "electronics").append("stock", 50)
        .append("tags", List.of("computer", "portable")),
    new Document("name", "Headphones").append("price", 79.99)
        .append("category", "electronics").append("stock", 200)
        .append("tags", List.of("audio", "portable")),
    new Document("name", "Desk Chair").append("price", 299.99)
        .append("category", "furniture").append("stock", 30)
        .append("tags", List.of("ergonomic", "office"))
));

// Find electronics under $500
List<Document> affordable = products.find(
    new Document("$and", List.of(
        new Document("category", "electronics"),
        new Document("price", new Document("$lt", 500))
    ))
).sort(new Document("price", 1)).toList();

// Revenue per category
AggregationPipeline pipeline = new AggregationPipeline()
    .group(new Document("_id", "$category")
        .append("totalValue", new Document("$sum", "$price"))
        .append("productCount", new Document("$sum", 1))
        .append("avgPrice", new Document("$avg", "$price")))
    .sort(new Document("totalValue", -1));

List<Document> revenue = pipeline.execute(products.find().toList());
```

### User Analytics

```java
Collection events = db.getCollection("events");

// Count events by type
AggregationPipeline countByType = new AggregationPipeline()
    .group(new Document("_id", "$eventType")
        .append("count", new Document("$sum", 1)))
    .sort(new Document("count", -1))
    .limit(10);

// Find users with specific tags using $all
List<Document> powerUsers = users.find(
    new Document("tags", new Document("$all", List.of("premium", "active")))
).toList();

// Regex search for email domain
List<Document> gmailUsers = users.find(
    new Document("email", new Document("$regex", "@gmail\\.com$"))
).toList();
```

---

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
