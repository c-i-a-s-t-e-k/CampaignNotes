package CampaignNotes;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.qdrant.client.QdrantClient;
import model.Campain;

@DisplayName("Database Loader Tests")
class DataBaseLoaderTest {
    private DataBaseLoader dataBaseLoader;
    private static final String TEST_DB_PATH = "sqlite.db";

    @BeforeEach
    void setUp() {
        dataBaseLoader = new DataBaseLoader();
    }

    @AfterEach
    void tearDown() {
        dataBaseLoader.closeConnections();
    }

    @Test
    @DisplayName("Should successfully connect to all databases")
    void testDatabaseConnectivity() {
        assertAll("Database connectivity checks",
            // Test individual connections
            () -> assertNotNull(dataBaseLoader.getNeo4jDriver(), 
                "Neo4j driver should be initialized"),
            () -> assertNotNull(dataBaseLoader.getQdrantClient(), 
                "Qdrant client should be initialized"),
            
            // Test if both databases are available
            () -> assertTrue(dataBaseLoader.checkDatabasesAvailability(), 
                "Both Neo4j and Qdrant databases should be available")
        );
    }

    @Nested
    @DisplayName("Campaign Management")
    class CampaignManagement {

        @Test
        @DisplayName("Should successfully create and save campaign")
        void testCampaignCreation() throws SQLException {
            // Create a test campaign
            String testUuid = UUID.randomUUID().toString();
            String testName = "Test Campaign";
            String testNeo4jLabel = "TestLabel";
            String testQdrantCollection = "test_collection_" + testUuid.substring(0, 8);

            Campain testCampaign = new Campain(testUuid, testName, testNeo4jLabel, testQdrantCollection);

            // Save the campaign
            assertTrue(dataBaseLoader.saveCampaignToRelativeDB(testCampaign), 
                "Campaign should be saved successfully");

            // Verify campaign exists in SQLite
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + TEST_DB_PATH);
                 PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM campains WHERE uuid = ?")) {

                pstmt.setString(1, testUuid);
                ResultSet rs = pstmt.executeQuery();

                assertAll("Campaign verification in SQLite",
                    () -> assertTrue(rs.next(), "Campaign should exist in SQLite database"),
                    () -> assertEquals(testName, rs.getString("name"), 
                        "Campaign name should match in SQLite")
                );
            }

            // Clean up - delete the test campaign
            dataBaseLoader.deleteCampaignFromRelativeDB(testUuid);
        }

        @Test
        @DisplayName("Should successfully delete campaign and cleanup resources")
        void testCampaignDeletion() throws SQLException {
            // Create a test campaign first
            String testUuid = UUID.randomUUID().toString();
            String testName = "Test Campaign for Deletion";
            String testNeo4jLabel = "TestLabelDelete";
            String testQdrantCollection = "test_collection_delete_" + testUuid.substring(0, 8);

            Campain testCampaign = new Campain(testUuid, testName, testNeo4jLabel, testQdrantCollection);
            dataBaseLoader.saveCampaignToRelativeDB(testCampaign);

            // Delete the campaign
            assertTrue(dataBaseLoader.deleteCampaignFromRelativeDB(testUuid), 
                "Campaign should be deleted successfully");

            // Verify campaign is soft deleted in SQLite
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + TEST_DB_PATH);
                 PreparedStatement pstmt = conn.prepareStatement("SELECT is_active, deleted_at FROM campains WHERE uuid = ?")) {

                pstmt.setString(1, testUuid);
                ResultSet rs = pstmt.executeQuery();

                assertTrue(rs.next(), "Campaign should still exist in database after soft deletion");
                assertFalse(rs.getBoolean("is_active"), "Campaign should be marked as inactive");
                assertTrue(rs.getLong("deleted_at") > 0, "Campaign should have deleted_at timestamp");
            }

            // Verify Qdrant collection is removed
            QdrantClient qdrantClient = dataBaseLoader.getQdrantClient();
            assertNotNull(qdrantClient, "Qdrant client should be available");

            assertDoesNotThrow(() -> {
                var collections = qdrantClient.listCollectionsAsync()
                    .get(5, TimeUnit.SECONDS);

                boolean collectionExists = collections.stream()
                    .anyMatch(name -> name.equals(testQdrantCollection));

                assertFalse(collectionExists, 
                    "Qdrant collection should not exist after campaign deletion");
            }, "Failed to verify Qdrant collection deletion");
        }
    }
} 