package CampaignNotes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SemantickSearchService.class);
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
        LOGGER.debug("Starting semantic search - query: '{}', k: {}, campaignUuid: '{}'", 
                query, k, campaignUuid);
        
        // Input validation
        if (query == null || query.trim().isEmpty()) {
            LOGGER.error("Validation failed: Query is null or empty");
            throw new IllegalArgumentException("Query cannot be null or empty");
        }
        if (k <= 0) {
            LOGGER.error("Validation failed: k must be positive, got: {}", k);
            throw new IllegalArgumentException("k must be positive, got: " + k);
        }
        if (campaignUuid == null || campaignUuid.trim().isEmpty()) {
            LOGGER.error("Validation failed: Campaign UUID is null or empty");
            throw new IllegalArgumentException("Campaign UUID cannot be null or empty");
        }
        
        LOGGER.debug("Input validation passed");
        
        // Clamp k to maximum allowed
        int effectiveK = Math.min(k, MAX_K);
        if (k > MAX_K) {
            LOGGER.warn("Requested k={} exceeds maximum {}, clamped to maximum", k, MAX_K);
        } else {
            LOGGER.debug("Using k={}", effectiveK);
        }
        
        try {
            // Fetch campaign to get Qdrant collection name
            LOGGER.debug("Fetching campaign from database: {}", campaignUuid);
            Campain campaign = dbConnectionManager.getSqliteRepository().getCampaignById(campaignUuid);
            if (campaign == null) {
                LOGGER.error("Campaign not found with UUID: {}", campaignUuid);
                throw new IllegalArgumentException("Campaign not found with UUID: " + campaignUuid);
            }
            LOGGER.debug("Campaign found: {}", campaign.getName());
            
            String collectionName = campaign.getQuadrantCollectionName();
            if (collectionName == null || collectionName.isEmpty()) {
                LOGGER.error("Campaign '{}' has no Qdrant collection name", campaignUuid);
                return Collections.emptyList();
            }
            LOGGER.debug("Using Qdrant collection: '{}'", collectionName);
            
            // Generate embedding for the query
            LOGGER.debug("Generating embedding for query: '{}'", query);
            EmbeddingResult embeddingResult = embeddingService.generateEmbeddingWithUsage(query);
            List<Double> embedding = embeddingResult.getEmbedding();
            LOGGER.debug("Embedding generated successfully, dimension: {}", embedding.size());
            
            // Convert Double to Float for Qdrant API
            List<Float> embeddingFloats = embedding.stream()
                    .map(Double::floatValue)
                    .toList();
            LOGGER.debug("Embedding converted to float format");
            
            // Get Qdrant client
            LOGGER.debug("Getting Qdrant client");
            QdrantClient qdrantClient = dbConnectionManager.getQdrantRepository().getClient();
            if (qdrantClient == null) {
                LOGGER.error("Qdrant client is not available");
                return Collections.emptyList();
            }
            LOGGER.debug("Qdrant client obtained successfully");
            
            // Perform search
            LOGGER.debug("Building search request for collection '{}' with limit {}", 
                    collectionName, effectiveK);
            SearchPoints searchRequest = SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(embeddingFloats)
                    .setLimit(effectiveK)
                    .setWithPayload(io.qdrant.client.WithPayloadSelectorFactory.enable(true))
                    .build();
            
            LOGGER.info("Executing semantic search in collection '{}' for query: '{}'", 
                    collectionName, query);
            List<ScoredPoint> searchResults = qdrantClient.searchAsync(searchRequest)
                    .get(); // Wait for async result
            LOGGER.info("Search completed successfully, found {} results", searchResults.size());
            
            // Extract note_id from each result's payload in order
            List<String> noteIds = new ArrayList<>();
            for (ScoredPoint point : searchResults) {
                // Check if payload contains note_id
                if (point.getPayloadMap().containsKey("note_id")) {
                    String noteId = point.getPayloadMap().get("note_id").getStringValue();
                    
                    if (noteId != null && !noteId.isEmpty()) {
                        noteIds.add(noteId);
                        LOGGER.debug("Result #{}: note_id='{}', score={}", 
                                noteIds.size(), noteId, point.getScore());
                    } else {
                        LOGGER.warn("Found result with empty note_id, skipping");
                    }
                } else {
                    LOGGER.warn("Found result without note_id in payload, skipping");
                }
            }
            
            LOGGER.info("Extracted {} valid note IDs from search results", noteIds.size());
            return noteIds;
            
        } catch (IllegalArgumentException e) {
            // Re-throw validation exceptions
            LOGGER.error("Validation error during semantic search: {}", e.getMessage());
            throw e;
        } catch (ExecutionException e) {
            // Handle async execution errors
            LOGGER.error("Error executing Qdrant search: {}", e.getMessage(), e);
            if (e.getCause() != null) {
                LOGGER.error("Caused by: {}", e.getCause().getMessage());
            }
            return Collections.emptyList();
        } catch (InterruptedException e) {
            // Handle interruption
            Thread.currentThread().interrupt();
            LOGGER.error("Search was interrupted: {}", e.getMessage(), e);
            return Collections.emptyList();
        } catch (Exception e) {
            // Handle any other unexpected errors
            LOGGER.error("Unexpected error during semantic search: {}", e.getMessage(), e);
            if (e.getCause() != null) {
                LOGGER.error("Caused by: {}", e.getCause().getMessage());
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

