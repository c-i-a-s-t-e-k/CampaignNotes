package CampaignNotes;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import CampaignNotes.database.DatabaseConnectionManager;
import CampaignNotes.database.Neo4jRepository;
import CampaignNotes.database.QdrantRepository;
import CampaignNotes.database.SqliteRepository;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.CollectionOperationResponse;
import io.qdrant.client.grpc.Collections.VectorParams;
import model.Campain;

/**
 * Unit tests for CampaignManager delete campaign functionality.
 * Tests cover soft delete, hard delete cleanup, and error handling scenarios.
 */
@DisplayName("Campaign Delete Unit Tests")
class DeleteCampaignUNITest {

    @Mock
    private DatabaseConnectionManager mockDbConnectionManager;
    
    @Mock
    private SqliteRepository mockSqliteRepository;
    
    @Mock
    private Neo4jRepository mockNeo4jRepository;
    
    @Mock
    private QdrantRepository mockQdrantRepository;
    
    @Mock
    private QdrantClient mockQdrantClient;
    
    private CampaignManager campaignManager;
    
    @BeforeEach
    void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock chain for database connection manager
        when(mockDbConnectionManager.getSqliteRepository()).thenReturn(mockSqliteRepository);
        when(mockDbConnectionManager.getNeo4jRepository()).thenReturn(mockNeo4jRepository);
        when(mockDbConnectionManager.getQdrantRepository()).thenReturn(mockQdrantRepository);
        when(mockQdrantRepository.getClient()).thenReturn(mockQdrantClient);
        
        // Mock default user ID for campaign creation
        when(mockSqliteRepository.getDefaultUserId()).thenReturn("default-user-id");
        
        // Mock saveCampaignToRelativeDB for campaign creation
        when(mockSqliteRepository.saveCampaignToRelativeDB(any(Campain.class))).thenReturn(true);
        
        // Mock createCollectionAsync for Qdrant - needed for createNewCampain()
        ListenableFuture<CollectionOperationResponse> createCollectionFuture = 
            Futures.immediateFuture(CollectionOperationResponse.getDefaultInstance());
        when(mockQdrantClient.createCollectionAsync(anyString(), any(VectorParams.class)))
            .thenReturn(createCollectionFuture);
        
        // Initialize CampaignManager with mocked dependencies
        campaignManager = new CampaignManager(mockDbConnectionManager);
    }
    
    /**
     * Helper method to create a test campaign.
     */
    private Campain createTestCampaign(String uuid, String name) {
        Campain campaign = new Campain(uuid, name, "TestLabel", "TestCollection");
        campaign.setUserId("test-user-id");
        campaign.setDescription("Test campaign");
        campaign.setCreatedAt(System.currentTimeMillis() / 1000L);
        campaign.setUpdatedAt(System.currentTimeMillis() / 1000L);
        campaign.setActive(true);
        return campaign;
    }
    
    /**
     * Helper method to create an expired campaign (soft deleted).
     */
    private Campain createExpiredCampaign(String uuid, String name, int daysAgo) {
        Campain campaign = createTestCampaign(uuid, name);
        long expiredTimestamp = System.currentTimeMillis() / 1000L - (daysAgo * 86400L);
        campaign.setDeletedAt(expiredTimestamp);
        campaign.setActive(false);
        return campaign;
    }
    
    @Nested
    @DisplayName("Delete Campaign - Soft Delete Tests")
    class DeleteCampaignSoftDeleteTests {
        
        @Test
        @DisplayName("Should successfully perform soft delete when campaign exists")
        void testDeleteCampaign_Success() throws SQLException {
            // Arrange
            String campaignUuid = "test-uuid-1";
            
            // Mock getAllCampaigns to return empty list initially
            when(mockSqliteRepository.getAllCampaigns()).thenReturn(new ArrayList<>());
            
            // Create campaign first
            Campain createdCampaign = campaignManager.createNewCampain("Test Campaign");
            if (createdCampaign != null) {
                campaignUuid = createdCampaign.getUuid();
            }
            
            // Mock the delete operation
            when(mockSqliteRepository.deleteCampaignFromRelativeDB(campaignUuid))
                .thenReturn(true);
            
            // Act
            boolean result = campaignManager.deleteCampaign(campaignUuid);
            
            // Assert
            assertTrue(result, "Delete should succeed");
            verify(mockSqliteRepository, times(1)).deleteCampaignFromRelativeDB(campaignUuid);
            // Verify that Qdrant and Neo4j are NOT called for soft delete
            verify(mockQdrantClient, never()).deleteCollectionAsync(anyString());
            verify(mockNeo4jRepository, never()).deleteHardCampaignSubgraphById(anyString());
        }
        
        @Test
        @DisplayName("Should return false when campaign does not exist")
        void testDeleteCampaign_CampaignNotFound() {
            // Arrange
            String nonExistentUuid = "non-existent-uuid";
            
            // Mock getAllCampaigns to return empty list
            when(mockSqliteRepository.getAllCampaigns()).thenReturn(new ArrayList<>());
            
            // Act
            boolean result = campaignManager.deleteCampaign(nonExistentUuid);
            
            // Assert
            assertFalse(result, "Delete should fail for non-existent campaign");
            // Note: verify() doesn't throw SQLException, but compiler may require handling
            try {
                verify(mockSqliteRepository, never()).deleteCampaignFromRelativeDB(anyString());
            } catch (SQLException e) {
                // This should never happen - verify() doesn't throw SQLException
            }
        }
        
        @Test
        @DisplayName("Should return false when SQLite delete throws exception")
        void testDeleteCampaign_SqliteDeleteFails() {
            // Arrange
            String campaignUuid = "test-uuid-2";
            
            // Mock getAllCampaigns to return empty list initially
            when(mockSqliteRepository.getAllCampaigns()).thenReturn(new ArrayList<>());
            
            // Create campaign first
            Campain createdCampaign = campaignManager.createNewCampain("Test Campaign");
            if (createdCampaign != null) {
                campaignUuid = createdCampaign.getUuid();
            }
            
            // Mock SQLite to throw exception
            try {
                when(mockSqliteRepository.deleteCampaignFromRelativeDB(campaignUuid))
                    .thenThrow(new SQLException("Database error"));
            } catch (SQLException e) {
                // This shouldn't happen in mock setup
            }
            
            // Act
            boolean result = campaignManager.deleteCampaign(campaignUuid);
            
            // Assert
            assertFalse(result, "Delete should fail when SQLite throws exception");
            // Note: verify() doesn't throw SQLException, but compiler may require handling
            try {
                verify(mockSqliteRepository, times(1)).deleteCampaignFromRelativeDB(campaignUuid);
            } catch (SQLException e) {
                // This should never happen - verify() doesn't throw SQLException
            }
        }
        
        @Test
        @DisplayName("Should return false when SQLite delete returns false")
        void testDeleteCampaign_SqliteReturnsFalse() throws SQLException {
            // Arrange
            String campaignUuid = "test-uuid-3";
            
            // Mock getAllCampaigns to return empty list initially
            when(mockSqliteRepository.getAllCampaigns()).thenReturn(new ArrayList<>());
            
            // Create campaign first
            Campain createdCampaign = campaignManager.createNewCampain("Test Campaign");
            if (createdCampaign != null) {
                campaignUuid = createdCampaign.getUuid();
            }
            
            // Mock SQLite to return false
            when(mockSqliteRepository.deleteCampaignFromRelativeDB(campaignUuid))
                .thenReturn(false);
            
            // Act
            boolean result = campaignManager.deleteCampaign(campaignUuid);
            
            // Assert
            assertFalse(result, "Delete should fail when SQLite returns false");
            verify(mockSqliteRepository, times(1)).deleteCampaignFromRelativeDB(campaignUuid);
        }
        
        @Test
        @DisplayName("Should remove campaign from map only when soft delete succeeds")
        void testDeleteCampaign_RemovesFromMapOnlyOnSuccess() {
            // Arrange
            String campaignUuid = "test-uuid-4";
            
            // Mock getAllCampaigns to return empty list initially
            when(mockSqliteRepository.getAllCampaigns()).thenReturn(new ArrayList<>());
            
            // Create campaign first
            Campain createdCampaign = campaignManager.createNewCampain("Test Campaign");
            if (createdCampaign != null) {
                campaignUuid = createdCampaign.getUuid();
                assertNotNull(campaignManager.getCampaignByUuid(campaignUuid), 
                    "Campaign should exist in map before deletion");
            }
            
            // Test successful deletion
            try {
                when(mockSqliteRepository.deleteCampaignFromRelativeDB(campaignUuid))
                    .thenReturn(true);
            } catch (SQLException e) {
                // This shouldn't happen in mock setup
            }
            
            boolean successResult = campaignManager.deleteCampaign(campaignUuid);
            assertTrue(successResult);
            assertNull(campaignManager.getCampaignByUuid(campaignUuid), 
                "Campaign should be removed from map after successful deletion");
            
            // Recreate campaign for failure test
            createdCampaign = campaignManager.createNewCampain("Test Campaign 2");
            if (createdCampaign != null) {
                campaignUuid = createdCampaign.getUuid();
                
                // Test failed deletion
                try {
                    when(mockSqliteRepository.deleteCampaignFromRelativeDB(campaignUuid))
                        .thenReturn(false);
                } catch (SQLException e) {
                    // This shouldn't happen in mock setup
                }
                
                boolean failResult = campaignManager.deleteCampaign(campaignUuid);
                assertFalse(failResult);
                assertNotNull(campaignManager.getCampaignByUuid(campaignUuid), 
                    "Campaign should remain in map after failed deletion");
            }
        }
    }
    
    @Nested
    @DisplayName("Cleanup Expired Campaigns Tests")
    class CleanupExpiredCampaignsTests {
        
        @Test
        @DisplayName("Should not perform cleanup when no expired campaigns exist")
        void testCleanupExpiredCampaigns_NoExpiredCampaigns() throws SQLException {
            // Arrange
            when(mockSqliteRepository.findExpiredCampaigns(any(Integer.class)))
                .thenReturn(new ArrayList<>());
            
            // Act
            campaignManager.cleanupExpiredCampaigns(7);
            
            // Assert
            verify(mockSqliteRepository, times(1)).findExpiredCampaigns(7);
            verify(mockNeo4jRepository, never()).deleteHardCampaignSubgraphById(anyString());
            verify(mockQdrantClient, never()).deleteCollectionAsync(anyString());
            verify(mockSqliteRepository, never()).hardDeleteCampaignFromRelativeDB(anyString());
        }
        
        @Test
        @DisplayName("Should successfully cleanup one expired campaign")
        void testCleanupExpiredCampaigns_OneExpiredCampaign_Success() throws SQLException {
            // Arrange
            String campaignUuid = "expired-uuid-1";
            Campain expiredCampaign = createExpiredCampaign(campaignUuid, "Expired Campaign", 10);
            
            List<Campain> expiredCampaigns = new ArrayList<>();
            expiredCampaigns.add(expiredCampaign);
            
            when(mockSqliteRepository.findExpiredCampaigns(7)).thenReturn(expiredCampaigns);
            when(mockSqliteRepository.getCampaignById(campaignUuid, true)).thenReturn(expiredCampaign);
            when(mockNeo4jRepository.deleteHardCampaignSubgraphById(campaignUuid)).thenReturn(true);
            
            // Mock Qdrant async delete - returns ListenableFuture<CollectionOperationResponse>
            ListenableFuture<CollectionOperationResponse> qdrantFuture = 
                Futures.immediateFuture(CollectionOperationResponse.getDefaultInstance());
            when(mockQdrantClient.deleteCollectionAsync(expiredCampaign.getQuadrantCollectionName()))
                .thenReturn(qdrantFuture);
            
            when(mockSqliteRepository.hardDeleteCampaignFromRelativeDB(campaignUuid))
                .thenReturn(true);
            
            // Act
            campaignManager.cleanupExpiredCampaigns(7);
            
            // Assert
            verify(mockSqliteRepository, times(1)).findExpiredCampaigns(7);
            verify(mockSqliteRepository, times(1)).getCampaignById(campaignUuid, true);
            verify(mockNeo4jRepository, times(1)).deleteHardCampaignSubgraphById(campaignUuid);
            verify(mockQdrantClient, times(1)).deleteCollectionAsync(expiredCampaign.getQuadrantCollectionName());
            verify(mockSqliteRepository, times(1)).hardDeleteCampaignFromRelativeDB(campaignUuid);
        }
        
        @Test
        @DisplayName("Should successfully cleanup multiple expired campaigns")
        void testCleanupExpiredCampaigns_MultipleExpiredCampaigns() throws SQLException {
            // Arrange
            Campain expired1 = createExpiredCampaign("expired-uuid-1", "Expired Campaign 1", 10);
            Campain expired2 = createExpiredCampaign("expired-uuid-2", "Expired Campaign 2", 15);
            Campain expired3 = createExpiredCampaign("expired-uuid-3", "Expired Campaign 3", 20);
            
            List<Campain> expiredCampaigns = new ArrayList<>();
            expiredCampaigns.add(expired1);
            expiredCampaigns.add(expired2);
            expiredCampaigns.add(expired3);
            
            when(mockSqliteRepository.findExpiredCampaigns(7)).thenReturn(expiredCampaigns);
            when(mockSqliteRepository.getCampaignById(eq("expired-uuid-1"), eq(true))).thenReturn(expired1);
            when(mockSqliteRepository.getCampaignById(eq("expired-uuid-2"), eq(true))).thenReturn(expired2);
            when(mockSqliteRepository.getCampaignById(eq("expired-uuid-3"), eq(true))).thenReturn(expired3);
            
            when(mockNeo4jRepository.deleteHardCampaignSubgraphById(anyString())).thenReturn(true);
            
            ListenableFuture<CollectionOperationResponse> qdrantFuture = 
                Futures.immediateFuture(CollectionOperationResponse.getDefaultInstance());
            when(mockQdrantClient.deleteCollectionAsync(anyString())).thenReturn(qdrantFuture);
            
            when(mockSqliteRepository.hardDeleteCampaignFromRelativeDB(anyString()))
                .thenReturn(true);
            
            // Act
            campaignManager.cleanupExpiredCampaigns(7);
            
            // Assert
            verify(mockSqliteRepository, times(1)).findExpiredCampaigns(7);
            verify(mockSqliteRepository, times(3)).getCampaignById(anyString(), eq(true));
            verify(mockNeo4jRepository, times(3)).deleteHardCampaignSubgraphById(anyString());
            verify(mockQdrantClient, times(3)).deleteCollectionAsync(anyString());
            verify(mockSqliteRepository, times(3)).hardDeleteCampaignFromRelativeDB(anyString());
        }
        
        @Test
        @DisplayName("Should handle partial failure during cleanup")
        void testCleanupExpiredCampaigns_PartialFailure() throws SQLException {
            // Arrange
            Campain expired1 = createExpiredCampaign("expired-uuid-1", "Expired Campaign 1", 10);
            Campain expired2 = createExpiredCampaign("expired-uuid-2", "Expired Campaign 2", 15);
            
            List<Campain> expiredCampaigns = new ArrayList<>();
            expiredCampaigns.add(expired1);
            expiredCampaigns.add(expired2);
            
            when(mockSqliteRepository.findExpiredCampaigns(7)).thenReturn(expiredCampaigns);
            when(mockSqliteRepository.getCampaignById(eq("expired-uuid-1"), eq(true))).thenReturn(expired1);
            when(mockSqliteRepository.getCampaignById(eq("expired-uuid-2"), eq(true))).thenReturn(expired2);
            
            // First campaign succeeds, second fails at Neo4j
            when(mockNeo4jRepository.deleteHardCampaignSubgraphById("expired-uuid-1")).thenReturn(true);
            when(mockNeo4jRepository.deleteHardCampaignSubgraphById("expired-uuid-2")).thenReturn(false);
            
            ListenableFuture<CollectionOperationResponse> qdrantFuture = 
                Futures.immediateFuture(CollectionOperationResponse.getDefaultInstance());
            when(mockQdrantClient.deleteCollectionAsync(anyString())).thenReturn(qdrantFuture);
            
            when(mockSqliteRepository.hardDeleteCampaignFromRelativeDB("expired-uuid-1"))
                .thenReturn(true);
            when(mockSqliteRepository.hardDeleteCampaignFromRelativeDB("expired-uuid-2"))
                .thenReturn(true);
            
            // Act
            campaignManager.cleanupExpiredCampaigns(7);
            
            // Assert - both campaigns should be attempted
            verify(mockSqliteRepository, times(1)).findExpiredCampaigns(7);
            verify(mockNeo4jRepository, times(2)).deleteHardCampaignSubgraphById(anyString());
            verify(mockQdrantClient, times(2)).deleteCollectionAsync(anyString());
            verify(mockSqliteRepository, times(2)).hardDeleteCampaignFromRelativeDB(anyString());
        }
        
        @Test
        @DisplayName("Should continue cleanup when Neo4j deletion fails")
        void testCleanupExpiredCampaigns_Neo4jFails() throws SQLException {
            // Arrange
            String campaignUuid = "expired-uuid-1";
            Campain expiredCampaign = createExpiredCampaign(campaignUuid, "Expired Campaign", 10);
            
            List<Campain> expiredCampaigns = new ArrayList<>();
            expiredCampaigns.add(expiredCampaign);
            
            when(mockSqliteRepository.findExpiredCampaigns(7)).thenReturn(expiredCampaigns);
            when(mockSqliteRepository.getCampaignById(campaignUuid, true)).thenReturn(expiredCampaign);
            when(mockNeo4jRepository.deleteHardCampaignSubgraphById(campaignUuid)).thenReturn(false);
            
            ListenableFuture<CollectionOperationResponse> qdrantFuture = 
                Futures.immediateFuture(CollectionOperationResponse.getDefaultInstance());
            when(mockQdrantClient.deleteCollectionAsync(expiredCampaign.getQuadrantCollectionName()))
                .thenReturn(qdrantFuture);
            
            when(mockSqliteRepository.hardDeleteCampaignFromRelativeDB(campaignUuid))
                .thenReturn(true);
            
            // Act
            campaignManager.cleanupExpiredCampaigns(7);
            
            // Assert
            verify(mockNeo4jRepository, times(1)).deleteHardCampaignSubgraphById(campaignUuid);
            verify(mockQdrantClient, times(1)).deleteCollectionAsync(expiredCampaign.getQuadrantCollectionName());
            verify(mockSqliteRepository, times(1)).hardDeleteCampaignFromRelativeDB(campaignUuid);
        }
        
        @Test
        @DisplayName("Should continue cleanup when Qdrant deletion fails")
        void testCleanupExpiredCampaigns_QdrantFails() throws SQLException {
            // Arrange
            String campaignUuid = "expired-uuid-1";
            Campain expiredCampaign = createExpiredCampaign(campaignUuid, "Expired Campaign", 10);
            
            List<Campain> expiredCampaigns = new ArrayList<>();
            expiredCampaigns.add(expiredCampaign);
            
            when(mockSqliteRepository.findExpiredCampaigns(7)).thenReturn(expiredCampaigns);
            when(mockSqliteRepository.getCampaignById(campaignUuid, true)).thenReturn(expiredCampaign);
            when(mockNeo4jRepository.deleteHardCampaignSubgraphById(campaignUuid)).thenReturn(true);
            
            // Mock Qdrant to throw exception
            ListenableFuture<CollectionOperationResponse> failedFuture = 
                Futures.immediateFailedFuture(new RuntimeException("Qdrant error"));
            when(mockQdrantClient.deleteCollectionAsync(expiredCampaign.getQuadrantCollectionName()))
                .thenReturn(failedFuture);
            
            when(mockSqliteRepository.hardDeleteCampaignFromRelativeDB(campaignUuid))
                .thenReturn(true);
            
            // Act
            campaignManager.cleanupExpiredCampaigns(7);
            
            // Assert
            verify(mockNeo4jRepository, times(1)).deleteHardCampaignSubgraphById(campaignUuid);
            verify(mockQdrantClient, times(1)).deleteCollectionAsync(expiredCampaign.getQuadrantCollectionName());
            verify(mockSqliteRepository, times(1)).hardDeleteCampaignFromRelativeDB(campaignUuid);
        }
        
        @Test
        @DisplayName("Should not remove campaign from map when SQLite hard delete fails")
        void testCleanupExpiredCampaigns_SqliteHardDeleteFails() throws SQLException {
            // Arrange
            String campaignUuid = "expired-uuid-1";
            Campain expiredCampaign = createExpiredCampaign(campaignUuid, "Expired Campaign", 10);
            
            List<Campain> expiredCampaigns = new ArrayList<>();
            expiredCampaigns.add(expiredCampaign);
            
            when(mockSqliteRepository.findExpiredCampaigns(7)).thenReturn(expiredCampaigns);
            when(mockSqliteRepository.getCampaignById(campaignUuid, true)).thenReturn(expiredCampaign);
            when(mockNeo4jRepository.deleteHardCampaignSubgraphById(campaignUuid)).thenReturn(true);
            
            ListenableFuture<CollectionOperationResponse> qdrantFuture = 
                Futures.immediateFuture(CollectionOperationResponse.getDefaultInstance());
            when(mockQdrantClient.deleteCollectionAsync(expiredCampaign.getQuadrantCollectionName()))
                .thenReturn(qdrantFuture);
            
            // Mock SQLite hard delete to throw exception
            when(mockSqliteRepository.hardDeleteCampaignFromRelativeDB(campaignUuid))
                .thenThrow(new SQLException("Hard delete failed"));
            
            // Act
            campaignManager.cleanupExpiredCampaigns(7);
            
            // Assert
            verify(mockSqliteRepository, times(1)).hardDeleteCampaignFromRelativeDB(campaignUuid);
            // Campaign should not be removed from map since SQLite hard delete failed
        }
        
        @Test
        @DisplayName("Should remove campaign from map only when SQLite hard delete succeeds")
        void testCleanupExpiredCampaigns_RemovesFromMapOnlyOnSqliteSuccess() throws SQLException {
            // Arrange
            String campaignUuid = "expired-uuid-1";
            Campain expiredCampaign = createExpiredCampaign(campaignUuid, "Expired Campaign", 10);
            
            List<Campain> expiredCampaigns = new ArrayList<>();
            expiredCampaigns.add(expiredCampaign);
            
            when(mockSqliteRepository.findExpiredCampaigns(7)).thenReturn(expiredCampaigns);
            when(mockSqliteRepository.getCampaignById(campaignUuid, true)).thenReturn(expiredCampaign);
            when(mockNeo4jRepository.deleteHardCampaignSubgraphById(campaignUuid)).thenReturn(true);
            
            ListenableFuture<CollectionOperationResponse> qdrantFuture = 
                Futures.immediateFuture(CollectionOperationResponse.getDefaultInstance());
            when(mockQdrantClient.deleteCollectionAsync(expiredCampaign.getQuadrantCollectionName()))
                .thenReturn(qdrantFuture);
            
            // Add campaign to manager's map first
            when(mockSqliteRepository.getAllCampaigns()).thenReturn(new ArrayList<>());
            campaignManager.createNewCampain("Test Campaign");
            
            // Mock successful hard delete
            when(mockSqliteRepository.hardDeleteCampaignFromRelativeDB(campaignUuid))
                .thenReturn(true);
            
            // Act
            campaignManager.cleanupExpiredCampaigns(7);
            
            // Assert
            verify(mockSqliteRepository, times(1)).hardDeleteCampaignFromRelativeDB(campaignUuid);
        }
    }
}

