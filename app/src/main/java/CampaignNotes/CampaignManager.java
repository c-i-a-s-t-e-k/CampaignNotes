package CampaignNotes;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

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
    private final DataBaseLoader dbLoader;
    
    /**
     * Constructor that initializes the CampainManager and loads campaign data from the SQLite database.
     */
    public CampaignManager() {
        dbLoader = new DataBaseLoader();
        loadCampaignsFromDatabase();
    }
    
    /**
     * Loads all campaigns from the SQLite database and populates the campaignsMap with campaign objects.
     */
    private void loadCampaignsFromDatabase() {
        campaignsMap.clear();
        List<Campain> campaigns = dbLoader.getAllCampaigns();
        for (Campain campaign : campaigns) {
            campaignsMap.put(campaign.getUuid(), campaign);
        }
    }

    
    /**
     * Checks if both Neo4j and Quadrant databases are up and running.
     * @return true if both databases are available, false otherwise
     */
    public boolean checkDatabasesAvailability() {
        return dbLoader.checkDatabasesAvailability();
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
                    campaignName + "CampaignLabel" + uuid.toString(),
                    campaignName + "CampaignCollection" + uuid.toString()
            );

            dbLoader.saveCampaignToRelativeDB(newCampain);
            dbLoader.getQdrantClient().createCollectionAsync(newCampain.getQuadrantCollectionName(),
                    VectorParams.newBuilder().setDistance(Distance.Cosine).setSize(3072).build() // OpenAI embeddings dimension
            ).get();    
            
            campaignsMap.put(newCampain.getUuid(), newCampain);
            return newCampain;
        } catch (SQLException | ExecutionException | InterruptedException e) {
            System.err.println("Error creating new campaign: " + e.getMessage());
            return null;
        }
    }

    /**
     * Deletes a campaign with the given UUID from both the local map and databases.
     * @param uuid The UUID of the campaign to delete
     * @return true if the campaign was successfully deleted, false otherwise
     */
    public boolean deleteCampaign(String uuid) {
        try {
            Campain campaignToDelete = campaignsMap.get(uuid);
            if (campaignToDelete == null) {
                return false;
            }

            // Delete from Qdrant collection
            dbLoader.getQdrantClient().deleteCollectionAsync(campaignToDelete.getQuadrantCollectionName()).get();
            
            // Delete from database
            dbLoader.deleteCampaignFromRelativeDB(uuid);
            
            // Remove from local map
            campaignsMap.remove(uuid);
            
            return true;
        } catch (Exception e) {
            System.err.println("Error deleting campaign: " + e.getMessage());
            return false;
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
            QdrantClient qdrantClient = dbLoader.getQdrantClient();
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
            QdrantClient qdrantClient = dbLoader.getQdrantClient();
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
                System.out.println("Collection does not exist: " + collectionName);
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
            long numericId = Math.abs(note.getId().hashCode());
            
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
            
            System.out.println("Note successfully stored in Qdrant collection: " + 
                campaign.getQuadrantCollectionName() + " with ID: " + numericId);
            
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
            System.out.println("Creating Qdrant collection: " + campaign.getQuadrantCollectionName());
            qdrantClient.createCollectionAsync(
                    campaign.getQuadrantCollectionName(),
                    VectorParams.newBuilder()
                            .setDistance(Distance.Cosine)
                            .setSize(3072) // OpenAI embeddings dimension
                            .build()
            ).get();
        }
    }
}
