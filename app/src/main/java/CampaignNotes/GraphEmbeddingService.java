package CampaignNotes;

import java.util.List;
import java.util.concurrent.ExecutionException;

import CampaignNotes.database.DatabaseConnectionManager;
import CampaignNotes.llm.OpenAIEmbeddingService;
import static io.qdrant.client.PointIdFactory.id;
import io.qdrant.client.QdrantClient;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;
import io.qdrant.client.grpc.Points.PointStruct;
import model.Artifact;
import model.EmbeddingResult;
import model.Relationship;

/**
 * Service for generating and storing graph embeddings for artifacts and relationships.
 * Uses OpenAI embeddings to create vector representations for Phase 1 (ANN search)
 * of the hybrid deduplication system.
 */
public class GraphEmbeddingService {
    
    private final OpenAIEmbeddingService embeddingService;
    private final DatabaseConnectionManager dbConnectionManager;
    
    /**
     * Constructor with dependency injection.
     * 
     * @param embeddingService the OpenAI embedding service to use
     * @param dbConnectionManager the database connection manager
     */
    public GraphEmbeddingService(OpenAIEmbeddingService embeddingService, 
                               DatabaseConnectionManager dbConnectionManager) {
        this.embeddingService = embeddingService;
        this.dbConnectionManager = dbConnectionManager;
    }
    
    /**
     * Generates a text representation of an artifact for embedding.
     * Combines name, type, and description for comprehensive context.
     * 
     * @param artifact the artifact to create text representation for
     * @return text representation suitable for embedding
     */
    private String createArtifactTextForEmbedding(Artifact artifact) {
        StringBuilder sb = new StringBuilder();
        sb.append("Artifact: ").append(artifact.getName());
        sb.append(" | Type: ").append(artifact.getType());
        
        if (artifact.getDescription() != null && !artifact.getDescription().isEmpty()) {
            sb.append(" | Description: ").append(artifact.getDescription());
        }
        
        return sb.toString();
    }
    
    /**
     * Generates a text representation of a relationship for embedding.
     * Combines source, target, label, and description.
     * 
     * @param relationship the relationship to create text representation for
     * @return text representation suitable for embedding
     */
    private String createRelationshipTextForEmbedding(Relationship relationship) {
        StringBuilder sb = new StringBuilder();
        sb.append("Relationship: ").append(relationship.getSourceArtifactName());
        sb.append(" -[").append(relationship.getLabel()).append("]-> ");
        sb.append(relationship.getTargetArtifactName());
        
        if (relationship.getDescription() != null && !relationship.getDescription().isEmpty()) {
            sb.append(" | Description: ").append(relationship.getDescription());
        }
        
        if (relationship.getReasoning() != null && !relationship.getReasoning().isEmpty()) {
            sb.append(" | Reasoning: ").append(relationship.getReasoning());
        }
        
        return sb.toString();
    }
    
    /**
     * Generates an embedding for an artifact.
     * Creates text representation and uses OpenAI embedding service.
     * 
     * @param artifact the artifact to generate embedding for
     * @return EmbeddingResult with embedding vector and token usage
     */
    public EmbeddingResult generateArtifactEmbedding(Artifact artifact) {
        if (artifact == null) {
            throw new IllegalArgumentException("Artifact cannot be null");
        }
        
        String textForEmbedding = createArtifactTextForEmbedding(artifact);
        return embeddingService.generateEmbeddingWithUsage(textForEmbedding);
    }
    
    /**
     * Generates an embedding for a relationship.
     * Creates text representation and uses OpenAI embedding service.
     * 
     * @param relationship the relationship to generate embedding for
     * @return EmbeddingResult with embedding vector and token usage
     */
    public EmbeddingResult generateRelationshipEmbedding(Relationship relationship) {
        if (relationship == null) {
            throw new IllegalArgumentException("Relationship cannot be null");
        }
        
        String textForEmbedding = createRelationshipTextForEmbedding(relationship);
        return embeddingService.generateEmbeddingWithUsage(textForEmbedding);
    }
    
    /**
     * Stores an artifact embedding in Qdrant with type tag.
     * 
     * @param artifact the artifact whose embedding will be stored
     * @param embedding the embedding vector
     * @param campaignCollectionName the Qdrant collection name for the campaign
     * @return true if stored successfully, false otherwise
     */
    public boolean storeArtifactEmbedding(Artifact artifact, List<Double> embedding, 
                                         String campaignCollectionName) {
        try {
            QdrantClient qdrantClient = dbConnectionManager.getQdrantRepository().getClient();
            if (qdrantClient == null) {
                System.err.println("Qdrant client not available");
                return false;
            }
            
            // Convert embedding to float array for Qdrant
            List<Float> embeddingFloats = embedding.stream()
                    .map(Double::floatValue)
                    .toList();
            
            // Generate deterministic numeric ID from artifact UUID
            // Using UUID's hashCode provides stable mapping across restarts (unlike Math.abs)
            long numericId = java.util.UUID.fromString(artifact.getId()).getMostSignificantBits() & Long.MAX_VALUE;
            
            // Create point with artifact data and type tag
            PointStruct point = PointStruct.newBuilder()
                    .setId(id(numericId))
                    .setVectors(vectors(embeddingFloats))
                    .putPayload("artifact_id", value(artifact.getId()))
                    .putPayload("name", value(artifact.getName()))
                    .putPayload("type", value("artifact"))  // Type tag for filtering
                    .putPayload("artifact_type", value(artifact.getType()))
                    .putPayload("description", value(artifact.getDescription() != null ? artifact.getDescription() : ""))
                    .putPayload("campaign_uuid", value(artifact.getCampaignUuid()))
                    .putPayload("created_at", value(artifact.getCreatedAt().toString()))
                    .build();
            
            // Upsert the point into Qdrant collection
            qdrantClient.upsertAsync(campaignCollectionName, List.of(point)).get();
            
            return true;
            
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error storing artifact embedding: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            System.err.println("Error storing artifact embedding: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Stores a relationship embedding in Qdrant with type tag.
     * 
     * @param relationship the relationship whose embedding will be stored
     * @param embedding the embedding vector
     * @param campaignCollectionName the Qdrant collection name for the campaign
     * @return true if stored successfully, false otherwise
     */
    public boolean storeRelationshipEmbedding(Relationship relationship, List<Double> embedding,
                                             String campaignCollectionName) {
        try {
            QdrantClient qdrantClient = dbConnectionManager.getQdrantRepository().getClient();
            if (qdrantClient == null) {
                System.err.println("Qdrant client not available");
                return false;
            }
            
            // Convert embedding to float array for Qdrant
            List<Float> embeddingFloats = embedding.stream()
                    .map(Double::floatValue)
                    .toList();
            
            // Generate deterministic numeric ID from relationship UUID
            // Using UUID's getMostSignificantBits provides stable mapping across restarts
            long numericId = java.util.UUID.fromString(relationship.getId()).getMostSignificantBits() & Long.MAX_VALUE;
            
            // Create point with relationship data and type tag
            PointStruct point = PointStruct.newBuilder()
                    .setId(id(numericId))
                    .setVectors(vectors(embeddingFloats))
                    .putPayload("relationship_id", value(relationship.getId()))
                    .putPayload("source", value(relationship.getSourceArtifactName()))
                    .putPayload("target", value(relationship.getTargetArtifactName()))
                    .putPayload("label", value(relationship.getLabel()))
                    .putPayload("type", value("relation"))  // Type tag for filtering
                    .putPayload("description", value(relationship.getDescription() != null ? relationship.getDescription() : ""))
                    .putPayload("reasoning", value(relationship.getReasoning() != null ? relationship.getReasoning() : ""))
                    .putPayload("campaign_uuid", value(relationship.getCampaignUuid()))
                    .putPayload("created_at", value(relationship.getCreatedAt().toString()))
                    .build();
            
            // Upsert the point into Qdrant collection
            qdrantClient.upsertAsync(campaignCollectionName, List.of(point)).get();
            
            return true;
            
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error storing relationship embedding: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            System.err.println("Error storing relationship embedding: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Generates and stores embeddings for multiple artifacts in batch.
     * Uses bulk upsert for better performance - single Qdrant call for all points.
     * 
     * @param artifacts list of artifacts to process
     * @param campaignCollectionName the Qdrant collection name
     * @return number of successfully stored embeddings
     */
    public int storeArtifactEmbeddingsBatch(List<Artifact> artifacts, String campaignCollectionName) {
        if (artifacts == null || artifacts.isEmpty()) {
            return 0;
        }
        
        try {
            QdrantClient qdrantClient = dbConnectionManager.getQdrantRepository().getClient();
            if (qdrantClient == null) {
                System.err.println("Qdrant client not available");
                return 0;
            }
            
            // Generate all embeddings and create points list
            java.util.List<PointStruct> points = new java.util.ArrayList<>();
            
            for (Artifact artifact : artifacts) {
                try {
                    EmbeddingResult result = generateArtifactEmbedding(artifact);
                    List<Float> embeddingFloats = result.getEmbedding().stream()
                        .map(Double::floatValue)
                        .toList();
                    
                    // Generate deterministic numeric ID from artifact UUID
                    long numericId = java.util.UUID.fromString(artifact.getId()).getMostSignificantBits() & Long.MAX_VALUE;
                    
                    PointStruct point = PointStruct.newBuilder()
                        .setId(id(numericId))
                        .setVectors(vectors(embeddingFloats))
                        .putPayload("artifact_id", value(artifact.getId()))
                        .putPayload("name", value(artifact.getName()))
                        .putPayload("type", value("artifact"))
                        .putPayload("artifact_type", value(artifact.getType()))
                        .putPayload("description", value(artifact.getDescription() != null ? artifact.getDescription() : ""))
                        .putPayload("campaign_uuid", value(artifact.getCampaignUuid()))
                        .putPayload("created_at", value(artifact.getCreatedAt().toString()))
                        .build();
                    
                    points.add(point);
                } catch (Exception e) {
                    System.err.println("Error processing artifact " + artifact.getId() + ": " + e.getMessage());
                }
            }
            
            // Single bulk upsert for all points
            if (!points.isEmpty()) {
                qdrantClient.upsertAsync(campaignCollectionName, points).get();
                System.out.println("Batch stored " + points.size() + " artifact embeddings");
            }
            
            return points.size();
            
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error in batch upsert: " + e.getMessage());
            Thread.currentThread().interrupt();
            return 0;
        } catch (Exception e) {
            System.err.println("Error in batch upsert: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Generates and stores embeddings for multiple relationships in batch.
     * Uses bulk upsert for better performance - single Qdrant call for all points.
     * 
     * @param relationships list of relationships to process
     * @param campaignCollectionName the Qdrant collection name
     * @return number of successfully stored embeddings
     */
    public int storeRelationshipEmbeddingsBatch(List<Relationship> relationships, String campaignCollectionName) {
        if (relationships == null || relationships.isEmpty()) {
            return 0;
        }
        
        try {
            QdrantClient qdrantClient = dbConnectionManager.getQdrantRepository().getClient();
            if (qdrantClient == null) {
                System.err.println("Qdrant client not available");
                return 0;
            }
            
            // Generate all embeddings and create points list
            java.util.List<PointStruct> points = new java.util.ArrayList<>();
            
            for (Relationship relationship : relationships) {
                try {
                    EmbeddingResult result = generateRelationshipEmbedding(relationship);
                    List<Float> embeddingFloats = result.getEmbedding().stream()
                        .map(Double::floatValue)
                        .toList();
                    
                    // Generate deterministic numeric ID from relationship UUID
                    long numericId = java.util.UUID.fromString(relationship.getId()).getMostSignificantBits() & Long.MAX_VALUE;
                    
                    PointStruct point = PointStruct.newBuilder()
                        .setId(id(numericId))
                        .setVectors(vectors(embeddingFloats))
                        .putPayload("relationship_id", value(relationship.getId()))
                        .putPayload("source", value(relationship.getSourceArtifactName()))
                        .putPayload("target", value(relationship.getTargetArtifactName()))
                        .putPayload("label", value(relationship.getLabel()))
                        .putPayload("type", value("relation"))
                        .putPayload("description", value(relationship.getDescription() != null ? relationship.getDescription() : ""))
                        .putPayload("reasoning", value(relationship.getReasoning() != null ? relationship.getReasoning() : ""))
                        .putPayload("campaign_uuid", value(relationship.getCampaignUuid()))
                        .putPayload("created_at", value(relationship.getCreatedAt().toString()))
                        .build();
                    
                    points.add(point);
                } catch (Exception e) {
                    System.err.println("Error processing relationship " + relationship.getId() + ": " + e.getMessage());
                }
            }
            
            // Single bulk upsert for all points
            if (!points.isEmpty()) {
                qdrantClient.upsertAsync(campaignCollectionName, points).get();
                System.out.println("Batch stored " + points.size() + " relationship embeddings");
            }
            
            return points.size();
            
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error in batch upsert: " + e.getMessage());
            Thread.currentThread().interrupt();
            return 0;
        } catch (Exception e) {
            System.err.println("Error in batch upsert: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Deletes an embedding from Qdrant by UUID.
     * Used when an artifact or relationship is merged and its embedding should be removed.
     * 
     * @param pointUuid the UUID of the point to delete
     * @param campaignCollectionName the Qdrant collection name
     * @return true if deleted successfully, false otherwise
     */
    public boolean deleteEmbedding(String pointUuid, String campaignCollectionName) {
        try {
            QdrantClient qdrantClient = dbConnectionManager.getQdrantRepository().getClient();
            if (qdrantClient == null) {
                System.err.println("Qdrant client not available");
                return false;
            }
            
            // Generate deterministic numeric ID from UUID
            long numericId = java.util.UUID.fromString(pointUuid).getMostSignificantBits() & Long.MAX_VALUE;
            
            qdrantClient.deleteAsync(
                campaignCollectionName,
                List.of(io.qdrant.client.PointIdFactory.id(numericId))
            ).get();
            
            System.out.println("Deleted embedding: " + pointUuid);
            return true;
        } catch (Exception e) {
            System.err.println("Error deleting embedding: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Updates an artifact's embedding in Qdrant after modifications.
     * Generates a new embedding and upserts it (overwriting the old one).
     * 
     * @param artifact the updated artifact
     * @param campaignCollectionName the Qdrant collection name
     * @return true if updated successfully, false otherwise
     */
    public boolean updateArtifactEmbedding(Artifact artifact, String campaignCollectionName) {
        try {
            // Generate new embedding
            EmbeddingResult result = generateArtifactEmbedding(artifact);
            
            // Upsert will overwrite existing point with same ID
            return storeArtifactEmbedding(artifact, result.getEmbedding(), campaignCollectionName);
        } catch (Exception e) {
            System.err.println("Error updating artifact embedding: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Updates a relationship's embedding in Qdrant after modifications.
     * Generates a new embedding and upserts it (overwriting the old one).
     * 
     * @param relationship the updated relationship
     * @param campaignCollectionName the Qdrant collection name
     * @return true if updated successfully, false otherwise
     */
    public boolean updateRelationshipEmbedding(Relationship relationship, String campaignCollectionName) {
        try {
            // Generate new embedding
            EmbeddingResult result = generateRelationshipEmbedding(relationship);
            
            // Upsert will overwrite existing point with same ID
            return storeRelationshipEmbedding(relationship, result.getEmbedding(), campaignCollectionName);
        } catch (Exception e) {
            System.err.println("Error updating relationship embedding: " + e.getMessage());
            return false;
        }
    }
}

