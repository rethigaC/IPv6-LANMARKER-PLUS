package com.yourproject;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Manages all database connections and schema initialization for MongoDB.
 */
public class DatabaseManager {
    // --- MongoDB Connection Details ---
    private static final String DB_HOST = "localhost";
    private static final String DB_PORT = "27017";
    private static final String DB_NAME = "geolocation_db";
    
    // The MongoDB connection URL
    private static final String DB_URL = "mongodb://" + DB_HOST + ":" + DB_PORT;

    private static MongoClient mongoClient;
    private static MongoDatabase database;

    /**
     * Initializes the connection to the MongoDB server.
     */
    public static void initializeDatabase() {
        try {
            mongoClient = MongoClients.create(DB_URL);
            database = mongoClient.getDatabase(DB_NAME);
            // Test connection
            database.runCommand(new Document("ping", 1));
            System.out.println("MongoDB Database connected successfully.");
        } catch (Exception e) {
            System.out.println("Failed to connect to MongoDB. Is it running?");
            e.printStackTrace();
        }
    }

    /**
     * Gets the 'landmarks' collection.
     */
    public static MongoCollection<Document> getLandmarksCollection() {
        return database.getCollection("landmarks");
    }

    /**
     * Gets the 'bssid_cache' collection.
     */
    public static MongoCollection<Document> getCacheCollection() {
        return database.getCollection("bssid_cache");
    }
    
    // --- API Methods ---

    /**
     * Fetches reliable landmarks to be displayed on the frontend map.
     */
    public static List<Map<String, Object>> getReliableLandmarksForMap() {
        List<Map<String, Object>> landmarks = new ArrayList<>();
        
        // Find landmarks where is_reliable = true
        getLandmarksCollection().find(Filters.eq("is_reliable", true))
            .forEach(doc -> landmarks.add(doc)); // 'doc' is already a Map-like object
            
        return landmarks;
    }

    /**
     * Fetches the Top 10 countries based on landmark count.
     * This uses the MongoDB Aggregation Pipeline (the NoSQL version of GROUP BY).
     */
    public static List<Map<String, Object>> getTopCountries() {
        List<Map<String, Object>> results = new ArrayList<>();

        getLandmarksCollection().aggregate(
            Arrays.asList(
                Aggregates.match(Filters.exists("country")), // Only documents with a country
                Aggregates.group("$country", Accumulators.sum("count", 1)),
                Aggregates.sort(Sorts.descending("count")),
                Aggregates.limit(10),
                Aggregates.project(Projections.fields( // Re-shape the output
                    Projections.excludeId(),
                    Projections.computed("name", "$_id"),
                    Projections.include("count")
                ))
            )
        ).forEach(doc -> results.add(doc));
        
        return results;
    }

    /**
     * Fetches the Top 10 Autonomous Systems (AS) based on landmark count.
     */
    public static List<Map<String, Object>> getTopAutonomousSystems() {
        List<Map<String, Object>> results = new ArrayList<>();
        
        getLandmarksCollection().aggregate(
            Arrays.asList(
                Aggregates.match(Filters.exists("as_name")),
                Aggregates.group(new Document("name", "$as_name").append("asn", "$asn"), // Group by two fields
                    Accumulators.sum("count", 1)),
                Aggregates.sort(Sorts.descending("count")),
                Aggregates.limit(10),
                Aggregates.project(Projections.fields(
                    Projections.excludeId(),
                    Projections.computed("name", "$_id.name"),
                    Projections.computed("asn", "$_id.asn"),
                    Projections.include("count")
                ))
            )
        ).forEach(doc -> results.add(doc));

        return results;
    }
}