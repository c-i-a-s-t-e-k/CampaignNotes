package CampaignNotes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import CampaignNotes.database.DatabaseConnectionManager;
import CampaignNotes.llm.OpenAIEmbeddingService;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import model.Campain;
import model.EmbeddingResult;

/**
 * Service for performing semantic search on campaign notes using Qdrant vector database.
 * Provides methods to search for notes by semantic similarity to a query string.
 */
public class SemantickSearchService {
    
    private static final int MAX_K = 100; // Maximum number of results to prevent resource exhaustion
    
    private final DatabaseConnectionManager dbConnectionManager;
    private final OpenAIEmbeddingService embeddingService;
    
    /**
     * Constructor with dependency injection for better testability and adherence to SOLID principles.
     * 
     * @param dbConnectionManager manager for database connections
     * @param embeddingService service for generating text embeddings
     */
    public SemantickSearchService(DatabaseConnectionManager dbConnectionManager, 
                                   OpenAIEmbeddingService embeddingService) {
        if (dbConnectionManager == null) {
            throw new IllegalArgumentException("DatabaseConnectionManager cannot be null");
        }
        if (embeddingService == null) {
            throw new IllegalArgumentException("OpenAIEmbeddingService cannot be null");
        }
        this.dbConnectionManager = dbConnectionManager;
        this.embeddingService = embeddingService;
    }
    
    /**
     * Searches for the top K notes most semantically similar to the query within a specific campaign.
     * Results are ordered by similarity score (descending), with index 0 being the closest match.
     * 
     * @param query the search query text
     * @param k the number of results to return (will be clamped to MAX_K)
     * @param campaignUuid the UUID of the campaign to search within
     * @return list of note IDs ordered by similarity (closest first), empty list if no results or error
     * @throws IllegalArgumentException if query is null/empty, k <= 0, or campaignUuid is null/empty
     */
    public List<String> getTopKMatch(String query, int k, String campaignUuid) {
        // Input validation
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Query cannot be null or empty");
        }
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive, got: " + k);
        }
        if (campaignUuid == null || campaignUuid.trim().isEmpty()) {
            throw new IllegalArgumentException("Campaign UUID cannot be null or empty");
        }
        
        // Clamp k to maximum allowed
        int effectiveK = Math.min(k, MAX_K);
        if (k > MAX_K) {
            System.out.println("Warning: Requested k=" + k + " exceeds maximum, clamped to " + MAX_K);
        }
        
        try {
            // Fetch campaign to get Qdrant collection name
            Campain campaign = dbConnectionManager.getSqliteRepository().getCampaignById(campaignUuid);
            if (campaign == null) {
                throw new IllegalArgumentException("Campaign not found with UUID: " + campaignUuid);
            }
            
            String collectionName = campaign.getQuadrantCollectionName();
            if (collectionName == null || collectionName.isEmpty()) {
                System.err.println("Campaign has no Qdrant collection name");
                return Collections.emptyList();
            }
            
            // Generate embedding for the query
            EmbeddingResult embeddingResult = embeddingService.generateEmbeddingWithUsage(query);
            List<Double> embedding = embeddingResult.getEmbedding();
            
            // Convert Double to Float for Qdrant API
            List<Float> embeddingFloats = embedding.stream()
                    .map(Double::floatValue)
                    .toList();
            
            // Get Qdrant client
            QdrantClient qdrantClient = dbConnectionManager.getQdrantRepository().getClient();
            if (qdrantClient == null) {
                System.err.println("Qdrant client not available");
                return Collections.emptyList();
            }
            
            // Perform search
            SearchPoints searchRequest = SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(embeddingFloats)
                    .setLimit(effectiveK)
                    .setWithPayload(io.qdrant.client.WithPayloadSelectorFactory.enable(true))
                    .build();
            
            List<ScoredPoint> searchResults = qdrantClient.searchAsync(searchRequest)
                    .get(); // Wait for async result
            
            // Extract note_id from each result's payload in order
            List<String> noteIds = new ArrayList<>();
            for (ScoredPoint point : searchResults) {
                // Check if payload contains note_id
                if (point.getPayloadMap().containsKey("note_id")) {
                    String noteId = point.getPayloadMap().get("note_id").getStringValue();
                    
                    if (noteId != null && !noteId.isEmpty()) {
                        noteIds.add(noteId);
                    }
                }
            }
            
            return noteIds;
            
        } catch (IllegalArgumentException e) {
            // Re-throw validation exceptions
            throw e;
        } catch (ExecutionException e) {
            // Handle async execution errors
            System.err.println("Error executing Qdrant search: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Caused by: " + e.getCause().getMessage());
            }
            return Collections.emptyList();
        } catch (InterruptedException e) {
            // Handle interruption
            Thread.currentThread().interrupt();
            System.err.println("Search was interrupted: " + e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            // Handle any other unexpected errors
            System.err.println("Unexpected error during semantic search: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Caused by: " + e.getCause().getMessage());
            }
            return Collections.emptyList();
        }
    }

    public List<String> searchSemanticklyNotes(String query, String campaignUuid) {
        return searchSemanticklyNotes(query, 5, campaignUuid);
    }
    public List<String> searchSemanticklyNotes(String query, int k, String campaignUuid) {
        //TODO: preprocessing query with LLM to get better query for search
        List<String> foundNotes = getTopKMatch(query, k, campaignUuid);
        //TODO: check with llm if iutput have a good qualit plus fiteri it with LLM
        return foundNotes;
    }
}

