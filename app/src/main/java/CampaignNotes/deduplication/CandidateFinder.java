package CampaignNotes.deduplication;

import java.util.ArrayList;
import java.util.List;

import CampaignNotes.config.DeduplicationConfig;
import CampaignNotes.database.DatabaseConnectionManager;
import CampaignNotes.dto.deduplication.ArtifactCandidate;
import CampaignNotes.dto.deduplication.RelationshipCandidate;
import static io.qdrant.client.ConditionFactory.matchKeyword;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;

/**
 * Service for finding candidate artifacts and relationships in Phase 1 (ANN search)
 * of the hybrid deduplication system.
 * 
 * Uses Approximate Nearest Neighbors (ANN) search in Qdrant to quickly identify
 * potentially duplicate artifacts based on vector similarity.
 */
public class CandidateFinder {
    
    private final QdrantClient qdrantClient;
    private final DeduplicationConfig config;
    
    /**
     * Constructor with dependency injection.
     * 
     * @param dbConnectionManager the database connection manager
     * @param config the deduplication configuration
     */
    public CandidateFinder(DatabaseConnectionManager dbConnectionManager, DeduplicationConfig config) {
        this.qdrantClient = dbConnectionManager.getQdrantRepository().getClient();
        this.config = config;
    }
    
    /**
     * Finds similar artifacts in the campaign using ANN search.
     * Filters results based on similarity threshold and candidate limit from config.
     * 
     * @param embedding the embedding vector to search for
     * @param campaignUuid the UUID of the campaign
     * @param campaignCollectionName the Qdrant collection name
     * @return list of candidate artifacts sorted by similarity (highest first)
     */
    public List<ArtifactCandidate> findSimilarArtifacts(List<Double> embedding, String campaignUuid,
                                                       String campaignCollectionName) {
        List<ArtifactCandidate> candidates = new ArrayList<>();
        
        if (qdrantClient == null) {
            System.err.println("Qdrant client not available");
            return candidates;
        }
        
        if (embedding == null || embedding.isEmpty()) {
            System.err.println("Embedding cannot be null or empty");
            return candidates;
        }
        
        try {
            // Convert to float array for Qdrant
            List<Float> embeddingFloats = embedding.stream()
                    .map(Double::floatValue)
                    .toList();
            
            // Build filter for artifact type and campaign
            Filter filter = Filter.newBuilder()
                    .addMust(matchKeyword("type", "artifact"))
                    .build();
            
            // Build search request
            SearchPoints searchRequest = SearchPoints.newBuilder()
                    .setCollectionName(campaignCollectionName)
                    .addAllVector(embeddingFloats)
                    .setLimit(config.getCandidateLimit())
                    .setFilter(filter)
                    .setWithPayload(io.qdrant.client.WithPayloadSelectorFactory.enable(true))
                    .build();
            
            List<ScoredPoint> searchResult = qdrantClient.searchAsync(searchRequest).get();
            
            // Process search results
            for (ScoredPoint scoredPoint : searchResult) {
                double similarity = scoredPoint.getScore();
                
                // Filter by similarity threshold
                if (similarity < config.getSimilarityThreshold()) {
                    continue;
                }
                
                // Extract payload information
                var payloadMap = scoredPoint.getPayloadMap();
                
                String artifactId = payloadMap.containsKey("artifact_id") ? 
                    payloadMap.get("artifact_id").getStringValue() : "";
                String name = payloadMap.containsKey("name") ? 
                    payloadMap.get("name").getStringValue() : "";
                String type = payloadMap.containsKey("artifact_type") ? 
                    payloadMap.get("artifact_type").getStringValue() : "";
                String description = payloadMap.containsKey("description") ? 
                    payloadMap.get("description").getStringValue() : "";
                
                if (!artifactId.isEmpty() && !name.isEmpty()) {
                    ArtifactCandidate candidate = new ArtifactCandidate(artifactId, name, type, 
                                                                       description, similarity);
                    candidates.add(candidate);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error searching for similar artifacts: " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        
        return candidates;
    }
    
    /**
     * Finds similar relationships in the campaign using ANN search.
     * Filters results based on similarity threshold and candidate limit from config.
     * 
     * @param embedding the embedding vector to search for
     * @param campaignUuid the UUID of the campaign
     * @param campaignCollectionName the Qdrant collection name
     * @return list of candidate relationships sorted by similarity (highest first)
     */
    public List<RelationshipCandidate> findSimilarRelationships(List<Double> embedding, String campaignUuid,
                                                               String campaignCollectionName) {
        List<RelationshipCandidate> candidates = new ArrayList<>();
        
        if (qdrantClient == null) {
            System.err.println("Qdrant client not available");
            return candidates;
        }
        
        if (embedding == null || embedding.isEmpty()) {
            System.err.println("Embedding cannot be null or empty");
            return candidates;
        }
        
        try {
            // Convert to float array for Qdrant
            List<Float> embeddingFloats = embedding.stream()
                    .map(Double::floatValue)
                    .toList();
            
            // Build filter for relation type and campaign
            Filter filter = Filter.newBuilder()
                    .addMust(matchKeyword("type", "relation"))
                    .build();
            
            // Build search request
            SearchPoints searchRequest = SearchPoints.newBuilder()
                    .setCollectionName(campaignCollectionName)
                    .addAllVector(embeddingFloats)
                    .setLimit(config.getCandidateLimit())
                    .setFilter(filter)
                    .setWithPayload(io.qdrant.client.WithPayloadSelectorFactory.enable(true))
                    .build();
            
            List<ScoredPoint> searchResult = qdrantClient.searchAsync(searchRequest).get();
            
            // Process search results
            for (ScoredPoint scoredPoint : searchResult) {
                double similarity = scoredPoint.getScore();
                
                // Filter by similarity threshold
                if (similarity < config.getSimilarityThreshold()) {
                    continue;
                }
                
                // Extract payload information
                var payloadMap = scoredPoint.getPayloadMap();
                
                String relationshipId = payloadMap.containsKey("relationship_id") ? 
                    payloadMap.get("relationship_id").getStringValue() : "";
                String source = payloadMap.containsKey("source") ? 
                    payloadMap.get("source").getStringValue() : "";
                String target = payloadMap.containsKey("target") ? 
                    payloadMap.get("target").getStringValue() : "";
                String label = payloadMap.containsKey("label") ? 
                    payloadMap.get("label").getStringValue() : "";
                String description = payloadMap.containsKey("description") ? 
                    payloadMap.get("description").getStringValue() : "";
                
                if (!relationshipId.isEmpty() && !source.isEmpty() && !target.isEmpty()) {
                    RelationshipCandidate candidate = new RelationshipCandidate(relationshipId, source, target,
                                                                              label, description, similarity);
                    candidates.add(candidate);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error searching for similar relationships: " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        
        return candidates;
    }
}

