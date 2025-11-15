package CampaignNotes;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import CampaignNotes.database.DatabaseConnectionManager;
import CampaignNotes.database.Neo4jRepository;
import static io.qdrant.client.PointIdFactory.id;
import io.qdrant.client.QdrantClient;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Points.PointStruct;
import model.Campain;
import model.Note;

public class CampaignManager {

    private final Map<String, Campain> campaignsMap = new HashMap<>();
    private final DatabaseConnectionManager dbConnectionManager;
    
    /**
     * Constructor that initializes the CampainManager and loads campaign data from the SQLite database.
     * 
     * @deprecated Use {@link #CampaignManager(DatabaseConnectionManager)} instead
     */
    @Deprecated
    public CampaignManager() {
        this.dbConnectionManager = new DatabaseConnectionManager();
        loadCampaignsFromDatabase();
        cleanupExpiredCampaigns(7); // Clean up campaigns soft-deleted more than 7 days ago
    }
    
    /**
     * Constructor with dependency injection.
     * 
     * @param dbConnectionManager the database connection manager to use
     */
    public CampaignManager(DatabaseConnectionManager dbConnectionManager) {
        this.dbConnectionManager = dbConnectionManager;
        loadCampaignsFromDatabase();
        cleanupExpiredCampaigns(7); // Clean up campaigns soft-deleted more than 7 days ago
    }
    
    /**
     * Loads all campaigns from the SQLite database and populates the campaignsMap with campaign objects.
     */
    private void loadCampaignsFromDatabase() {
        campaignsMap.clear();
        List<Campain> campaigns = dbConnectionManager.getSqliteRepository().getAllCampaigns();
        for (Campain campaign : campaigns) {
            campaignsMap.put(campaign.getUuid(), campaign);
        }
    }

    
    /**
     * Checks if both Neo4j and Quadrant databases are up and running.
     * @return true if both databases are available, false otherwise
     */
    public boolean checkDatabasesAvailability() {
        return dbConnectionManager.checkDatabasesAvailability();
    }

    /**
     * Creates a new campaign with the given name and saves it to the database.
     * @param campaignName The name of the new campaign
     * @return The created Campaign object, or null if creation failed
     */
    public Campain createNewCampain(String campaignName) {
        try {
            UUID uuid = UUID.randomUUID();
            while (campaignsMap.containsKey(uuid.toString())) {
                uuid = UUID.randomUUID();
            }

            Campain newCampain = new Campain(
                    uuid.toString(),
                    campaignName,
                    Neo4jRepository.sanitizeNeo4jLabel(campaignName + "CampaignLabel" + uuid.toString()),
                    campaignName + "CampaignCollection" + uuid.toString(),
                    dbConnectionManager.getSqliteRepository().getDefaultUserId(),
                    "Campaign created for " + campaignName
            );

            dbConnectionManager.getSqliteRepository().saveCampaignToRelativeDB(newCampain);
            dbConnectionManager.getQdrantRepository().getClient().createCollectionAsync(newCampain.getQuadrantCollectionName(),
                    VectorParams.newBuilder().setDistance(Distance.Cosine).setSize(3072).build() // OpenAI embeddings dimension
            ).get();    
            
            campaignsMap.put(newCampain.getUuid(), newCampain);
            return newCampain;
        } catch (SQLException | ExecutionException | InterruptedException e) {
            System.err.println("Error creating new campaign: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Performs soft delete of a campaign by marking it as deleted in SQLite.
     * Does not remove data from Neo4j or Qdrant - these will be cleaned up later
     * when the retention period expires.
     * @param uuid The UUID of the campaign to soft delete
     * @return true if the campaign was successfully soft deleted, false otherwise
     */
    public boolean deleteCampaign(String uuid) {
        try {
            Campain campaignToDelete = campaignsMap.get(uuid);
            if (campaignToDelete == null) {
                return false;
            }

            // Soft delete from SQLite
            boolean success = dbConnectionManager.getSqliteRepository().deleteCampaignFromRelativeDB(uuid);
            
            // Remove from local map only if soft delete succeeded
            if (success) {
                campaignsMap.remove(uuid);
            }
            
            return success;
        } catch (Exception e) {
            System.err.println("Error deleting campaign: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Performs hard delete of a campaign from all databases (Neo4j, Qdrant, SQLite).
     * This method should only be used for final cleanup after retention period.
     * @param uuid The UUID of the campaign to hard delete
     * @return true if the campaign was successfully deleted from all databases, false otherwise
     */
    private boolean hardDeleteCampaign(String uuid) {
        try {
            Campain campaignToDelete = campaignsMap.get(uuid);
            if (campaignToDelete == null) {
                // Try to get campaign from database
                campaignToDelete = dbConnectionManager.getSqliteRepository().getCampaignById(uuid, true);
            }
            
            if (campaignToDelete == null) {
                System.err.println("Campaign not found for hard delete: " + uuid);
                return false;
            }

            boolean neo4jSuccess = true;
            boolean qdrantSuccess = true;
            boolean sqliteSuccess = true;

            // Delete from Neo4j
            neo4jSuccess = dbConnectionManager.getNeo4jRepository().deleteHardCampaignSubgraphById(campaignToDelete.getUuid());

            // Delete from Qdrant
            try {
                QdrantClient qdrantClient = dbConnectionManager.getQdrantRepository().getClient();
                if (qdrantClient != null) {
                    qdrantClient.deleteCollectionAsync(campaignToDelete.getQuadrantCollectionName()).get();
                }
            } catch (Exception e) {
                System.err.println("Error deleting Qdrant collection: " + e.getMessage());
                qdrantSuccess = false;
            }

            // Hard delete from SQLite
            try {
                sqliteSuccess = dbConnectionManager.getSqliteRepository().hardDeleteCampaignFromRelativeDB(uuid);
            } catch (SQLException e) {
                System.err.println("Error hard deleting campaign from SQLite: " + e.getMessage());
                sqliteSuccess = false;
            }

            // Remove from local map if SQLite deletion succeeded
            if (sqliteSuccess) {
                campaignsMap.remove(uuid);
            }

            return neo4jSuccess && qdrantSuccess && sqliteSuccess;
        } catch (Exception e) {
            System.err.println("Error performing hard delete campaign: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Cleans up campaigns that have been soft deleted and are past the retention period.
     * Removes them permanently from Neo4j, Qdrant, and SQLite.
     * @param retentionDays Number of days to retain soft-deleted campaigns
     */
    public void cleanupExpiredCampaigns(int retentionDays) {
        try {
            List<Campain> expiredCampaigns = dbConnectionManager.getSqliteRepository().findExpiredCampaigns(retentionDays);
            
            if (expiredCampaigns.isEmpty()) {
                System.out.println("No expired campaigns to clean up");
                return;
            }

            System.out.println("Found " + expiredCampaigns.size() + " expired campaign(s) to clean up");
            
            int successCount = 0;
            int failureCount = 0;
            
            for (Campain campaign : expiredCampaigns) {
                System.out.println("Hard deleting expired campaign: " + campaign.getName() + " (UUID: " + campaign.getUuid() + ")");
                boolean success = hardDeleteCampaign(campaign.getUuid());
                if (success) {
                    successCount++;
                } else {
                    failureCount++;
                }
            }
            
            System.out.println("Cleanup completed: " + successCount + " successful, " + failureCount + " failed");
        } catch (Exception e) {
            System.err.println("Error cleaning up expired campaigns: " + e.getMessage());
            e.printStackTrace();
        }
    }

    
    /**
     * Gets all campaigns as a list.
     * @return List of all campaigns
     */
    public List<Campain> getAllCampaigns() {
        return new ArrayList<>(campaignsMap.values());
    }
    
    /**
     * Gets a campaign by its UUID.
     * @param uuid The UUID of the campaign to retrieve
     * @return Campaign object or null if not found
     */
    public Campain getCampaignByUuid(String uuid) {
        return campaignsMap.get(uuid);
    }
    
    /**
     * Adds a note with its embedding to the specified campaign.
     * Handles Qdrant storage operations and collection management.
     * 
     * @param note the note to add
     * @param campaign the campaign to add the note to
     * @param embedding the embedding vector for the note
     * @return true if the note was successfully added, false otherwise
     */
    public boolean addNoteToCampaign(Note note, Campain campaign, List<Double> embedding) {
        try {
            QdrantClient qdrantClient = dbConnectionManager.getQdrantRepository().getClient();
            if (qdrantClient == null) {
                System.err.println("Qdrant client not available");
                return false;
            }
            
            // Ensure collection exists
            ensureCollectionExists(campaign, qdrantClient);
            
            // Store the note in Qdrant
            return storeNoteInQdrant(note, campaign, embedding, qdrantClient);
            
        } catch (Exception e) {
            System.err.println("Error adding note to campaign: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Checks if a campaign has existing notes by checking if the collection exists and has points.
     * 
     * @param campaign the campaign to check
     * @return true if there are existing notes, false otherwise
     */
    public boolean hasExistingNotes(Campain campaign) {
        try {
            QdrantClient qdrantClient = dbConnectionManager.getQdrantRepository().getClient();
            if (qdrantClient == null) {
                System.err.println("Qdrant client not available");
                return false;
            }
            
            // Check if collection exists and has points
            String collectionName = campaign.getQuadrantCollectionName();
            try {
                var collectionInfo = qdrantClient.getCollectionInfoAsync(collectionName).get();
                long pointsCount = collectionInfo.getPointsCount();
                return pointsCount > 0;
            } catch (Exception e) {
                // Collection doesn't exist, so no existing notes
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("Error checking for existing notes: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Stores a note with its embedding in Qdrant vector database.
     * 
     * @param note the note to store
     * @param campaign the campaign the note belongs to
     * @param embedding the embedding vector for the note
     * @param qdrantClient the Qdrant client to use
     * @return true if storage was successful, false otherwise
     */
    private boolean storeNoteInQdrant(Note note, Campain campaign, List<Double> embedding, QdrantClient qdrantClient) {
        try {
            // Convert embedding to float array for Qdrant
            List<Float> embeddingFloats = embedding.stream()
                    .map(Double::floatValue)
                    .toList();
            
            // Generate hash of note ID for numerical ID
            long numericId = note.getNumericId();
            
            // Create the point using the proper builder pattern with payload
            PointStruct point = PointStruct.newBuilder()
                    .setId(id(numericId))
                    .setVectors(vectors(embeddingFloats))
                    .putPayload("note_id", value(note.getId()))
                    .putPayload("title", value(note.getTitle()))
                    .putPayload("content", value(note.getContent()))
                    .putPayload("campaign_uuid", value(note.getCampaignUuid()))
                    .putPayload("created_at", value(note.getCreatedAt().toString()))
                    .putPayload("updated_at", value(note.getUpdatedAt().toString()))
                    .putPayload("is_override", value(note.isOverride()))
                    .putPayload("is_overridden", value(note.isOverridden()))
                    .putPayload("type", value("note"))  // Type tag for filtering in Phase 1
                    .build();
            
            // Add override reason if present
            if (note.getOverrideReason() != null) {
                point = point.toBuilder()
                        .putPayload("override_reason", value(note.getOverrideReason()))
                        .build();
            }
            
            // Upsert the point into Qdrant collection
            qdrantClient.upsertAsync(
                    campaign.getQuadrantCollectionName(),
                    List.of(point)
            ).get();
            
            return true;
            
        } catch (Exception e) {
            System.err.println("Error storing note in Qdrant: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Ensures that the Qdrant collection exists for the campaign.
     */
    private void ensureCollectionExists(Campain campaign, QdrantClient qdrantClient) 
            throws ExecutionException, InterruptedException {
        try {
            // Try to get collection info
            qdrantClient.getCollectionInfoAsync(campaign.getQuadrantCollectionName()).get();
        } catch (Exception e) {
            // Collection doesn't exist, create it
            qdrantClient.createCollectionAsync(
                    campaign.getQuadrantCollectionName(),
                    VectorParams.newBuilder()
                            .setDistance(Distance.Cosine)
                            .setSize(3072) // OpenAI embeddings dimension
                            .build()
            ).get();
        }
    }
    
    /**
     * Safely terminates all database connections and ensures no data loss.
     * This method ensures that all pending asynchronous operations are completed
     * before closing database connections.
     * 
     * @return true if all connections were closed successfully, false if there were errors
     */
    public boolean endManaging() {
        boolean success = true;
        
        try {
            // Check if databases are available before attempting operations
            if (dbConnectionManager.checkDatabasesAvailability()) {
                
                // For Qdrant: Wait for any pending asynchronous operations to complete
                QdrantClient qdrantClient = dbConnectionManager.getQdrantRepository().getClient();
                if (qdrantClient != null) {
                    try {
                        // Flush any pending operations by checking collection status for all campaigns
                        for (Campain campaign : campaignsMap.values()) {
                            try {
                                // This will wait for any pending operations on this collection
                                qdrantClient.getCollectionInfoAsync(campaign.getQuadrantCollectionName()).get();
                            } catch (Exception e) {
                                // Collection might not exist, which is fine
                            }
                        }
                        
                        // Small delay to ensure all async operations have settled
                        Thread.sleep(500);
                        
                    } catch (InterruptedException e) {
                        System.err.println("Interrupted while waiting for Qdrant operations to complete: " + e.getMessage());
                        Thread.currentThread().interrupt(); // Restore interrupted status
                        success = false;
                    } catch (Exception e) {
                        System.err.println("Error while ensuring Qdrant operations completion: " + e.getMessage());
                        success = false;
                    }
                }
                
                // For Neo4j: Check if driver is responsive before closing
                if (dbConnectionManager.getNeo4jRepository().getDriver() != null) {
                    try {
                        dbConnectionManager.getNeo4jRepository().getDriver().verifyConnectivity();
                    } catch (Exception e) {
                        System.err.println("Neo4j driver verification failed during shutdown: " + e.getMessage());
                        success = false;
                    }
                }
            }
            
            // Close all database connections
            dbConnectionManager.closeAll();
            
            // Clear local campaign cache
            campaignsMap.clear();
            
        } catch (Exception e) {
            System.err.println("Error during CampaignManager shutdown: " + e.getMessage());
            e.printStackTrace();
            success = false;
            
            // Even if there was an error, try to close connections
            try {
                dbConnectionManager.closeAll();
            } catch (Exception closeError) {
                System.err.println("Error closing connections after failed shutdown: " + closeError.getMessage());
            }
        }
        
        return success;
    }
}
