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
            this.ensureUsersTableExists();
            this.ensureCampaignsTableExists();
            this.ensureArtifactTablesExist();
            this.insertDefaultArtifactCategories();
            this.createDefaultUserIfNeeded();
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
            System.out.println("Qdrant database closed");
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
        String sql = """
            SELECT uuid, name, neo4j_label, quadrant_collection_name, user_id, description, 
                   created_at, updated_at, is_active, deleted_at, settings 
            FROM campains WHERE is_active = 1 AND deleted_at IS NULL
            """;
        
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
                
                // Set new fields
                campaign.setUserId(rs.getString("user_id"));
                campaign.setDescription(rs.getString("description"));
                campaign.setCreatedAt(rs.getLong("created_at"));
                campaign.setUpdatedAt(rs.getLong("updated_at"));
                campaign.setActive(rs.getBoolean("is_active"));
                
                Long deletedAt = rs.getLong("deleted_at");
                if (!rs.wasNull()) {
                    campaign.setDeletedAt(deletedAt);
                }
                
                campaign.setSettings(rs.getString("settings"));
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
        String sql = """
            SELECT uuid, name, neo4j_label, quadrant_collection_name, user_id, description, 
                   created_at, updated_at, is_active, deleted_at, settings 
            FROM campains WHERE uuid = ? AND is_active = 1 AND deleted_at IS NULL
            """;
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, uuid);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Campain campaign = new Campain(
                        rs.getString("uuid"),
                        rs.getString("name"),
                        rs.getString("neo4j_label"),
                        rs.getString("quadrant_collection_name")
                    );
                    
                    // Set new fields
                    campaign.setUserId(rs.getString("user_id"));
                    campaign.setDescription(rs.getString("description"));
                    campaign.setCreatedAt(rs.getLong("created_at"));
                    campaign.setUpdatedAt(rs.getLong("updated_at"));
                    campaign.setActive(rs.getBoolean("is_active"));
                    
                    Long deletedAt = rs.getLong("deleted_at");
                    if (!rs.wasNull()) {
                        campaign.setDeletedAt(deletedAt);
                    }
                    
                    campaign.setSettings(rs.getString("settings"));
                    return campaign;
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
        String insertSql = """
            INSERT INTO campains (uuid, name, neo4j_label, quadrant_collection_name, 
                                 user_id, description, created_at, updated_at, is_active) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
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
            
            // Ensure required fields are set
            if (campaign.getUserId() == null || campaign.getUserId().isEmpty()) {
                campaign.setUserId(getDefaultUserId());
            }
            
            long currentTime = System.currentTimeMillis() / 1000L;
            if (campaign.getCreatedAt() == 0) {
                campaign.setCreatedAt(currentTime);
            }
            if (campaign.getUpdatedAt() == 0) {
                campaign.setUpdatedAt(currentTime);
            }
            
            // Insert the new campaign
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setString(1, campaign.getUuid());
                pstmt.setString(2, campaign.getName());
                pstmt.setString(3, campaign.getNeo4jLabel());
                pstmt.setString(4, campaign.getQuadrantCollectionName());
                pstmt.setString(5, campaign.getUserId());
                pstmt.setString(6, campaign.getDescription());
                pstmt.setLong(7, campaign.getCreatedAt());
                pstmt.setLong(8, campaign.getUpdatedAt());
                pstmt.setBoolean(9, campaign.isActive());
                
                int affectedRows = pstmt.executeUpdate();
                return affectedRows > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error saving campaign to database: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Deletes a campaign from the SQLite database using soft delete.
     * @param uuid The UUID of the campaign to delete
     * @return true if the campaign was successfully deleted, false otherwise
     * @throws SQLException if there is an error executing the SQL query
     */
    public boolean deleteCampaignFromRelativeDB(String uuid) throws SQLException {
        String sql = """
            UPDATE campains 
            SET is_active = 0, deleted_at = ?, updated_at = ? 
            WHERE uuid = ? AND is_active = 1
            """;
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            long currentTime = System.currentTimeMillis() / 1000L;
            pstmt.setLong(1, currentTime);
            pstmt.setLong(2, currentTime);
            pstmt.setString(3, uuid);
            
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }
    
    /**
     * Ensures the 'users' table exists in SQLite database.
     */
    private void ensureUsersTableExists() {
        String createUsers = """
            CREATE TABLE IF NOT EXISTS users (
                id TEXT PRIMARY KEY,
                email TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                is_active BOOLEAN DEFAULT 1,
                deleted_at INTEGER DEFAULT NULL,
                email_verified BOOLEAN DEFAULT 0,
                email_verification_token TEXT,
                email_verification_expires_at INTEGER,
                last_login_at INTEGER,
                is_admin BOOLEAN DEFAULT 0
            )
            """;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(createUsers)) {
            pstmt.execute();
            System.out.println("Users table ensured");
        } catch (SQLException e) {
            System.err.println("Error creating users table: " + e.getMessage());
        }
    }
    
    /**
     * Ensures the 'campains' table exists in SQLite database.
     */
    private void ensureCampaignsTableExists() {
        String createCampains = """
            CREATE TABLE IF NOT EXISTS campains (
                uuid TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                neo4j_label TEXT NOT NULL,
                quadrant_collection_name TEXT NOT NULL,
                user_id TEXT NOT NULL,
                description TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                is_active BOOLEAN DEFAULT 1,
                deleted_at INTEGER DEFAULT NULL,
                settings TEXT,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(createCampains)) {
            pstmt.execute();
            System.out.println("Campains table ensured");
        } catch (SQLException e) {
            System.err.println("Error creating campains table: " + e.getMessage());
        }
    }

    /**
     * Ensures artifact-related tables exist in SQLite database.
     * Creates artifact_categories and artifact_categories_to_campaigns tables if they don't exist.
     */
    private void ensureArtifactTablesExist() {
        String createArtifactCategories = """
            CREATE TABLE IF NOT EXISTS artifact_categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE NOT NULL,
                description TEXT NOT NULL,
                is_active BOOLEAN DEFAULT 1,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;
        
        String createCampaignCategories = """
            CREATE TABLE IF NOT EXISTS artifact_categories_to_campaigns (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                campaign_uuid TEXT NOT NULL,
                category_name TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (campaign_uuid) REFERENCES campains(uuid) ON DELETE CASCADE,
                FOREIGN KEY (category_name) REFERENCES artifact_categories(name) ON DELETE CASCADE,
                UNIQUE(campaign_uuid, category_name)
            )
            """;
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            // Enable foreign key constraints
            try (PreparedStatement enableFk = conn.prepareStatement("PRAGMA foreign_keys = ON")) {
                enableFk.execute();
            }
            
            // Create artifact_categories table
            try (PreparedStatement pstmt = conn.prepareStatement(createArtifactCategories)) {
                pstmt.execute();
                System.out.println("Artifact categories table ensured");
            }
            
            // Create artifact_categories_to_campaigns table
            try (PreparedStatement pstmt = conn.prepareStatement(createCampaignCategories)) {
                pstmt.execute();
                System.out.println("Campaign-categories mapping table ensured");
            }
            
        } catch (SQLException e) {
            System.err.println("Error creating artifact tables: " + e.getMessage());
        }
    }
    
    /**
     * Initializes default artifact categories if they don't exist.
     * Adds the four default categories: characters, locations, items, events.
     */
    private void insertDefaultArtifactCategories() {
        String insertSql = "INSERT OR IGNORE INTO artifact_categories (name, description) VALUES (?, ?)";
        
        String[][] defaultCategories = {
            {"characters", "People, creatures, and sentient beings within the narrative"},
            {"locations", "Places, regions, buildings, and geographical features"},
            {"items", "Objects, weapons, artifacts, and physical possessions"},
            {"events", "Important occurrences, battles, ceremonies, and plot developments"}
        };
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            
            int insertedCount = 0;
            for (String[] category : defaultCategories) {
                pstmt.setString(1, category[0]);
                pstmt.setString(2, category[1]);
                int affected = pstmt.executeUpdate();
                if (affected > 0) {
                    insertedCount++;
                }
            }
            
            if (insertedCount > 0) {
                System.out.println("Inserted " + insertedCount + " default artifact categories");
            }
            
        } catch (SQLException e) {
            System.err.println("Error inserting default artifact categories: " + e.getMessage());
        }
    }
    
    /**
     * Creates a default user if no users exist in the database.
     * This is needed for migration from old schema and testing purposes.
     */
    private void createDefaultUserIfNeeded() {
        String checkUsersSql = "SELECT COUNT(*) FROM users";
        String insertDefaultUserSql = """
            INSERT INTO users (id, email, password_hash, created_at, updated_at, is_admin) 
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            // Check if any users exist
            try (PreparedStatement checkStmt = conn.prepareStatement(checkUsersSql);
                 ResultSet rs = checkStmt.executeQuery()) {
                
                if (rs.next() && rs.getInt(1) == 0) {
                    // No users exist, create default user
                    long currentTime = System.currentTimeMillis() / 1000L;
                    
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertDefaultUserSql)) {
                        insertStmt.setString(1, "default-user");
                        insertStmt.setString(2, "admin@localhost");
                        insertStmt.setString(3, "dummy-hash-for-migration");
                        insertStmt.setLong(4, currentTime);
                        insertStmt.setLong(5, currentTime);
                        insertStmt.setBoolean(6, true);
                        
                        int affected = insertStmt.executeUpdate();
                        if (affected > 0) {
                            System.out.println("Created default user for migration purposes");
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error creating default user: " + e.getMessage());
        }
    }
    
    /**
     * Gets the default user ID for migration purposes.
     * @return the default user ID or null if not found
     */
    public String getDefaultUserId() {
        String sql = "SELECT id FROM users WHERE email = 'admin@localhost'";
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getString("id");
            }
        } catch (SQLException e) {
            System.err.println("Error getting default user ID: " + e.getMessage());
        }
        
        return "default-user"; // Fallback to known default
    }
}
