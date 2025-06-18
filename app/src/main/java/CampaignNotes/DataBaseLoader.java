package CampaignNotes;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.exceptions.ServiceUnavailableException;

import io.github.cdimascio.dotenv.Dotenv;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import model.Campain;

public class DataBaseLoader {
    private static final String DB_PATH = "sqlite.db";
    private Driver neo4jDriver;
    private QdrantClient qdrantClient;
    private Dotenv dotenv;
    
    /**
     * Constructor initializes the Dotenv instance for environment variables.
     */
    public DataBaseLoader() {
        try {
            this.dotenv = Dotenv.configure().directory("./").load();
        } catch (Exception e) {
            System.err.println("Error loading .env file: " + e.getMessage() + " working directory: " + System.getProperty("user.dir"));
        }
    }
    
    /**
     * Initializes Neo4j driver if not already created.
     * @return Neo4j Driver instance or null if connection failed
     */
    public Driver getNeo4jDriver() {
        if (neo4jDriver == null) {
            String dbUri = dotenv.get("NEO4J_URI");
            String dbUser = dotenv.get("NEO4J_USER");
            String dbPassword = dotenv.get("NEO4J_PASSWORD");
            
            if (dbUri == null || dbUser == null || dbPassword == null) {
                System.err.println("Neo4j connection parameters are not properly defined");
                return null;
            }
            
            try {
                neo4jDriver = GraphDatabase.driver(dbUri, AuthTokens.basic(dbUser, dbPassword));
                neo4jDriver.verifyConnectivity();
                System.out.println("Neo4j driver initialized successfully");
            } catch (Exception e) {
                System.err.println("Failed to initialize Neo4j driver: " + e.getMessage());
                return null;
            }
        }
        return neo4jDriver;
    }
    
    /**
     * Initializes Qdrant client if not already created.
     * @return QdrantClient instance or null if connection failed
     */
    public QdrantClient getQdrantClient() {
        if (qdrantClient == null) {
            String qdrantUrl = dotenv.get("QUADRANT_URL");
            String qdrantPortStr = dotenv.get("QUADRANT_GRPC_PORT");
            
            if (qdrantUrl == null || qdrantUrl.isEmpty() || qdrantPortStr == null) {
                System.err.println("Qdrant connection parameters are not properly defined");
                return null;
            }
            
            try {
                int qdrantPort = Integer.parseInt(qdrantPortStr);
                qdrantClient = new QdrantClient(
                        QdrantGrpcClient.newBuilder(qdrantUrl, qdrantPort, false).build());
                System.out.println("Qdrant client initialized successfully");
            } catch (Exception e) {
                System.err.println("Failed to initialize Qdrant client: " + e.getMessage());
                return null;
            }
        }
        return qdrantClient;
    }
    
    /**
     * Checks if both Neo4j and Qdrant databases are available.
     * @return true if both databases are available, false otherwise
     */
    public boolean checkDatabasesAvailability() {
        boolean neo4jAvailable = checkNeo4jAvailability();
        boolean qdrantAvailable = checkQdrantAvailability();
        return neo4jAvailable && qdrantAvailable;
    }
    
    /**
     * Checks if Neo4j database is available.
     * @return true if Neo4j is available, false otherwise
     */
    private boolean checkNeo4jAvailability() {
        try {
            Driver driver = getNeo4jDriver();
            if (driver != null) {
                driver.verifyConnectivity();
                System.out.println("Neo4j database is available");
                return true;
            }
            return false;
        } catch (ServiceUnavailableException e) {
            System.err.println("Neo4j database is unavailable: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Error connecting to Neo4j: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks if Qdrant database is available.
     * @return true if Qdrant is available, false otherwise
     */
    private boolean checkQdrantAvailability() {
        try {
            QdrantClient client = getQdrantClient();
            if (client != null) {
                // Get the ListenableFuture for the collection list
                var future = client.listCollectionsAsync();
                
                // Wait for the result with a timeout (5 seconds)
                future.get(5, java.util.concurrent.TimeUnit.SECONDS);
                
                System.out.println("Qdrant database is available");
                return true;
            }
            return false;
        } catch (Exception e) {
            System.err.println("Error connecting to Qdrant: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Closes database connections.
     */
    public void closeConnections() {
        if (neo4jDriver != null) {
            try {
                neo4jDriver.close();
                System.out.println("Neo4j connection closed");
            } catch (Exception e) {
                System.err.println("Error closing Neo4j connection: " + e.getMessage());
            }
        }
        if (qdrantClient != null) {
            qdrantClient.close();
            System.out.println("Neo4j database closed");
        }
    }
    
    /**
     * Loads all campaign UUIDs from the SQLite database.
     * @return Set of campaign UUIDs
     */
    public Set<String> loadCampaignIds() {
        Set<String> campaignIds = new HashSet<>();
        String sql = "SELECT uuid FROM campains";
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                String uuid = rs.getString("uuid");
                campaignIds.add(uuid);
            }
            
            System.out.println("Loaded " + campaignIds.size() + " campaign UUIDs from the database");
            
        } catch (SQLException e) {
            System.err.println("Error loading campaign IDs from database: " + e.getMessage());
        }
        
        return campaignIds;
    }
    
    /**
     * Gets all campaigns from the SQLite database.
     * @return List of Campain objects
     */
    public List<Campain> getAllCampaigns() {
        List<Campain> campaigns = new ArrayList<>();
        String sql = "SELECT uuid, name, neo4j_label, quadrant_collection_name FROM campains";
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                Campain campaign = new Campain(
                    rs.getString("uuid"),
                    rs.getString("name"),
                    rs.getString("neo4j_label"),
                    rs.getString("quadrant_collection_name")
                );
                campaigns.add(campaign);
            }
            
        } catch (SQLException e) {
            System.err.println("Error retrieving campaigns from database: " + e.getMessage());
        }
        
        return campaigns;
    }
    
    /**
     * Gets a campaign by its UUID.
     * @param uuid The UUID of the campaign to retrieve
     * @return Campain object or null if not found
     */
    public Campain getCampaignById(String uuid) {
        String sql = "SELECT uuid, name, neo4j_label, quadrant_collection_name FROM campains WHERE uuid = ?";
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, uuid);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Campain(
                        rs.getString("uuid"),
                        rs.getString("name"),
                        rs.getString("neo4j_label"),
                        rs.getString("quadrant_collection_name")
                    );
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error retrieving campaign by ID: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Saves a new campaign to the SQLite database.
     * @param campaign The campaign to save
     * @return true if saved successfully, false otherwise
     * @throws SQLException if a campaign with the same UUID already exists
     */
    public boolean saveCampaignToRelativeDB(Campain campaign) throws SQLException {
        // First check if a campaign with this UUID already exists
        String checkSql = "SELECT uuid FROM campains WHERE uuid = ?";
        String insertSql = "INSERT INTO campains (uuid, name, neo4j_label, quadrant_collection_name) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            // Check for existing UUID
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, campaign.getUuid());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        throw new SQLException("Campaign with UUID " + campaign.getUuid() + " already exists");
                    }
                }
            }
            
            // Insert the new campaign
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setString(1, campaign.getUuid());
                pstmt.setString(2, campaign.getName());
                pstmt.setString(3, campaign.getNeo4jLabel());
                pstmt.setString(4, campaign.getQuadrantCollectionName());
                
                int affectedRows = pstmt.executeUpdate();
                return affectedRows > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error saving campaign to database: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Deletes a campaign from the SQLite database.
     * @param uuid The UUID of the campaign to delete
     * @return true if the campaign was successfully deleted, false otherwise
     * @throws SQLException if there is an error executing the SQL query
     */
    public boolean deleteCampaignFromRelativeDB(String uuid) throws SQLException {
        String sql = "DELETE FROM campains WHERE uuid = ?";
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, uuid);
            int affectedRows = pstmt.executeUpdate();
            
            return affectedRows > 0;
        }
    }
}
