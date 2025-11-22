package CampaignNotes.assistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import CampaignNotes.database.DatabaseConnectionManager;
import CampaignNotes.llm.OpenAIEmbeddingService;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import model.Campain;
import model.EmbeddingResult;

/**
 * Service for performing multi-type semantic search on campaign data using Qdrant vector database.
 * Provides methods to search for notes, artifacts, and relations by semantic similarity.
 */
@Service
public class VectorSearchService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(VectorSearchService.class);
    private static final int MAX_K = 100;
    
    private final DatabaseConnectionManager dbConnectionManager;
    private final OpenAIEmbeddingService embeddingService;
    
    public VectorSearchService(DatabaseConnectionManager dbConnectionManager,
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
     * Searches for items of a specific type by semantic similarity.
     * 
     * @param query the search query text
     * @param type the type to filter ("note", "artifact", or "relation")
     * @param k the number of results to return
     * @param campaignUuid the UUID of the campaign to search within
     * @return list of point IDs ordered by similarity, with payload data
     */
    public List<Map<String, Object>> searchByType(String query, String type, int k, String campaignUuid) {
        LOGGER.debug("Searching by type - query: '{}', type: '{}', k: {}, campaignUuid: '{}'",
                query, type, k, campaignUuid);
        
        // Input validation
        if (query == null || query.trim().isEmpty()) {
            LOGGER.error("Validation failed: Query is null or empty");
            throw new IllegalArgumentException("Query cannot be null or empty");
        }
        if (type == null || type.trim().isEmpty()) {
            LOGGER.error("Validation failed: Type is null or empty");
            throw new IllegalArgumentException("Type cannot be null or empty");
        }
        if (k <= 0) {
            LOGGER.error("Validation failed: k must be positive, got: {}", k);
            throw new IllegalArgumentException("k must be positive, got: " + k);
        }
        if (campaignUuid == null || campaignUuid.trim().isEmpty()) {
            LOGGER.error("Validation failed: Campaign UUID is null or empty");
            throw new IllegalArgumentException("Campaign UUID cannot be null or empty");
        }
        
        int effectiveK = Math.min(k, MAX_K);
        if (k > MAX_K) {
            LOGGER.warn("Requested k={} exceeds maximum {}, clamped to maximum", k, MAX_K);
        }
        
        try {
            // Fetch campaign
            LOGGER.debug("Fetching campaign from database: {}", campaignUuid);
            Campain campaign = dbConnectionManager.getSqliteRepository().getCampaignById(campaignUuid);
            if (campaign == null) {
                LOGGER.error("Campaign not found with UUID: {}", campaignUuid);
                throw new IllegalArgumentException("Campaign not found with UUID: " + campaignUuid);
            }
            
            String collectionName = campaign.getQuadrantCollectionName();
            if (collectionName == null || collectionName.isEmpty()) {
                LOGGER.error("Campaign '{}' has no Qdrant collection name", campaignUuid);
                return Collections.emptyList();
            }
            LOGGER.debug("Using Qdrant collection: '{}'", collectionName);
            
            // Generate embedding
            LOGGER.debug("Generating embedding for query: '{}'", query);
            EmbeddingResult embeddingResult = embeddingService.generateEmbeddingWithUsage(query);
            List<Double> embedding = embeddingResult.getEmbedding();
            LOGGER.debug("Embedding generated successfully, dimension: {}", embedding.size());
            
            // Convert to Float for Qdrant
            List<Float> embeddingFloats = embedding.stream()
                    .map(Double::floatValue)
                    .toList();
            
            // Get Qdrant client
            QdrantClient qdrantClient = dbConnectionManager.getQdrantRepository().getClient();
            if (qdrantClient == null) {
                LOGGER.error("Qdrant client is not available");
                return Collections.emptyList();
            }
            
            // Build search request with type filter
            LOGGER.debug("Building search request with type filter: '{}'", type);
            SearchPoints.Builder searchBuilder = SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(embeddingFloats)
                    .setLimit(effectiveK)
                    .setWithPayload(io.qdrant.client.WithPayloadSelectorFactory.enable(true));
            
            // Add type filter
            Filter filter = Filter.newBuilder()
                    .addMust(io.qdrant.client.ConditionFactory.matchKeyword("type", type))
                    .build();
            searchBuilder.setFilter(filter);
            
            SearchPoints searchRequest = searchBuilder.build();
            
            LOGGER.info("Executing semantic search in collection '{}' for type '{}', query: '{}'",
                    collectionName, type, query);
            List<ScoredPoint> searchResults = qdrantClient.searchAsync(searchRequest).get();
            LOGGER.info("Search completed successfully, found {} results", searchResults.size());
            
            // Extract results with payload
            List<Map<String, Object>> results = new ArrayList<>();
            for (ScoredPoint point : searchResults) {
                Map<String, Object> result = new HashMap<>();
                result.put("score", point.getScore());
                
                // Extract all payload fields
                var payloadMap = point.getPayloadMap();
                for (var entry : payloadMap.entrySet()) {
                    String key = entry.getKey();
                    var value = entry.getValue();
                    
                    // Convert Qdrant Value to Java object
                    if (value.hasStringValue()) {
                        result.put(key, value.getStringValue());
                    } else if (value.hasIntegerValue()) {
                        result.put(key, value.getIntegerValue());
                    } else if (value.hasDoubleValue()) {
                        result.put(key, value.getDoubleValue());
                    } else if (value.hasBoolValue()) {
                        result.put(key, value.getBoolValue());
                    }
                }
                
                results.add(result);
                LOGGER.debug("Result #{}: score={}, payload keys={}", 
                        results.size(), point.getScore(), result.keySet());
            }
            
            LOGGER.info("Extracted {} valid results from search", results.size());
            return results;
            
        } catch (IllegalArgumentException e) {
            LOGGER.error("Validation error during semantic search: {}", e.getMessage());
            throw e;
        } catch (ExecutionException e) {
            LOGGER.error("Error executing Qdrant search: {}", e.getMessage(), e);
            return Collections.emptyList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Search was interrupted: {}", e.getMessage(), e);
            return Collections.emptyList();
        } catch (Exception e) {
            LOGGER.error("Unexpected error during semantic search: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Searches for notes by semantic similarity.
     * 
     * @param query the search query text
     * @param k the number of results to return
     * @param campaignUuid the UUID of the campaign to search within
     * @return list of note IDs ordered by similarity
     */
    public List<String> searchNotes(String query, int k, String campaignUuid) {
        List<Map<String, Object>> results = searchByType(query, "note", k, campaignUuid);
        return results.stream()
                .filter(r -> r.containsKey("note_id"))
                .map(r -> (String) r.get("note_id"))
                .toList();
    }
    
    /**
     * Searches for artifacts by semantic similarity.
     * 
     * @param query the search query text
     * @param k the number of results to return
     * @param campaignUuid the UUID of the campaign to search within
     * @return list of results with artifact data
     */
    public List<Map<String, Object>> searchArtifacts(String query, int k, String campaignUuid) {
        return searchByType(query, "artifact", k, campaignUuid);
    }
    
    /**
     * Searches for relations by semantic similarity.
     * 
     * @param query the search query text
     * @param k the number of results to return
     * @param campaignUuid the UUID of the campaign to search within
     * @return list of results with relation data
     */
    public List<Map<String, Object>> searchRelations(String query, int k, String campaignUuid) {
        return searchByType(query, "relation", k, campaignUuid);
    }
}

