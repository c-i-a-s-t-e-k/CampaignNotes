package CampaignNotes.tracking;

import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import model.ModelPricing;
import model.Note;

/**
 * Service for tracking AI operations in Langfuse.
 * Handles creation of traces, generations, and other observability events
 * for different types of AI operations within the Campaign Notes system.
 * 
 * Responsibilities:
 * - Tracking embedding operations
 * - Tracking LLM generations
 * - Tracking note processing sessions
 * - Tracking artifact extraction workflows
 * - Creating and managing traces
 * - Payload creation and standardization
 * - Cost calculation integration
 */
public class LangfuseTracker {
    
    private final LangfuseHttpClient httpClient;
    private final LangfuseModelService modelService;
    
    /**
     * Constructor with dependencies.
     * 
     * @param httpClient the HTTP client for API communication
     * @param modelService the model service for pricing information
     */
    public LangfuseTracker(LangfuseHttpClient httpClient, LangfuseModelService modelService) {
        this.httpClient = httpClient;
        this.modelService = modelService;
    }
    
    /**
     * Tracks an embedding generation call to Langfuse with full note information.
     * Creates a generation entry with proper tagging for OpenAI embedding calls.
     * 
     * @param traceId ID of the parent trace to link this generation to
     * @param note the full note that was embedded
     * @param model the embedding model used
     * @param campaignId the campaign UUID
     * @param tokensUsed exact number of tokens consumed (from OpenAI API)
     * @param durationMs time taken in milliseconds
     * @return true if tracking was successful, false otherwise
     */
    public boolean trackEmbedding(String traceId, Note note, String model, String campaignId, 
                                 int tokensUsed, long durationMs) {
        return trackEmbedding(traceId, note, model, campaignId, tokensUsed, durationMs, Collections.emptyList());
    }
    
    /**
     * Tracks an embedding generation call to Langfuse with full note information and custom tags.
     * Creates a generation entry with proper tagging for OpenAI embedding calls.
     * 
     * @param traceId ID of the parent trace to link this generation to
     * @param note the full note that was embedded
     * @param model the embedding model used
     * @param campaignId the campaign UUID
     * @param tokensUsed exact number of tokens consumed (from OpenAI API)
     * @param durationMs time taken in milliseconds
     * @param customTags additional custom tags to include
     * @return true if tracking was successful, false otherwise
     */
    public boolean trackEmbedding(String traceId, Note note, String model, String campaignId, 
                                 int tokensUsed, long durationMs, List<String> customTags) {
        try {
            // Get model pricing for cost calculation
            ModelPricing pricing = modelService.getModelPricing(model);
            Double calculatedCost = null;
            if (pricing != null) {
                calculatedCost = pricing.calculateCost(tokensUsed);
            }
            
            String inputText = note.getFullTextForEmbedding();
            
            // Additional validation to ensure inputText is not null or empty
            if (inputText == null || inputText.trim().isEmpty()) {
                System.err.println("ERROR: Input text for embedding is null or empty!");
                System.err.println("Note details - Title: '" + note.getTitle() + "', Content: '" + note.getContent() + "'");
                return false;
            }
            
            // Create generation body using ingestion API format
            JsonObject generationBody = createEmbeddingGenerationBody(
                traceId, note, model, campaignId, tokensUsed, durationMs, inputText, calculatedCost, customTags);
            
            // Create the ingestion event envelope
            JsonObject event = createIngestionEvent("generation-create", generationBody);
            
            // Create the ingestion batch payload
            JsonObject payload = createIngestionBatch(event);
            
            HttpResponse<String> response = httpClient.post("/api/public/ingestion", payload);
            
            if (httpClient.isIngestionSuccessful(response)) {
                System.out.println("Embedding tracked successfully in Langfuse with " + tokensUsed + " tokens" +
                    (calculatedCost != null ? " and cost $" + String.format("%.6f", calculatedCost) : ""));
                return true;
            } else {
                System.err.println("Failed to track embedding in Langfuse. Status: " + response.statusCode() + 
                                 ", Response: " + response.body());
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("Error tracking embedding in Langfuse: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Tracks a note processing session to Langfuse.
     * Creates a trace for the entire note processing workflow.
     * 
     * @param sessionName name of the session (e.g., "note-processing")
     * @param campaignId the campaign UUID
     * @param noteId the note ID
     * @param userId user performing the action (if available)
     * @return trace ID for linking related generations, or null if failed
     */
    public String trackNoteProcessingSession(String sessionName, String campaignId, String noteId, String userId) {
        return trackNoteProcessingSession(sessionName, campaignId, noteId, userId, Collections.emptyList());
    }
    
    /**
     * Tracks a note processing session to Langfuse with custom tags.
     * Creates a trace for the entire note processing workflow.
     * 
     * @param sessionName name of the session (e.g., "note-processing")
     * @param campaignId the campaign UUID
     * @param noteId the note ID
     * @param userId user performing the action (if available)
     * @param customTags additional custom tags to include
     * @return trace ID for linking related generations, or null if failed
     */
    public String trackNoteProcessingSession(String sessionName, String campaignId, String noteId, String userId, List<String> customTags) {
        try {
            // Create trace payload
            JsonObject payload = createTracePayload(sessionName, campaignId, noteId, userId, customTags, "note-processing");
            
            HttpResponse<String> response = httpClient.post("/api/public/traces", payload);
            
            if (httpClient.isSuccessful(response)) {
                JsonObject responseJson = httpClient.parseJsonResponse(response);
                String traceId = responseJson.get("id").getAsString();
                System.out.println("Note processing session tracked in Langfuse with trace ID: " + traceId);
                return traceId;
            } else {
                System.err.println("Failed to track session in Langfuse. Status: " + response.statusCode());
                return null;
            }
            
        } catch (Exception e) {
            System.err.println("Error tracking session in Langfuse: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Tracks an LLM generation to Langfuse for observability.
     * Used for monitoring AI operations like artifact extraction.
     * 
     * @param traceId the trace ID to associate this generation with
     * @param model the model used (e.g., "o1-mini", "o1")
     * @param prompt the input prompt sent to the model
     * @param response the response received from the model
     * @param tokens number of tokens used
     * @param duration duration of the operation in milliseconds
     * @return true if tracked successfully, false otherwise
     */
    public boolean trackLLMGeneration(String traceId, String model, String prompt, String response, int tokens, long duration) {
        try {
            // Create generation body
            JsonObject generationBody = createLLMGenerationBody(traceId, model, prompt, response, tokens, duration);
            
            // Create the ingestion event envelope
            JsonObject event = createIngestionEvent("generation-create", generationBody);
            
            // Create the ingestion batch payload
            JsonObject payload = createIngestionBatch(event);
            
            HttpResponse<String> response1 = httpClient.post("/api/public/ingestion", payload);
            
            if (httpClient.isIngestionSuccessful(response1)) {
                System.out.println("LLM generation tracked successfully in Langfuse. Model: " + model + ", Tokens: " + tokens);
                return true;
            } else {
                System.err.println("Failed to track LLM generation in Langfuse. Status: " + response1.statusCode() + 
                                 ", Response: " + response1.body());
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("Error tracking LLM generation in Langfuse: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Tracks an artifact extraction workflow session to Langfuse.
     * Creates a trace specifically for artifact processing operations.
     * 
     * @param sessionName name of the session (e.g., "artifact-extraction")
     * @param campaignId the campaign UUID
     * @param noteId the note ID being processed
     * @return trace ID for linking related generations, or null if failed
     */
    public String trackArtifactExtractionWorkflow(String sessionName, String campaignId, String noteId) {
        try {
            // Create trace payload with artifact-specific metadata
            JsonObject payload = createTracePayload(sessionName, campaignId, noteId, null, null, "artifact-extraction");
            
            HttpResponse<String> response = httpClient.post("/api/public/traces", payload);
            
            if (httpClient.isSuccessful(response)) {
                JsonObject responseJson = httpClient.parseJsonResponse(response);
                String traceId = responseJson.get("id").getAsString();
                System.out.println("Artifact extraction workflow tracked in Langfuse with trace ID: " + traceId);
                return traceId;
            } else {
                System.err.println("Failed to track artifact extraction workflow in Langfuse. Status: " + response.statusCode());
                return null;
            }
            
        } catch (Exception e) {
            System.err.println("Error tracking artifact extraction workflow in Langfuse: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Retrieves a trace from Langfuse by its ID asynchronously.
     * Used for verification purposes, typically in testing scenarios.
     * 
     * @param traceId the ID of the trace to retrieve
     * @return CompletableFuture containing JsonObject with trace data, or null if not found
     * @throws TimeoutException if the request times out
     */
    public CompletableFuture<JsonObject> getTrace(String traceId) {
        String getTraceEndpoint = "/api/public/traces/" + traceId;
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<String> response = httpClient.get(getTraceEndpoint);
                
                if (httpClient.isSuccessful(response)) {
                    return httpClient.parseJsonResponse(response);
                } else if (response.statusCode() == 404) {
                    System.err.println("Trace not found: " + traceId);
                    return null;
                } else {
                    System.err.println("Failed to retrieve trace. Status: " + response.statusCode() + 
                                     ", Response: " + response.body());
                    return null;
                }
            } catch (Exception e) {
                if (e.getCause() instanceof java.net.http.HttpTimeoutException) {
                    throw new RuntimeException(new TimeoutException("Request timeout while retrieving trace: " + traceId));
                }
                System.err.println("Error retrieving trace from Langfuse: " + e.getMessage());
                return null;
            }
        });
    }
    
    // Private helper methods for payload creation
    
    /**
     * Creates generation body for embedding operations.
     */
    private JsonObject createEmbeddingGenerationBody(String traceId, Note note, String model, String campaignId,
                                                   int tokensUsed, long durationMs, String inputText, 
                                                   Double calculatedCost, List<String> customTags) {
        JsonObject generationBody = new JsonObject();
        
        // Generate unique generation ID
        String generationId = java.util.UUID.randomUUID().toString();
        generationBody.addProperty("id", generationId);
        
        if (traceId != null && !traceId.isEmpty()) {
            generationBody.addProperty("traceId", traceId);
        }
        generationBody.addProperty("name", "note-embedding");
        generationBody.addProperty("model", model);
        generationBody.addProperty("input", inputText);
        generationBody.addProperty("output", "embedding-vector");
        generationBody.addProperty("startTime", java.time.Instant.now().minusMillis(durationMs).toString());
        generationBody.addProperty("endTime", java.time.Instant.now().toString());
        
        // Add usage information with exact token count
        JsonObject usage = new JsonObject();
        usage.addProperty("input", tokensUsed);
        usage.addProperty("output", 0);
        usage.addProperty("total", tokensUsed);
        usage.addProperty("unit", "TOKENS");
        generationBody.add("usage", usage);
        
        // Add cost information if available
        if (calculatedCost != null) {
            generationBody.addProperty("cost", calculatedCost);
        }
        
        // Add metadata with comprehensive note and campaign information
        JsonObject metadata = new JsonObject();
        metadata.addProperty("campaign_id", campaignId);
        metadata.addProperty("note_id", note.getId());
        metadata.addProperty("note_title", note.getTitle());
        metadata.addProperty("note_content_length", note.getContent().length());
        metadata.addProperty("note_is_override", note.isOverride());
        metadata.addProperty("system_component", "note-embedding");
        metadata.addProperty("operation_type", "text-embedding");
        metadata.addProperty("exact_tokens_used", tokensUsed);
        if (calculatedCost != null) {
            metadata.addProperty("calculated_cost_usd", calculatedCost);
            metadata.addProperty("pricing_source", "langfuse_models_api");
        }
        generationBody.add("metadata", metadata);
        
        // Add tags for filtering and organization
        JsonArray tagsArray = new JsonArray();
        tagsArray.add("system:campaign-notes");
        tagsArray.add("component:embedding");
        tagsArray.add("model:" + model);
        tagsArray.add("campaign:" + campaignId.substring(0, 8)); // First 8 chars of UUID for grouping
        if (note.isOverride()) {
            tagsArray.add("note-type:override");
        } else {
            tagsArray.add("note-type:standard");
        }
        
        // Add custom tags if provided
        if (customTags != null) {
            for (String customTag : customTags) {
                tagsArray.add(customTag);
            }
        }
        generationBody.add("tags", tagsArray);
        
        return generationBody;
    }
    
    /**
     * Creates generation body for LLM operations.
     */
    private JsonObject createLLMGenerationBody(String traceId, String model, String prompt, String response, int tokens, long duration) {
        JsonObject generationBody = new JsonObject();
        generationBody.addProperty("id", java.util.UUID.randomUUID().toString());
        generationBody.addProperty("traceId", traceId);
        generationBody.addProperty("name", "llm-generation");
        generationBody.addProperty("startTime", java.time.Instant.now().minusMillis(duration).toString());
        generationBody.addProperty("endTime", java.time.Instant.now().toString());
        generationBody.addProperty("model", model);
        generationBody.addProperty("modelParameters", "{}"); // Empty for now
        generationBody.addProperty("prompt", prompt);
        generationBody.addProperty("completion", response);
        
        // Add usage information
        JsonObject usage = new JsonObject();
        usage.addProperty("promptTokens", tokens / 2); // Rough estimation
        usage.addProperty("completionTokens", tokens / 2);
        usage.addProperty("totalTokens", tokens);
        generationBody.add("usage", usage);
        
        // Add metadata
        JsonObject metadata = new JsonObject();
        metadata.addProperty("component", "artifact-extraction");
        metadata.addProperty("operation_duration_ms", duration);
        generationBody.add("metadata", metadata);
        
        // Add tags
        JsonArray tagsArray = new JsonArray();
        tagsArray.add("system:campaign-notes");
        tagsArray.add("component:llm-generation");
        tagsArray.add("model:" + model);
        generationBody.add("tags", tagsArray);
        
        return generationBody;
    }
    
    /**
     * Creates trace payload for different workflow types.
     */
    private JsonObject createTracePayload(String sessionName, String campaignId, String noteId, String userId, 
                                         List<String> customTags, String workflowType) {
        JsonObject payload = new JsonObject();
        payload.addProperty("name", sessionName);
        payload.addProperty("timestamp", java.time.Instant.now().toString());
        if (userId != null) {
            payload.addProperty("userId", userId);
        }
        
        // Add metadata
        JsonObject metadata = new JsonObject();
        metadata.addProperty("campaign_id", campaignId);
        metadata.addProperty("note_id", noteId);
        metadata.addProperty("system_component", workflowType);
        if (userId != null) {
            metadata.addProperty("user_id", userId);
        }
        
        // Add workflow-specific metadata
        if ("artifact-extraction".equals(workflowType)) {
            metadata.addProperty("workflow_type", "ai-powered-extraction");
        }
        
        payload.add("metadata", metadata);
        
        // Add tags
        JsonArray tagsArray = new JsonArray();
        tagsArray.add("system:campaign-notes");
        
        if ("note-processing".equals(workflowType)) {
            tagsArray.add("workflow:note-processing");
        } else if ("artifact-extraction".equals(workflowType)) {
            tagsArray.add("workflow:artifact-extraction");
            tagsArray.add("ai-operation:artifact-identification");
        }
        
        tagsArray.add("campaign:" + campaignId.substring(0, 8));
        
        // Add custom tags if provided
        if (customTags != null) {
            for (String customTag : customTags) {
                tagsArray.add(customTag);
            }
        }
        payload.add("tags", tagsArray);
        
        return payload;
    }
    
    /**
     * Creates ingestion event envelope.
     */
    private JsonObject createIngestionEvent(String eventType, JsonObject body) {
        JsonObject event = new JsonObject();
        event.addProperty("id", java.util.UUID.randomUUID().toString()); // Unique event ID for deduplication
        event.addProperty("type", eventType);
        event.addProperty("timestamp", java.time.Instant.now().toString());
        event.add("body", body);
        return event;
    }
    
    /**
     * Creates ingestion batch payload.
     */
    private JsonObject createIngestionBatch(JsonObject event) {
        JsonObject payload = new JsonObject();
        JsonArray batch = new JsonArray();
        batch.add(event);
        payload.add("batch", batch);
        return payload;
    }
}
