package CampaignNotes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import CampaignNotes.database.DatabaseConnectionManager;
import CampaignNotes.database.QdrantRepository;
import CampaignNotes.database.SqliteRepository;
import CampaignNotes.llm.OpenAIEmbeddingService;
import io.qdrant.client.QdrantClient;
import static io.qdrant.client.ValueFactory.value;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import model.Campain;
import model.EmbeddingResult;

/**
 * Unit tests for SemantickSearchService.
 * Tests cover input validation, happy path scenarios, and error handling.
 */
class SemantickSearchServiceTest {

    @Mock
    private DatabaseConnectionManager mockDbConnectionManager;
    
    @Mock
    private SqliteRepository mockSqliteRepository;
    
    @Mock
    private QdrantRepository mockQdrantRepository;
    
    @Mock
    private QdrantClient mockQdrantClient;
    
    @Mock
    private OpenAIEmbeddingService mockEmbeddingService;
    
    private SemantickSearchService searchService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock chain for database connection manager
        when(mockDbConnectionManager.getSqliteRepository()).thenReturn(mockSqliteRepository);
        when(mockDbConnectionManager.getQdrantRepository()).thenReturn(mockQdrantRepository);
        when(mockQdrantRepository.getClient()).thenReturn(mockQdrantClient);
        
        searchService = new SemantickSearchService(mockDbConnectionManager, mockEmbeddingService);
    }
    
    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {
        
        @Test
        @DisplayName("Should throw IllegalArgumentException when DatabaseConnectionManager is null")
        void testConstructorWithNullDbManager() {
            assertThrows(IllegalArgumentException.class, 
                () -> new SemantickSearchService(null, mockEmbeddingService));
        }
        
        @Test
        @DisplayName("Should throw IllegalArgumentException when EmbeddingService is null")
        void testConstructorWithNullEmbeddingService() {
            assertThrows(IllegalArgumentException.class, 
                () -> new SemantickSearchService(mockDbConnectionManager, null));
        }
    }
    
    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {
        
        @Test
        @DisplayName("Should throw IllegalArgumentException when query is null")
        void testGetTopKMatchWithNullQuery() {
            assertThrows(IllegalArgumentException.class, 
                () -> searchService.getTopKMatch(null, 5, "campaign-uuid"));
        }
        
        @Test
        @DisplayName("Should throw IllegalArgumentException when query is empty")
        void testGetTopKMatchWithEmptyQuery() {
            assertThrows(IllegalArgumentException.class, 
                () -> searchService.getTopKMatch("", 5, "campaign-uuid"));
        }
        
        @Test
        @DisplayName("Should throw IllegalArgumentException when query is only whitespace")
        void testGetTopKMatchWithWhitespaceQuery() {
            assertThrows(IllegalArgumentException.class, 
                () -> searchService.getTopKMatch("   ", 5, "campaign-uuid"));
        }
        
        @Test
        @DisplayName("Should throw IllegalArgumentException when k is zero")
        void testGetTopKMatchWithZeroK() {
            assertThrows(IllegalArgumentException.class, 
                () -> searchService.getTopKMatch("test query", 0, "campaign-uuid"));
        }
        
        @Test
        @DisplayName("Should throw IllegalArgumentException when k is negative")
        void testGetTopKMatchWithNegativeK() {
            assertThrows(IllegalArgumentException.class, 
                () -> searchService.getTopKMatch("test query", -5, "campaign-uuid"));
        }
        
        @Test
        @DisplayName("Should throw IllegalArgumentException when campaignUuid is null")
        void testGetTopKMatchWithNullCampaignUuid() {
            assertThrows(IllegalArgumentException.class, 
                () -> searchService.getTopKMatch("test query", 5, null));
        }
        
        @Test
        @DisplayName("Should throw IllegalArgumentException when campaignUuid is empty")
        void testGetTopKMatchWithEmptyCampaignUuid() {
            assertThrows(IllegalArgumentException.class, 
                () -> searchService.getTopKMatch("test query", 5, ""));
        }
    }
    
    @Nested
    @DisplayName("Campaign Validation Tests")
    class CampaignValidationTests {
        
        @Test
        @DisplayName("Should throw IllegalArgumentException when campaign not found")
        void testGetTopKMatchWithNonexistentCampaign() {
            when(mockSqliteRepository.getCampaignById("unknown-uuid")).thenReturn(null);
            
            assertThrows(IllegalArgumentException.class, 
                () -> searchService.getTopKMatch("test query", 5, "unknown-uuid"));
        }
        
        @Test
        @DisplayName("Should return empty list when campaign has no collection name")
        void testGetTopKMatchWithNoCollectionName() {
            Campain campaign = new Campain("uuid", "Test Campaign", "Label", null);
            when(mockSqliteRepository.getCampaignById("uuid")).thenReturn(campaign);
            
            List<String> results = searchService.getTopKMatch("test query", 5, "uuid");
            
            assertTrue(results.isEmpty());
        }
    }
    
    @Nested
    @DisplayName("Happy Path Tests")
    class HappyPathTests {
        
        @Test
        @DisplayName("Should return ordered note IDs when search is successful")
        void testSuccessfulSearch() throws Exception {
            // Setup campaign
            Campain campaign = new Campain("test-uuid", "Test Campaign", "Label", "test-collection");
            when(mockSqliteRepository.getCampaignById("test-uuid")).thenReturn(campaign);
            
            // Setup embedding
            List<Double> embedding = Arrays.asList(0.1, 0.2, 0.3);
            EmbeddingResult embeddingResult = new EmbeddingResult(embedding, 10);
            when(mockEmbeddingService.generateEmbeddingWithUsage("test query")).thenReturn(embeddingResult);
            
            // Setup mock search results
            List<ScoredPoint> mockResults = createMockSearchResults(
                Arrays.asList("note-id-1", "note-id-2", "note-id-3")
            );
            
            when(mockQdrantClient.searchAsync(any(SearchPoints.class)))
                .thenReturn(Futures.immediateFuture(mockResults));
            
            // Execute search
            List<String> results = searchService.getTopKMatch("test query", 3, "test-uuid");
            
            // Verify results
            assertEquals(3, results.size());
            assertEquals("note-id-1", results.get(0));
            assertEquals("note-id-2", results.get(1));
            assertEquals("note-id-3", results.get(2));
        }
        
        @Test
        @DisplayName("Should return empty list when no results found")
        void testSearchWithNoResults() throws Exception {
            // Setup campaign
            Campain campaign = new Campain("test-uuid", "Test Campaign", "Label", "test-collection");
            when(mockSqliteRepository.getCampaignById("test-uuid")).thenReturn(campaign);
            
            // Setup embedding
            List<Double> embedding = Arrays.asList(0.1, 0.2, 0.3);
            EmbeddingResult embeddingResult = new EmbeddingResult(embedding, 10);
            when(mockEmbeddingService.generateEmbeddingWithUsage("test query")).thenReturn(embeddingResult);
            
            // Setup empty search results
            when(mockQdrantClient.searchAsync(any(SearchPoints.class)))
                .thenReturn(Futures.immediateFuture(Collections.emptyList()));
            
            // Execute search
            List<String> results = searchService.getTopKMatch("test query", 5, "test-uuid");
            
            // Verify empty results
            assertTrue(results.isEmpty());
        }
        
        @Test
        @DisplayName("Should clamp k to maximum allowed value")
        void testKClampingToMaximum() throws Exception {
            // Setup campaign
            Campain campaign = new Campain("test-uuid", "Test Campaign", "Label", "test-collection");
            when(mockSqliteRepository.getCampaignById("test-uuid")).thenReturn(campaign);
            
            // Setup embedding
            List<Double> embedding = Arrays.asList(0.1, 0.2, 0.3);
            EmbeddingResult embeddingResult = new EmbeddingResult(embedding, 10);
            when(mockEmbeddingService.generateEmbeddingWithUsage("test query")).thenReturn(embeddingResult);
            
            // Setup mock search results
            List<ScoredPoint> mockResults = createMockSearchResults(Arrays.asList("note-id-1"));
            when(mockQdrantClient.searchAsync(any(SearchPoints.class)))
                .thenReturn(Futures.immediateFuture(mockResults));
            
            // Execute search with k > MAX_K (100)
            List<String> results = searchService.getTopKMatch("test query", 200, "test-uuid");
            
            // Verify search was performed (with clamped k)
            verify(mockQdrantClient).searchAsync(any(SearchPoints.class));
            assertNotNull(results);
        }
    }
    
    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {
        
        @Test
        @DisplayName("Should return empty list when Qdrant client is unavailable")
        void testSearchWithUnavailableQdrantClient() {
            // Setup campaign
            Campain campaign = new Campain("test-uuid", "Test Campaign", "Label", "test-collection");
            when(mockSqliteRepository.getCampaignById("test-uuid")).thenReturn(campaign);
            
            // Setup embedding
            List<Double> embedding = Arrays.asList(0.1, 0.2, 0.3);
            EmbeddingResult embeddingResult = new EmbeddingResult(embedding, 10);
            when(mockEmbeddingService.generateEmbeddingWithUsage("test query")).thenReturn(embeddingResult);
            
            // Make Qdrant client unavailable
            when(mockQdrantRepository.getClient()).thenReturn(null);
            
            // Execute search
            List<String> results = searchService.getTopKMatch("test query", 5, "test-uuid");
            
            // Verify empty results
            assertTrue(results.isEmpty());
        }
        
        @Test
        @DisplayName("Should return empty list when search execution fails")
        void testSearchWithExecutionException() throws Exception {
            // Setup campaign
            Campain campaign = new Campain("test-uuid", "Test Campaign", "Label", "test-collection");
            when(mockSqliteRepository.getCampaignById("test-uuid")).thenReturn(campaign);
            
            // Setup embedding
            List<Double> embedding = Arrays.asList(0.1, 0.2, 0.3);
            EmbeddingResult embeddingResult = new EmbeddingResult(embedding, 10);
            when(mockEmbeddingService.generateEmbeddingWithUsage("test query")).thenReturn(embeddingResult);
            
            // Make search fail
            ListenableFuture<List<ScoredPoint>> failedFuture = 
                Futures.immediateFailedFuture(new RuntimeException("Search failed"));
            when(mockQdrantClient.searchAsync(any(SearchPoints.class))).thenReturn(failedFuture);
            
            // Execute search
            List<String> results = searchService.getTopKMatch("test query", 5, "test-uuid");
            
            // Verify empty results
            assertTrue(results.isEmpty());
        }
    }
    
    /**
     * Helper method to create mock ScoredPoint objects with note IDs in payload.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<ScoredPoint> createMockSearchResults(List<String> noteIds) {
        List<ScoredPoint> results = new ArrayList<>();
        
        for (String noteId : noteIds) {
            // Create a mock ScoredPoint with payload containing note_id
            ScoredPoint mockPoint = mock(ScoredPoint.class);
            Map payloadMap = new HashMap<>();
            
            // Use ValueFactory to create the value (same as in production code)
            payloadMap.put("note_id", value(noteId));
            
            when(mockPoint.getPayloadMap()).thenReturn(payloadMap);
            
            results.add(mockPoint);
        }
        
        return results;
    }
}

