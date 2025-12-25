// Inside src/main/java/com/yourproject/UpdateService.java
//
// Add these imports at the top:
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
// (end of new imports)

// (All other code in the file is the same... just replace the method below)

    /**
     * This is the core update logic for MongoDB.
     * It's called by the DataImporter for every EUI-64 IP it finds.
     */
    public void checkForPrefixRotation(String newIp, String iid) {
        try {
            MongoCollection<Document> landmarks = DatabaseManager.getLandmarksCollection();
            
            // Find a landmark with this IID
            Document existingLandmark = landmarks.find(Filters.eq("interface_id", iid)).first();

            if (existingLandmark != null) { // We are tracking this IID
                String oldIp = existingLandmark.getString("_id");
                
                if (!oldIp.equals(newIp)) {
                    // Prefix Rotation Detected!
                    System.out.println("Prefix rotation detected for IID: " + iid + ". Updating " + oldIp + " -> " + newIp);

                    // We must delete the old document and insert a new one
                    // because we are using the IP address as the unique _id.
                    
                    // 1. Delete the old landmark
                    landmarks.deleteOne(Filters.eq("_id", oldIp));
                    
                    // 2. Insert the new landmark (by re-using the old data)
                    Document newLandmarkDoc = new Document("_id", newIp)
                        .append("interface_id", existingLandmark.getString("interface_id"))
                        .append("wan_mac", existingLandmark.getString("wan_mac"))
                        .append("latitude", existingLandmark.getDouble("latitude"))
                        .append("longitude", existingLandmark.getDouble("longitude"))
                        .append("source_offset", existingLandmark.getInteger("source_offset"))
                        .append("is_reliable", existingLandmark.getBoolean("is_reliable", false))
                        .append("is_dynamic", true) // Mark as dynamic
                        .append("created_at", existingLandmark.getDate("created_at"))
                        .append("last_updated", new java.util.Date()); // Set new update time

                    landmarks.insertOne(newLandmarkDoc);
                }
            }
            // If existingLandmark is null, the DataImporter will save it.
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }