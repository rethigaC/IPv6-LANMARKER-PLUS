package com.yourproject;

import static spark.Spark.*;
import com.google.gson.Gson;
import java.nio.file.Paths;

/**
 * The main entry point for the application.
 * Starts the database, the web server, and the update service.
 */
public class Main {

    public static void main(String[] args) {
        
        // 1. Initialize the Database (connects to MongoDB)
        DatabaseManager.initializeDatabase();

        // 2. Start the API Web Server
        Gson gson = new Gson();
        port(8080); // Set the server port
        
        // Serve the frontend files from 'src/main/resources/public'
        staticFiles.location("/public"); 
        
        // Define the API endpoint for the map
        get("/api/landmarks", (req, res) -> {
            res.type("application/json");
            // This method queries the DB for reliable landmarks
            return DatabaseManager.getReliableLandmarksForMap();
        }, gson::toJson); // Automatically convert List to JSON

        // API endpoint for Top 10 Countries
        get("/api/stats/top-countries", (req, res) -> {
            res.type("application/json");
            return DatabaseManager.getTopCountries();
        }, gson::toJson);

        // API endpoint for Top 10 AS
        get("/api/stats/top-as", (req, res) -> {
            res.type("application/json");
            return DatabaseManager.getTopAutonomousSystems();
        }, gson::toJson);

        System.out.println("-------------------------------------------------");
        System.out.println("Web server started on http://localhost:8080");
        System.out.println("-------------------------------------------------");

        // 3. Start the continuous update service
        UpdateService updateService = new UpdateService();
        updateService.start();

        // 4. Run the one-time import
        DataImporter importer = new DataImporter(updateService);
        
        System.out.println("\n--- Import will start now... ---");

        // This line is now active and points to your file:
        importer.importHitlist(Paths.get("D:\\ipv6-geolocator\\data\\responsive-addresses.txt"));
        
        
        // 5. (Optional) Run the Evaluation
        // LandmarkEvaluator evaluator = new LandmarkEvaluator();
        // evaluator.evaluateAllLandmarks();
    } 
}