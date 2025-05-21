package CampainNotes;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import model.Campain;

public class CampainManager {

    private final Map<String, Campain> campaignsMap = new HashMap<>();
    private final DataBaseLoader dbLoader;
    
    /**
     * Constructor that initializes the CampainManager and loads campaign data from the SQLite database.
     */
    public CampainManager() {
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

            dbLoader.saveCampaign(newCampain);
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
}
