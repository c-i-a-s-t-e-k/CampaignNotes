package CampaignNotes.deduplication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import CampaignNotes.config.DeduplicationConfig;
import CampaignNotes.database.DatabaseConnectionManager;
import CampaignNotes.dto.deduplication.ArtifactCandidate;
import CampaignNotes.dto.deduplication.RelationshipCandidate;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.PointStruct;
import io.qdrant.client.grpc.Collections.SearchPoints;

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
            
            // Build search request with filter for artifact type and campaign
            var searchResult = qdrantClient.searchAsync(
                campaignCollectionName,
                embeddingFloats,
                Map.of("type", "artifact", "campaign_uuid", campaignUuid),
                config.getCandidateLimit(),
                null
            ).get();
            
            // Process search results
            for (var scoredPoint : searchResult) {
                double similarity = scoredPoint.getScore();
                
                // Filter by similarity threshold
                if (similarity < config.getSimilarityThreshold()) {
                    continue;
                }
                
                // Extract payload information
                var payload = scoredPoint.getPayload();
                
                String artifactId = payload.get("artifact_id") != null ? 
                    payload.get("artifact_id").getStringValue() : "";
                String name = payload.get("name") != null ? 
                    payload.get("name").getStringValue() : "";
                String type = payload.get("artifact_type") != null ? 
                    payload.get("artifact_type").getStringValue() : "";
                String description = payload.get("description") != null ? 
                    payload.get("description").getStringValue() : "";
                
                if (!artifactId.isEmpty() && !name.isEmpty()) {
                    ArtifactCandidate candidate = new ArtifactCandidate(artifactId, name, type, 
                                                                       description, similarity);
                    candidates.add(candidate);
                }
            }
            
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error searching for similar artifacts: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Error searching for similar artifacts: " + e.getMessage());
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
            
            // Build search request with filter for relation type and campaign
            var searchResult = qdrantClient.searchAsync(
                campaignCollectionName,
                embeddingFloats,
                Map.of("type", "relation", "campaign_uuid", campaignUuid),
                config.getCandidateLimit(),
                null
            ).get();
            
            // Process search results
            for (var scoredPoint : searchResult) {
                double similarity = scoredPoint.getScore();
                
                // Filter by similarity threshold
                if (similarity < config.getSimilarityThreshold()) {
                    continue;
                }
                
                // Extract payload information
                var payload = scoredPoint.getPayload();
                
                String relationshipId = payload.get("relationship_id") != null ? 
                    payload.get("relationship_id").getStringValue() : "";
                String source = payload.get("source") != null ? 
                    payload.get("source").getStringValue() : "";
                String target = payload.get("target") != null ? 
                    payload.get("target").getStringValue() : "";
                String label = payload.get("label") != null ? 
                    payload.get("label").getStringValue() : "";
                String description = payload.get("description") != null ? 
                    payload.get("description").getStringValue() : "";
                
                if (!relationshipId.isEmpty() && !source.isEmpty() && !target.isEmpty()) {
                    RelationshipCandidate candidate = new RelationshipCandidate(relationshipId, source, target,
                                                                              label, description, similarity);
                    candidates.add(candidate);
                }
            }
            
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error searching for similar relationships: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Error searching for similar relationships: " + e.getMessage());
        }
        
        return candidates;
    }
}

