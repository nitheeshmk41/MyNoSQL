package com.mynosql.database;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The top-level database server — manages multiple databases.
 * Equivalent to a MongoDB server instance.
 */
public class DatabaseServer {

    private final Path baseDir;
    private final Map<String, Database> databases;

    public DatabaseServer(String dataPath) throws IOException {
        this.baseDir = Paths.get(dataPath);
        Files.createDirectories(baseDir);
        this.databases = new ConcurrentHashMap<>();
        loadExistingDatabases();
    }

    public DatabaseServer() throws IOException {
        this("mynosql_data");
    }

    /** Get or create a database. */
    public Database getDatabase(String dbName) throws IOException {
        return databases.computeIfAbsent(dbName, name -> {
            try {
                return new Database(name, baseDir);
            } catch (IOException e) {
                throw new RuntimeException("Failed to open database: " + name, e);
            }
        });
    }

    /** List all database names. */
    public Set<String> listDatabases() {
        return Collections.unmodifiableSet(databases.keySet());
    }

    /** Drop a database. */
    public boolean dropDatabase(String dbName) throws IOException {
        Database db = databases.remove(dbName);
        if (db != null) {
            db.drop();
            return true;
        }
        return false;
    }

    public Path getBaseDir() {
        return baseDir;
    }

    // ---- Internal ----

    private void loadExistingDatabases() throws IOException {
        try (var stream = Files.list(baseDir)) {
            stream.filter(Files::isDirectory)
                    .forEach(p -> {
                        String dbName = p.getFileName().toString();
                        try {
                            databases.put(dbName, new Database(dbName, baseDir));
                        } catch (IOException e) {
                            System.err.println("Warning: failed to load database " + dbName + ": " + e.getMessage());
                        }
                    });
        }
    }
}
