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
        String insertSql = "INSERT INTO campains (uuid, name, neo4j_label, quadrant_collection_name) VALUES (?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath)) {
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, campaign.getUuid());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        throw new SQLException("Campaign with UUID " + campaign.getUuid() + " already exists");
                    }
                }
            }
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

    public boolean deleteCampaignFromRelativeDB(String uuid) throws SQLException {
        String sql = "DELETE FROM campains WHERE uuid = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }
}


