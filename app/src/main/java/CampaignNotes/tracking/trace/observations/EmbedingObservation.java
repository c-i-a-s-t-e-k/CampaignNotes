package CampaignNotes.tracking.trace.observations;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;

import CampaignNotes.tracking.LangfuseHttpClient;
import CampaignNotes.tracking.trace.payload.PayloadBuilder;
import model.Note;

/**
 * Observation implementation for embedding operations.
 * 
 * This observation type tracks the generation of vector embeddings from text content,
 * typically for note embedding operations. It captures input text, model used,
 * token consumption, timing information, and cost calculations.
 * 
 * Based on Langfuse documentation, this creates a generation observation
 * specifically optimized for embedding models like text-embedding-ada-002.
 */
public class EmbedingObservation extends Observation {
    
    private Note sourceNote;
    private String campaignId;
    private int tokensUsed;
    private Double calculatedCost;

    /**
     * Constructor for EmbedingObservation.
     * 
     * @param name the observation name (e.g., "note-embedding")
     * @param httpClient HTTP client for API communication
     * @param payloadBuilder payload builder for JSON creation
     */
    public EmbedingObservation(String name, LangfuseHttpClient httpClient, PayloadBuilder payloadBuilder) {
        super(name, ObservationType.EMBEDDING_OBSERVATION, httpClient, payloadBuilder);
    }

    /**
     * Sets the source note for this embedding operation.
     * 
     * @param note the note being embedded
     * @return this observation for method chaining
     */
    public EmbedingObservation withNote(Note note) {
        this.sourceNote = note;
        
        // Set input based on note content
        JsonObject inputJson = new JsonObject();
        inputJson.addProperty("text", note.getFullTextForEmbedding());
        inputJson.addProperty("note_id", note.getId());
        inputJson.addProperty("note_title", note.getTitle());
        inputJson.addProperty("content_length", note.getContent().length());
        setInput(inputJson);
        
        return this;
    }

    /**
     * Sets the campaign ID for this embedding operation.
     * 
     * @param campaignId the campaign UUID
     * @return this observation for method chaining
     */
    public EmbedingObservation withCampaignId(String campaignId) {
        this.campaignId = campaignId;
        return this;
    }

    /**
     * Sets the embedding model used.
     * 
     * @param model the embedding model (e.g., "text-embedding-ada-002")
     * @return this observation for method chaining
     */
    public EmbedingObservation withModel(String model) {
        setModel(model);
        return this;
    }

    /**
     * Sets the token usage information.
     * 
     * @param tokensUsed exact number of tokens consumed
     * @return this observation for method chaining
     */
    public EmbedingObservation withTokenUsage(int tokensUsed) {
        this.tokensUsed = tokensUsed;
        
        // Set output to indicate embedding generation
        JsonObject outputJson = new JsonObject();
        outputJson.addProperty("embedding_generated", true);
        outputJson.addProperty("tokens_consumed", tokensUsed);
        outputJson.addProperty("output_type", "embedding-vector");
        setOutput(outputJson);
        
        return this;
    }

    /**
     * Sets the calculated cost for this operation.
     * 
     * @param cost the calculated cost in USD
     * @return this observation for method chaining
     */
    public EmbedingObservation withCost(Double cost) {
        this.calculatedCost = cost;
        return this;
    }

    /**
     * Finalizes the observation and prepares it for sending.
     * 
     * @return this observation for method chaining
     */
    public EmbedingObservation finalizeForSending() {
        finalizeObservation();
        
        // Create comprehensive metadata
        JsonObject metadataJson = new JsonObject();
        if (campaignId != null) {
            metadataJson.addProperty("campaign_id", campaignId);
        }
        if (sourceNote != null) {
            metadataJson.addProperty("note_id", sourceNote.getId());
            metadataJson.addProperty("note_title", sourceNote.getTitle());
            metadataJson.addProperty("note_content_length", sourceNote.getContent().length());
            metadataJson.addProperty("note_is_override", sourceNote.isOverride());
        }
        metadataJson.addProperty("system_component", "note-embedding");
        metadataJson.addProperty("operation_type", "text-embedding");
        metadataJson.addProperty("exact_tokens_used", tokensUsed);
        metadataJson.addProperty("operation_duration_ms", getDurationMs());
        
        if (calculatedCost != null) {
            metadataJson.addProperty("calculated_cost_usd", calculatedCost);
            metadataJson.addProperty("pricing_source", "langfuse_models_api");
        }
        
        setMetadata(metadataJson);
        return this;
    }

    @Override
    public CompletableFuture<Boolean> sendToTrace(String traceId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Build payload for this observation
                JsonObject observationPayload = buildPayload(traceId);
                
                // Create ingestion event
                JsonObject event = payloadBuilder.buildIngestionEvent("generation-create", observationPayload);
                JsonObject batchPayload = payloadBuilder.buildIngestionBatch(event);
                
                // Send to Langfuse
                HttpResponse<String> response = httpClient.post("/api/public/ingestion", batchPayload);
                
                if (httpClient.isIngestionSuccessful(response)) {
                    System.out.println("EmbedingObservation sent successfully: " + getObservationId() + 
                        " (tokens: " + tokensUsed + ")");
                    return true;
                } else {
                    System.err.println("Failed to send EmbedingObservation. Status: " + 
                        response.statusCode() + ", Response: " + response.body());
                    return false;
                }
            } catch (Exception e) {
                System.err.println("Error sending EmbedingObservation: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    protected JsonObject buildPayload(String traceId) {
        if (sourceNote == null) {
            throw new IllegalStateException("Source note is required for EmbedingObservation");
        }
        
        // Use the existing payload builder method for embedding generation
        return payloadBuilder.buildEmbeddingGeneration(
            traceId, sourceNote, getModel(), campaignId, tokensUsed, getDurationMs());
    }
}
