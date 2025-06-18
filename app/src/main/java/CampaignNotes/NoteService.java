package CampaignNotes;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static io.qdrant.client.PointIdFactory.id;
import io.qdrant.client.QdrantClient;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Points.PointStruct;
import model.Campain;
import model.Note;

/**
 * Service for managing campaign notes.
 * Handles note validation, embedding generation, and storage in Qdrant vector database.
 */
public class NoteService {
    
    private final DataBaseLoader dbLoader;
    private final OpenAIEmbeddingService embeddingService;
    private final LangfuseClient langfuseClient;
    
    /**
     * Constructor initializes all required services.
     */
    public NoteService() {
        this.dbLoader = new DataBaseLoader();
        this.embeddingService = new OpenAIEmbeddingService();
        this.langfuseClient = new LangfuseClient();
    }
    
    /**
     * Adds a note to the specified campaign.
     * Validates the note, generates embedding, and stores in Qdrant with Langfuse monitoring.
     * 
     * @param note the note to add
     * @param campaign the campaign to add the note to
     * @return true if the note was successfully added, false otherwise
     */
    public boolean addNote(Note note, Campain campaign) {
        if (note == null || campaign == null) {
            System.err.println("Note and campaign cannot be null");
            return false;
        }
        
        // Validate note
        if (!note.isValid()) {
            System.err.println("Note validation failed: " + note.toString());
            return false;
        }
        
        // Start tracking session in Langfuse
        String traceId = langfuseClient.trackNoteProcessingSession(
            "add-note", campaign.getUuid(), note.getId(), null);
        
        try {
            // Generate embedding
            long startTime = System.currentTimeMillis();
            String textForEmbedding = note.getFullTextForEmbedding();
            List<Double> embedding = embeddingService.generateEmbedding(textForEmbedding);
            long durationMs = System.currentTimeMillis() - startTime;
            
            // Track embedding generation in Langfuse
            int estimatedTokens = estimateTokenCount(textForEmbedding);
            langfuseClient.trackEmbedding(
                textForEmbedding, 
                embeddingService.getEmbeddingModel(),
                campaign.getUuid(),
                note.getId(),
                estimatedTokens,
                durationMs
            );
            
            // Store in Qdrant
            boolean stored = storeNoteInQdrant(note, campaign, embedding);
            
            if (stored) {
                System.out.println("Note successfully added to campaign: " + campaign.getName());
                return true;
            } else {
                System.err.println("Failed to store note in Qdrant");
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("Error adding note: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Stores a note with its embedding in Qdrant vector database.
     * 
     * @param note the note to store
     * @param campaign the campaign the note belongs to
     * @param embedding the embedding vector for the note
     * @return true if storage was successful, false otherwise
     */
    private boolean storeNoteInQdrant(Note note, Campain campaign, List<Double> embedding) {
        try {
            QdrantClient qdrantClient = dbLoader.getQdrantClient();
            if (qdrantClient == null) {
                System.err.println("Qdrant client not available");
                return false;
            }
            
            // Ensure collection exists
            ensureCollectionExists(campaign, qdrantClient);
            
            // Convert embedding to float array for Qdrant
            List<Float> embeddingFloats = embedding.stream()
                    .map(Double::floatValue)
                    .toList();
            
            // Generate hash of note ID for numerical ID
            long numericId = Math.abs(note.getId().hashCode());
            
            // Create the point using the proper builder pattern with payload
            PointStruct point = PointStruct.newBuilder()
                    .setId(id(numericId))
                    .setVectors(vectors(embeddingFloats))
                    .putPayload("note_id", value(note.getId()))
                    .putPayload("title", value(note.getTitle()))
                    .putPayload("content", value(note.getContent()))
                    .putPayload("campaign_uuid", value(note.getCampaignUuid()))
                    .putPayload("created_at", value(note.getCreatedAt().toString()))
                    .putPayload("updated_at", value(note.getUpdatedAt().toString()))
                    .putPayload("is_override", value(note.isOverride()))
                    .build();
            
            // Add override reason if present
            if (note.getOverrideReason() != null) {
                point = point.toBuilder()
                        .putPayload("override_reason", value(note.getOverrideReason()))
                        .build();
            }
            
            // Upsert the point into Qdrant collection
            qdrantClient.upsertAsync(
                    campaign.getQuadrantCollectionName(),
                    List.of(point)
            ).get();
            
            System.out.println("Note successfully stored in Qdrant collection: " + 
                campaign.getQuadrantCollectionName() + " with ID: " + numericId);
            
            return true;
            
        } catch (Exception e) {
            System.err.println("Error storing note in Qdrant: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Ensures that the Qdrant collection exists for the campaign.
     */
    private void ensureCollectionExists(Campain campaign, QdrantClient qdrantClient) 
            throws ExecutionException, InterruptedException {
        try {
            // Try to get collection info
            qdrantClient.getCollectionInfoAsync(campaign.getQuadrantCollectionName()).get();
        } catch (Exception e) {
            // Collection doesn't exist, create it
            System.out.println("Creating Qdrant collection: " + campaign.getQuadrantCollectionName());
            qdrantClient.createCollectionAsync(
                    campaign.getQuadrantCollectionName(),
                    VectorParams.newBuilder()
                            .setDistance(Distance.Cosine)
                            .setSize(embeddingService.getEmbeddingDimension())
                            .build()
            ).get();
        }
    }
    
    /**
     * Estimates token count for a given text (rough approximation).
     * OpenAI typically counts ~4 characters per token for English text.
     * 
     * @param text the text to estimate tokens for
     * @return estimated token count
     */
    private int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }
    
    /**
     * Checks if all required services are available.
     * 
     * @return true if all services are ready, false otherwise
     */
    public boolean checkServicesAvailability() {
        System.out.println("Checking services availability...");
        
        boolean dbAvailable = dbLoader.checkDatabasesAvailability();
        if (!dbAvailable) {
            System.err.println("Database services not available");
            return false;
        }
        
        boolean openAiAvailable = embeddingService.testConnection();
        if (!openAiAvailable) {
            System.err.println("OpenAI service not available");
            return false;
        }
        
        boolean langfuseAvailable = langfuseClient.checkConnection();
        if (!langfuseAvailable) {
            System.err.println("Langfuse service not available");
            return false;
        }
        
        System.out.println("All services are available");
        return true;
    }
} 