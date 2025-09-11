package CampaignNotes.tracking.trace.traces;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonObject;

import CampaignNotes.tracking.LangfuseHttpClient;
import CampaignNotes.tracking.trace.observations.Observation;
import CampaignNotes.tracking.trace.payload.PayloadBuilder;

/**
 * Trace implementation for artifact relation workflows.
 * 
 * This trace type is designed for the complete NAE → ARE pipeline, tracking
 * multi-step processing involving both Note Artifact Extraction (NAE) and
 * Artifact Relationship Extraction (ARE) operations.
 */
public class ArtefactRelationTrace extends Trace {
    
    // Session management for multi-step processing
    private final String sessionId;
    private final ConcurrentHashMap<String, Observation> observations;
    private final AtomicInteger observationCount;
    
    // Workflow state tracking (commented out to avoid unused warnings, can be uncommented when needed)
    // private boolean naeCompleted = false;
    // private boolean areCompleted = false;
    // private JsonObject naeResults;
    // private JsonObject areResults;

    /**
     * Constructor for ArtefactRelationTrace.
     */
    public ArtefactRelationTrace(String name, String campaignId, String noteId, String userId,
                                List<String> customTags, String workflowType,
                                LangfuseHttpClient httpClient, PayloadBuilder payloadBuilder) {
        super(name, campaignId, noteId, userId, customTags, workflowType, httpClient, payloadBuilder);
        this.sessionId = traceId; // Use traceId as sessionId for consistency
        this.observations = new ConcurrentHashMap<>();
        this.observationCount = new AtomicInteger(0);
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create trace payload with session support
                JsonObject tracePayload = payloadBuilder.buildTrace(
                    name, campaignId, noteId, userId, customTags, workflowType);
                tracePayload.addProperty("id", traceId);
                tracePayload.addProperty("sessionId", sessionId);
                tracePayload.addProperty("timestamp", startTime.toString());

                // Create ingestion event
                JsonObject event = payloadBuilder.buildIngestionEvent("trace-create", tracePayload);
                JsonObject batchPayload = payloadBuilder.buildIngestionBatch(event);

                // Send to Langfuse
                HttpResponse<String> response = httpClient.post("/api/public/ingestion", batchPayload);
                
                return httpClient.isIngestionSuccessful(response);
            } catch (Exception e) {
                System.err.println("Error initializing ArtefactRelationTrace: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> addObservation(Observation observation) {
        if (isFinalized) {
            return CompletableFuture.completedFuture(false);
        }
        
        // Store observation for tracking
        observations.put(observation.getObservationId(), observation);
        observationCount.incrementAndGet();
        
        return observation.sendToTrace(traceId).thenApply(success -> {
            if (success) {
                updateWorkflowState(observation);
            }
            return success;
        });
    }

    @Override
    public CompletableFuture<Boolean> updateTraceInput(JsonObject input) {
        if (isFinalized) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                this.input = input;
                
                JsonObject traceUpdatePayload = new JsonObject();
                traceUpdatePayload.addProperty("id", traceId);
                traceUpdatePayload.add("input", input);

                JsonObject event = payloadBuilder.buildIngestionEvent("trace-update", traceUpdatePayload);
                JsonObject batchPayload = payloadBuilder.buildIngestionBatch(event);

                HttpResponse<String> response = httpClient.post("/api/public/ingestion", batchPayload);
                return httpClient.isIngestionSuccessful(response);
            } catch (Exception e) {
                System.err.println("Error updating ArtefactRelationTrace input: " + e.getMessage());
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
                
                JsonObject traceUpdatePayload = new JsonObject();
                traceUpdatePayload.addProperty("id", traceId);
                traceUpdatePayload.add("output", output);

                JsonObject event = payloadBuilder.buildIngestionEvent("trace-update", traceUpdatePayload);
                JsonObject batchPayload = payloadBuilder.buildIngestionBatch(event);

                HttpResponse<String> response = httpClient.post("/api/public/ingestion", batchPayload);
                return httpClient.isIngestionSuccessful(response);
            } catch (Exception e) {
                System.err.println("Error finalizing ArtefactRelationTrace: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Updates workflow state based on observation type.
     * Currently simplified - can be extended when workflow tracking is needed.
     */
    private void updateWorkflowState(Observation observation) {
        String observationName = observation.getName().toLowerCase();
        
        // Log workflow progress
        if (observationName.contains("nae") || observationName.contains("artifact-extraction")) {
            System.out.println("NAE stage observation added to trace: " + traceId);
        } else if (observationName.contains("are") || observationName.contains("relationship-extraction")) {
            System.out.println("ARE stage observation added to trace: " + traceId);
        }
        
        // Future enhancement: track completion state and auto-finalize when both stages complete
        // naeCompleted = true; naeResults = observation.getOutput();
        // areCompleted = true; areResults = observation.getOutput();
    }
}
