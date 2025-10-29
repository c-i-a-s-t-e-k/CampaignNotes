package CampaignNotes.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import model.Campain;

/**
 * Repository responsible for SQLite operations and schema initialization.
 */
public class SqliteRepository {
    private final String databasePath;

    public SqliteRepository(String databasePath) {
        this.databasePath = databasePath;
    }
    
    /**
     * Gets a connection to the SQLite database.
     * 
     * @return Connection to the SQLite database
     * @throws SQLException if connection fails
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }
    
    /**
     * Gets the database path.
     * 
     * @return the database path
     */
    public String getDatabasePath() {
        return databasePath;
    }

    public void ensureUsersTableExists() {
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
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             PreparedStatement pstmt = conn.prepareStatement(createUsers)) {
            pstmt.execute();
        } catch (SQLException e) {
            System.err.println("Error creating users table: " + e.getMessage());
        }
    }
    
    public void ensureCampaignsTableExists() {
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
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             PreparedStatement pstmt = conn.prepareStatement(createCampains)) {
            pstmt.execute();
        } catch (SQLException e) {
            System.err.println("Error creating campains table: " + e.getMessage());
        }
    }

    public void ensureArtifactTablesExist() {
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

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath)) {
            try (PreparedStatement enableFk = conn.prepareStatement("PRAGMA foreign_keys = ON")) {
                enableFk.execute();
            }
            try (PreparedStatement pstmt = conn.prepareStatement(createArtifactCategories)) {
                pstmt.execute();
            }
            try (PreparedStatement pstmt = conn.prepareStatement(createCampaignCategories)) {
                pstmt.execute();
            }
        } catch (SQLException e) {
            System.err.println("Error creating artifact tables: " + e.getMessage());
        }
    }

    public void insertDefaultArtifactCategories() {
        String insertSql = "INSERT OR IGNORE INTO artifact_categories (name, description) VALUES (?, ?)";

        String[][] defaultCategories = {
            {"characters", "People, creatures, and sentient beings within the narrative"},
            {"locations", "Places, regions, buildings, and geographical features"},
            {"items", "Objects, weapons, artifacts, and physical possessions"},
            {"events", "Important occurrences, battles, ceremonies, and plot developments"}
        };

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
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
        } catch (SQLException e) {
            System.err.println("Error inserting default artifact categories: " + e.getMessage());
        }
    }

    public Set<String> loadCampaignIds() {
        Set<String> campaignIds = new HashSet<>();
        String sql = "SELECT uuid FROM campains";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                String uuid = rs.getString("uuid");
                campaignIds.add(uuid);
            }
        } catch (SQLException e) {
            System.err.println("Error loading campaign IDs from database: " + e.getMessage());
        }
        return campaignIds;
    }

    public List<Campain> getAllCampaigns() {
        List<Campain> campaigns = new ArrayList<>();
        String sql = "SELECT uuid, name, neo4j_label, quadrant_collection_name FROM campains";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
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

    public Campain getCampaignById(String uuid) {
        String sql = "SELECT uuid, name, neo4j_label, quadrant_collection_name FROM campains WHERE uuid = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
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

    public boolean saveCampaignToRelativeDB(Campain campaign) throws SQLException {
        String checkSql = "SELECT uuid FROM campains WHERE uuid = ?";
        String insertSql = """
            INSERT INTO campains (uuid, name, neo4j_label, quadrant_collection_name, 
                                 user_id, description, created_at, updated_at, is_active) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath)) {
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

    public boolean deleteCampaignFromRelativeDB(String uuid) throws SQLException {
        String sql = "DELETE FROM campains WHERE uuid = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }
    
    /**
     * Creates a default user if no users exist in the database.
     * This is needed for migration from old schema and testing purposes.
     */
    public void createDefaultUserIfNeeded() {
        String checkUsersSql = "SELECT COUNT(*) FROM users";
        String insertDefaultUserSql = """
            INSERT INTO users (id, email, password_hash, created_at, updated_at, is_admin) 
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath)) {
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
                        
                        insertStmt.executeUpdate();
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
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
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


