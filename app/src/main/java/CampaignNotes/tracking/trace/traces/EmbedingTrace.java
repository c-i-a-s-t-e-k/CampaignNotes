package CampaignNotes.tracking.trace.traces;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;

import CampaignNotes.tracking.LangfuseHttpClient;
import CampaignNotes.tracking.trace.observations.Observation;
import CampaignNotes.tracking.trace.payload.PayloadBuilder;

/**
 * Trace implementation for embedding operations.
 * 
 * This trace type is designed for single embedding operations without complex workflows.
 * It tracks the generation of vector embeddings from text content, typically for
 * note embedding operations in the campaign notes system.
 * 
 * Based on Langfuse documentation, this trace will contain embedding observations
 * that track the input text, model used, token consumption, and timing information.
 */
public class EmbedingTrace extends Trace {

    /**
     * Constructor for EmbedingTrace.
     * 
     * @param name the trace name (e.g., "note-embedding")
     * @param campaignId the campaign UUID
     * @param noteId the note ID being processed
     * @param userId the user ID (optional)
     * @param customTags additional custom tags
     * @param workflowType type of workflow (typically "embedding-generation")
     * @param httpClient HTTP client for API communication
     * @param payloadBuilder payload builder for JSON creation
     */
    public EmbedingTrace(String name, String campaignId, String noteId, String userId,
                        List<String> customTags, String workflowType,
                        LangfuseHttpClient httpClient, PayloadBuilder payloadBuilder) {
        super(name, campaignId, noteId, userId, customTags, workflowType, httpClient, payloadBuilder);
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create trace payload
                JsonObject tracePayload = payloadBuilder.buildTrace(
                    name, campaignId, noteId, userId, customTags, workflowType);
                tracePayload.addProperty("id", traceId);
                tracePayload.addProperty("timestamp", startTime.toString());

                // Create ingestion event
                JsonObject event = payloadBuilder.buildIngestionEvent("trace-create", tracePayload);
                JsonObject batchPayload = payloadBuilder.buildIngestionBatch(event);

                // Send to Langfuse
                HttpResponse<String> response = httpClient.post("/api/public/ingestion", batchPayload);
                
                if (httpClient.isIngestionSuccessful(response)) {
                    System.out.println("EmbedingTrace initialized successfully: " + traceId);
                    return true;
                } else {
                    System.err.println("Failed to initialize EmbedingTrace. Status: " + 
                        response.statusCode() + ", Response: " + response.body());
                    return false;
                }
            } catch (Exception e) {
                System.err.println("Error initializing EmbedingTrace: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> addObservation(Observation observation) {
        if (isFinalized) {
            return CompletableFuture.completedFuture(false);
        }
        
        return observation.sendToTrace(traceId);
    }

    @Override
    public CompletableFuture<Boolean> updateTraceInput(JsonObject input) {
        if (isFinalized) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                this.input = input;
                
                // Create trace update payload
                JsonObject traceUpdatePayload = new JsonObject();
                traceUpdatePayload.addProperty("id", traceId);
                traceUpdatePayload.add("input", input);

                // Send update to Langfuse
                JsonObject event = payloadBuilder.buildIngestionEvent("trace-update", traceUpdatePayload);
                JsonObject batchPayload = payloadBuilder.buildIngestionBatch(event);

                HttpResponse<String> response = httpClient.post("/api/public/ingestion", batchPayload);
                
                if (httpClient.isIngestionSuccessful(response)) {
                    System.out.println("EmbedingTrace input updated successfully: " + traceId);
                    return true;
                } else {
                    System.err.println("Failed to update EmbedingTrace input. Status: " + 
                        response.statusCode() + ", Response: " + response.body());
                    return false;
                }
            } catch (Exception e) {
                System.err.println("Error updating EmbedingTrace input: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> updateTraceOutput(JsonObject output) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                this.output = output;
                finalizeTrace(); // Mark trace as finalized
                
                // Create trace update payload
                JsonObject traceUpdatePayload = new JsonObject();
                traceUpdatePayload.addProperty("id", traceId);
                traceUpdatePayload.add("output", output);

                // Send final update to Langfuse
                JsonObject event = payloadBuilder.buildIngestionEvent("trace-update", traceUpdatePayload);
                JsonObject batchPayload = payloadBuilder.buildIngestionBatch(event);

                HttpResponse<String> response = httpClient.post("/api/public/ingestion", batchPayload);
                
                if (httpClient.isIngestionSuccessful(response)) {
                    System.out.println("EmbedingTrace finalized successfully: " + traceId);
                    return true;
                } else {
                    System.err.println("Failed to finalize EmbedingTrace. Status: " + 
                        response.statusCode() + ", Response: " + response.body());
                    return false;
                }
            } catch (Exception e) {
                System.err.println("Error finalizing EmbedingTrace: " + e.getMessage());
                return false;
            }
        });
    }
}
