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
            for (String[] category : defaultCategories) {
                pstmt.setString(1, category[0]);
                pstmt.setString(2, category[1]);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error inserting default artifact categories: " + e.getMessage());
        }
    }

    public Set<String> loadCampaignIds() {
        Set<String> campaignIds = new HashSet<>();
        String sql = "SELECT uuid FROM campains WHERE is_active = 1 AND deleted_at IS NULL";

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
        String sql = """
            SELECT uuid, name, neo4j_label, quadrant_collection_name, user_id, description, 
                   created_at, updated_at, is_active, deleted_at, settings 
            FROM campains WHERE is_active = 1 AND deleted_at IS NULL
            """;
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
                
                // Set additional fields
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

    public Campain getCampaignById(String uuid){
        return getCampaignById(uuid, false);
    }
    
    /**
     * Gets a campaign by its UUID.
     * @param uuid The UUID of the campaign to retrieve
     * @param includeDeleted Whether to include deleted campaigns
     * @return Campain object or null if not found
     */
    public Campain getCampaignById(String uuid, boolean includeDeleted) {
            String sql = """
                SELECT uuid, name, neo4j_label, quadrant_collection_name, user_id, description, 
                    created_at, updated_at, is_active, deleted_at, settings 
                FROM campains WHERE uuid = ?
                """ + 
                (includeDeleted ? "" : " AND is_active = 1 AND deleted_at IS NULL");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
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
                    
                    // Set additional fields
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

    /**
     * Performs soft delete of a campaign by setting is_active = 0 and deleted_at timestamp.
     * @param uuid The UUID of the campaign to soft delete
     * @return true if the campaign was successfully soft deleted, false otherwise
     * @throws SQLException if there is an error executing the SQL query
     */
    public boolean deleteCampaignFromRelativeDB(String uuid) throws SQLException {
        String sql = """
            UPDATE campains 
            SET is_active = 0, deleted_at = ?, updated_at = ? 
            WHERE uuid = ? AND is_active = 1
            """;
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
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
     * Performs hard delete of a campaign from the database.
     * This method should only be used for final cleanup after retention period.
     * @param uuid The UUID of the campaign to hard delete
     * @return true if the campaign was successfully deleted, false otherwise
     * @throws SQLException if there is an error executing the SQL query
     */
    public boolean hardDeleteCampaignFromRelativeDB(String uuid) throws SQLException {
        String sql = "DELETE FROM campains WHERE uuid = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }
    
    /**
     * Gets all soft-deleted campaigns.
     * @return List of campaigns with is_active = 0 or deleted_at IS NOT NULL
     */
    public List<Campain> getDeletedCampaigns() {
        List<Campain> campaigns = new ArrayList<>();
        String sql = """
            SELECT uuid, name, neo4j_label, quadrant_collection_name, user_id, description, 
                   created_at, updated_at, is_active, deleted_at, settings 
            FROM campains WHERE is_active = 0 OR deleted_at IS NOT NULL
            ORDER BY deleted_at DESC
            """;
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
            System.err.println("Error retrieving deleted campaigns from database: " + e.getMessage());
        }
        return campaigns;
    }
    
    /**
     * Restores a soft-deleted campaign by setting is_active = 1 and deleted_at = NULL.
     * @param uuid The UUID of the campaign to restore
     * @return true if the campaign was successfully restored, false otherwise
     * @throws SQLException if there is an error executing the SQL query
     */
    public boolean restoreCampaign(String uuid) throws SQLException {
        String sql = """
            UPDATE campains 
            SET is_active = 1, deleted_at = NULL, updated_at = ? 
            WHERE uuid = ?
            """;
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            long currentTime = System.currentTimeMillis() / 1000L;
            pstmt.setLong(1, currentTime);
            pstmt.setString(2, uuid);
            
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
    
    /**
     * Finds campaigns that have been soft deleted and are past the retention period.
     * @param retentionDays Number of days to retain soft-deleted campaigns
     * @return List of campaigns that should be permanently deleted
     */
    public List<Campain> findExpiredCampaigns(int retentionDays) {
        List<Campain> expiredCampaigns = new ArrayList<>();
        long currentTime = System.currentTimeMillis() / 1000L;
        long retentionSeconds = retentionDays * 86400L; // Convert days to seconds
        long cutoffTime = currentTime - retentionSeconds;
        
        String sql = """
            SELECT uuid, name, neo4j_label, quadrant_collection_name, user_id, description, 
                   created_at, updated_at, is_active, deleted_at, settings 
            FROM campains 
            WHERE deleted_at IS NOT NULL AND deleted_at < ?
            """;
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, cutoffTime);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Campain campaign = new Campain(
                        rs.getString("uuid"),
                        rs.getString("name"),
                        rs.getString("neo4j_label"),
                        rs.getString("quadrant_collection_name")
                    );
                    
                    // Set additional fields
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
                    expiredCampaigns.add(campaign);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error finding expired campaigns: " + e.getMessage());
        }
        
        return expiredCampaigns;
    }
    
    /**
     * Retrieves note metadata for a specific campaign with pagination support.
     * Returns basic note information from campaign_notes table.
     * 
     * @param campaignUuid UUID of the campaign
     * @param limit Maximum number of notes to return
     * @param offset Offset for pagination
     * @return List of note metadata (UUID, title, created_at, updated_at, word_count)
     */
    public List<NoteMetadata> getAllNotesForCampaign(String campaignUuid, int limit, int offset) {
        List<NoteMetadata> notes = new ArrayList<>();
        String sql = """
            SELECT note_uuid, title, created_at, updated_at, word_count 
            FROM campaign_notes 
            WHERE campaign_uuid = ? AND is_active = 1 AND deleted_at IS NULL
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, campaignUuid);
            pstmt.setInt(2, limit);
            pstmt.setInt(3, offset);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    NoteMetadata metadata = new NoteMetadata(
                        rs.getString("note_uuid"),
                        rs.getString("title"),
                        rs.getLong("created_at"),
                        rs.getLong("updated_at"),
                        rs.getInt("word_count")
                    );
                    notes.add(metadata);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving notes for campaign: " + e.getMessage());
        }
        
        return notes;
    }
    
    /**
     * Counts total number of notes for a specific campaign.
     * 
     * @param campaignUuid UUID of the campaign
     * @return Total count of active notes in the campaign
     */
    public int countNotesForCampaign(String campaignUuid) {
        String sql = """
            SELECT COUNT(*) as note_count 
            FROM campaign_notes 
            WHERE campaign_uuid = ? AND is_active = 1 AND deleted_at IS NULL
            """;
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, campaignUuid);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("note_count");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error counting notes for campaign: " + e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * Inserts a new campaign note into the campaign_notes table.
     * Initializes with pending sync status for both Qdrant and Neo4j.
     * 
     * @param campaignUuid UUID of the campaign
     * @param noteUuid UUID of the note
     * @param title Title of the note
     * @param isOverride Whether this note overrides another note
     * @param wordCount Number of words in the note content
     * @return true if the note was successfully inserted, false otherwise
     */
    public boolean insertCampaignNote(String campaignUuid, String noteUuid, String title, 
                                      boolean isOverride, int wordCount) {
        String sql = """
            INSERT INTO campaign_notes (
                campaign_uuid, note_uuid, title, created_at, updated_at,
                qdrant_sync_status, neo4j_sync_status, is_override, word_count, is_active
            ) VALUES (?, ?, ?, ?, ?, 'pending', 'pending', ?, ?, 1)
            """;
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            long currentTime = System.currentTimeMillis() / 1000L;
            
            pstmt.setString(1, campaignUuid);
            pstmt.setString(2, noteUuid);
            pstmt.setString(3, title);
            pstmt.setLong(4, currentTime);
            pstmt.setLong(5, currentTime);
            pstmt.setBoolean(6, isOverride);
            pstmt.setInt(7, wordCount);
            
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
            
        } catch (SQLException e) {
            System.err.println("Error inserting campaign note: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Updates the Qdrant sync status for a campaign note.
     * Records the sync attempt timestamp and optionally stores error information.
     * 
     * @param noteUuid UUID of the note to update
     * @param status New sync status ('pending', 'syncing', 'synced', 'error', 'retry')
     * @param errorMessage Optional error message if status is 'error'
     * @return true if the update was successful, false otherwise
     */
    public boolean updateQdrantSyncStatus(String noteUuid, String status, String errorMessage) {
        String sql = """
            UPDATE campaign_notes
            SET qdrant_sync_status = ?, qdrant_last_sync_at = ?, qdrant_sync_error = ?
            WHERE note_uuid = ?
            """;
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            long currentTime = System.currentTimeMillis() / 1000L;
            
            pstmt.setString(1, status);
            pstmt.setLong(2, currentTime);
            pstmt.setString(3, errorMessage);
            pstmt.setString(4, noteUuid);
            
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
            
        } catch (SQLException e) {
            System.err.println("Error updating Qdrant sync status for note " + noteUuid + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Inner class to represent note metadata from database.
     */
    public static class NoteMetadata {
        private final String noteUuid;
        private final String title;
        private final long createdAt;
        private final long updatedAt;
        private final int wordCount;
        
        public NoteMetadata(String noteUuid, String title, long createdAt, long updatedAt, int wordCount) {
            this.noteUuid = noteUuid;
            this.title = title;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.wordCount = wordCount;
        }
        
        public String getNoteUuid() {
            return noteUuid;
        }
        
        public String getTitle() {
            return title;
        }
        
        public long getCreatedAt() {
            return createdAt;
        }
        
        public long getUpdatedAt() {
            return updatedAt;
        }
        
        public int getWordCount() {
            return wordCount;
        }
    }
}


