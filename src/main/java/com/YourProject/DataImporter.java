// Inside src/main/java/com/yourproject/DataImporter.java
//
// Add these imports at the top:
import com.mongodb.client.MongoCollection;
import org.bson.Document;

// (All other code in the file is the same... just replace the method below)

    /**
     * Saves the candidate landmark to the MongoDB database.
     */
    private void saveCandidateLandmark(String ip, String iid, byte[] mac, LocationFinder.LocationHit hit) {
        
        try {
            MongoCollection<Document> landmarks = DatabaseManager.getLandmarksCollection();

            // We use the IP address as the unique ID (_id) to prevent duplicates
            Document landmarkDoc = new Document("_id", ip)
                    .append("interface_id", iid)
                    .append("wan_mac", MacUtils.bytesToMacString(mac))
                    .append("latitude", hit.latitude)
                    .append("longitude", hit.longitude)
                    .append("source_offset", hit.offset)
                    .append("is_reliable", false) // Default to false
                    .append("is_dynamic", false) // Default to false
                    .append("created_at", new java.util.Date());
                    // last_updated will be set by the UpdateService

            // This command will insert the doc IF the _id doesn't exist.
            // If it does exist, it does nothing.
            landmarks.insertOne(landmarkDoc);
            
        } catch (com.mongodb.MongoWriteException e) {
            // This catches a duplicate key error, which we can safely ignore.
            if (e.getError().getCategory() != com.mongodb.ErrorCategory.DUPLICATE_KEY) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }