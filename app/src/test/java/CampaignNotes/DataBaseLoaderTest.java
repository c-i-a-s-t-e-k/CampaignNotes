package CampaignNotes;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

import io.qdrant.client.QdrantClient;
import model.Campain;

public class DataBaseLoaderTest {
    private DataBaseLoader dataBaseLoader;
    private static final String TEST_DB_PATH = "sqlite.db";

    @Before
    public void setUp() {
        dataBaseLoader = new DataBaseLoader();
    }

    @After
    public void tearDown() {
        dataBaseLoader.closeConnections();
    }

    @Test
    public void testDatabaseConnectivity() {
        // Test individual connections
        assertNotNull("Neo4j driver should be initialized", dataBaseLoader.getNeo4jDriver()
        );
        assertNotNull("Qdrant client should be initialized", dataBaseLoader.getQdrantClient()
        );

        // Test if both databases are available
        assertTrue("Both Neo4j and Qdrant databases should be available", dataBaseLoader.checkDatabasesAvailability()
            );
    }

    @Test
    public void testCampaignCreation() throws SQLException {
        // Create a test campaign
        String testUuid = UUID.randomUUID().toString();
        String testName = "Test Campaign";
        String testNeo4jLabel = "TestLabel";
        String testQdrantCollection = "test_collection_" + testUuid.substring(0, 8);

        Campain testCampaign = new Campain(testUuid, testName, testNeo4jLabel, testQdrantCollection);

        // Save the campaign
        assertTrue("Campaign should be saved successfully", dataBaseLoader.saveCampaign(testCampaign)
            );

        // Verify campaign exists in SQLite
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + TEST_DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM campains WHERE uuid = ?")) {

            pstmt.setString(1, testUuid);
            ResultSet rs = pstmt.executeQuery();

            assertTrue( "Campaign should exist in SQLite database", rs.next());
            assertEquals(
                    "Campaign name should match in SQLite", testName, rs.getString("name"));
        }

        // Clean up - delete the test campaign
        dataBaseLoader.deleteCampaign(testUuid);
    }

    @Test
    public void testCampaignDeletion() throws SQLException {
        // Create a test campaign first
        String testUuid = UUID.randomUUID().toString();
        String testName = "Test Campaign for Deletion";
        String testNeo4jLabel = "TestLabelDelete";
        String testQdrantCollection = "test_collection_delete_" + testUuid.substring(0, 8);

        Campain testCampaign = new Campain(testUuid, testName, testNeo4jLabel, testQdrantCollection);
        dataBaseLoader.saveCampaign(testCampaign);

        // Delete the campaign
        assertTrue("Campaign should be deleted successfully",
                dataBaseLoader.deleteCampaign(testUuid)
            );

        // Verify campaign doesn't exist in SQLite
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + TEST_DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM campains WHERE uuid = ?")) {

            pstmt.setString(1, testUuid);
            ResultSet rs = pstmt.executeQuery();

            assertFalse("Campaign should not exist in SQLite database after deletion",
                    rs.next());
        }

        // Verify Qdrant collection is removed
        QdrantClient qdrantClient = dataBaseLoader.getQdrantClient();
        assertNotNull("Qdrant client should be available", qdrantClient);

        try {
            var collections = qdrantClient.listCollectionsAsync()
                .get(5, TimeUnit.SECONDS);

            boolean collectionExists = collections.stream()
                .anyMatch(name -> name.equals(testQdrantCollection));

            assertFalse("Qdrant collection should not exist after campaign deletion",
                collectionExists);
        } catch (Exception e) {
            fail("Failed to verify Qdrant collection deletion: " + e.getMessage());
        }
    }
} 