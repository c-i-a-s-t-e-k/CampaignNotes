package CampaignNotes.tracking;

import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
     * @param model the model used (e.g., "o3-mini")
     * @param prompt the input prompt sent to the model
     * @param response the response received from the model
     * @param tokens number of tokens used
     * @param duration duration of the operation in milliseconds
     * @return true if tracked successfully, false otherwise
     */
    public boolean trackLLMGeneration(String traceId, String model, String prompt, String response, int tokens, long duration) {
        try {
            // Create observation body for generation
            JsonObject observationBody = createLLMGenerationBody(traceId, model, prompt, response, tokens, duration);
            
            // Create the ingestion event envelope with observation-create type
            JsonObject event = createIngestionEvent("observation-create", observationBody);
            
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
     * Tracks an LLM generation with separate input/output token counts.
     * 
     * @param traceId the trace ID to associate this generation with
     * @param model the model used (e.g., "o3-mini")
     * @param prompt the input prompt sent to the model
     * @param response the response received from the model
     * @param inputTokens exact input token count
     * @param outputTokens exact output token count
     * @param totalTokens exact total token count
     * @param duration duration of the operation in milliseconds
     * @return true if tracked successfully, false otherwise
     */
    public boolean trackLLMGeneration(String traceId, String model, String prompt, String response, int inputTokens, int outputTokens, int totalTokens, long duration) {
        try {
            // Create observation body for generation
            JsonObject observationBody = createLLMGenerationBody(traceId, model, prompt, response, inputTokens, outputTokens, totalTokens, duration);
            
            // Create the ingestion event envelope with observation-create type
            JsonObject event = createIngestionEvent("observation-create", observationBody);
            
            // Create the ingestion batch payload
            JsonObject payload = createIngestionBatch(event);
            
            HttpResponse<String> response1 = httpClient.post("/api/public/ingestion", payload);
            
            if (httpClient.isIngestionSuccessful(response1)) {
                System.out.println("LLM generation tracked successfully in Langfuse. Model: " + model + ", Tokens: " + totalTokens);
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
     * Tracks an LLM generation with component identification (NAE/ARE).
     * 
     * @param traceId the trace ID to associate this generation with
     * @param model the model used (e.g., "o3-mini")
     * @param prompt the input prompt sent to the model
     * @param response the response received from the model
     * @param inputTokens exact input token count
     * @param outputTokens exact output token count
     * @param totalTokens exact total token count
     * @param duration duration of the operation in milliseconds
     * @param componentName name identifying the component (e.g., "nae-generation", "are-generation")
     * @param stage processing stage (e.g., "artifact-extraction", "relationship-extraction")
     * @return true if tracked successfully, false otherwise
     */
    public boolean trackLLMGeneration(String traceId, String model, String prompt, String response, 
                                    int inputTokens, int outputTokens, int totalTokens, long duration,
                                    String componentName, String stage) {
        try {
            // Create structured input/output
            JsonObject input = createStructuredInput(prompt, componentName);
            JsonObject output = createStructuredOutput(response, componentName);
            
            // Create usage information with exact values
            JsonObject usage = new JsonObject();
            usage.addProperty("promptTokens", inputTokens);
            usage.addProperty("completionTokens", outputTokens);
            usage.addProperty("totalTokens", totalTokens);
            
            // Create metadata
            JsonObject metadata = new JsonObject();
            metadata.addProperty("component", "artifact-extraction");
            metadata.addProperty("stage", stage);
            metadata.addProperty("operation_duration_ms", duration);
            metadata.addProperty("token_usage_source", "exact_from_provider");
            
            // Use createObservationBody helper
            JsonObject observationBody = createObservationBody(
                traceId,
                "generation",
                componentName,
                input,
                output,
                model,
                usage,
                duration,
                metadata
            );
            
            // Add tags
            JsonArray tagsArray = new JsonArray();
            tagsArray.add("system:campaign-notes");
            tagsArray.add("component:" + (componentName.contains("nae") ? "nae" : "are"));
            tagsArray.add("model:" + model);
            tagsArray.add("stage:" + stage);
            observationBody.add("tags", tagsArray);
            
            // Create the ingestion event envelope
            JsonObject event = createIngestionEvent("observation-create", observationBody);
            
            // Create the ingestion batch payload
            JsonObject payload = createIngestionBatch(event);
            
            HttpResponse<String> response1 = httpClient.post("/api/public/ingestion", payload);
            
            if (httpClient.isIngestionSuccessful(response1)) {
                System.out.println("LLM generation (" + componentName + ") tracked successfully in Langfuse. Model: " + model + ", Tokens: " + totalTokens);
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
     * This method is deprecated - use createTraceWithInput instead.
     * 
     * @param sessionName name of the session (e.g., "artifact-extraction")
     * @param campaignId the campaign UUID
     * @param noteId the note ID being processed
     * @return trace ID for linking related generations, or null if failed
     * @deprecated Use createTraceWithInput for better input/output tracking
     */
    @Deprecated
    public String trackArtifactExtractionWorkflow(String sessionName, String campaignId, String noteId) {
        // Delegate to the new method with minimal information
        return createTraceWithInput(sessionName, campaignId, noteId, "", null, "ai-powered-extraction");
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
     * Creates observation body for LLM generation operations.
     * Uses observation-create event with type "generation" for Langfuse compatibility.
     * 
     * @param traceId the trace to link this generation to
     * @param model the AI model used
     * @param prompt the input prompt
     * @param response the model's response
     * @param tokens total token count
     * @param duration duration in milliseconds
     * @return JsonObject formatted as observation for ingestion
     */
    private JsonObject createLLMGenerationBody(String traceId, String model, String prompt, String response, int tokens, long duration) {
        // Create structured input/output
        JsonObject input = new JsonObject();
        input.addProperty("text", prompt);
        
        JsonObject output = new JsonObject();
        output.addProperty("text", response);
        
        // Create usage information
        JsonObject usage = new JsonObject();
        usage.addProperty("promptTokens", tokens / 2); // Backward-compatible rough estimation
        usage.addProperty("completionTokens", tokens / 2);
        usage.addProperty("totalTokens", tokens);
        
        // Create metadata with original prompt/completion for backward compatibility
        JsonObject metadata = new JsonObject();
        metadata.addProperty("component", "artifact-extraction");
        metadata.addProperty("operation_duration_ms", duration);
        metadata.addProperty("token_usage_source", "estimated");
        metadata.addProperty("original_prompt", prompt);
        metadata.addProperty("original_completion", response);
        
        // Use createObservationBody helper
        JsonObject observationBody = createObservationBody(
            traceId,
            "generation",
            "llm-generation",
            input,
            output,
            model,
            usage,
            duration,
            metadata
        );
        
        // Add tags
        JsonArray tagsArray = new JsonArray();
        tagsArray.add("system:campaign-notes");
        tagsArray.add("component:llm-generation");
        tagsArray.add("model:" + model);
        observationBody.add("tags", tagsArray);
        
        return observationBody;
    }

    /**
     * Creates observation body for LLM generation with exact token usage values.
     * Uses observation-create event with type "generation" for Langfuse compatibility.
     * 
     * @param traceId the trace to link this generation to
     * @param model the AI model used
     * @param prompt the input prompt
     * @param response the model's response
     * @param inputTokens exact input token count
     * @param outputTokens exact output token count
     * @param totalTokens exact total token count
     * @param duration duration in milliseconds
     * @return JsonObject formatted as observation for ingestion
     */
    private JsonObject createLLMGenerationBody(String traceId, String model, String prompt, String response, int inputTokens, int outputTokens, int totalTokens, long duration) {
        // Create structured input/output
        JsonObject input = new JsonObject();
        input.addProperty("text", prompt);
        
        JsonObject output = new JsonObject();
        output.addProperty("text", response);
        
        // Create usage information with exact values
        JsonObject usage = new JsonObject();
        usage.addProperty("promptTokens", inputTokens);
        usage.addProperty("completionTokens", outputTokens);
        usage.addProperty("totalTokens", totalTokens);
        
        // Create metadata with original prompt/completion for backward compatibility
        JsonObject metadata = new JsonObject();
        metadata.addProperty("component", "artifact-extraction");
        metadata.addProperty("operation_duration_ms", duration);
        metadata.addProperty("token_usage_source", "exact_from_provider");
        metadata.addProperty("original_prompt", prompt);
        metadata.addProperty("original_completion", response);
        
        // Use createObservationBody helper
        JsonObject observationBody = createObservationBody(
            traceId,
            "generation",
            "llm-generation",
            input,
            output,
            model,
            usage,
            duration,
            metadata
        );
        
        // Add tags
        JsonArray tagsArray = new JsonArray();
        tagsArray.add("system:campaign-notes");
        tagsArray.add("component:llm-generation");
        tagsArray.add("model:" + model);
        observationBody.add("tags", tagsArray);
        
        return observationBody;
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
    
    // New helper methods for structured trace input/output
    
    /**
     * Creates structured input for trace creation containing note and campaign information.
     * 
     * @param noteId the ID of the note being processed
     * @param campaignId the campaign UUID
     * @param notePreview preview of the note content (first 500 chars)
     * @param categories list of available categories for artifact extraction
     * @return JsonObject with structured input data
     */
    private JsonObject createTraceInput(String noteId, String campaignId, String notePreview, List<String> categories) {
        JsonObject input = new JsonObject();
        input.addProperty("campaign_id", campaignId);
        input.addProperty("note_id", noteId);
        input.addProperty("note_preview", notePreview);
        
        JsonArray categoriesArray = new JsonArray();
        if (categories != null) {
            for (String category : categories) {
                categoriesArray.add(category);
            }
        }
        input.add("categories", categoriesArray);
        
        return input;
    }
    
    /**
     * Creates structured output for trace update with extraction results.
     * 
     * @param artifactsCount number of artifacts extracted
     * @param relationshipsCount number of relationships found
     * @param artifactIds list of artifact IDs created
     * @param processingStatus success/failure status
     * @param durationMs total processing duration
     * @return JsonObject with structured output data
     */
    private JsonObject createTraceOutput(int artifactsCount, int relationshipsCount, 
                                       List<String> artifactIds, String processingStatus, long durationMs) {
        JsonObject output = new JsonObject();
        output.addProperty("artifacts_count", artifactsCount);
        output.addProperty("relationships_count", relationshipsCount);
        
        JsonArray artifactIdsArray = new JsonArray();
        if (artifactIds != null) {
            for (String id : artifactIds) {
                artifactIdsArray.add(id);
            }
        }
        output.add("artifact_ids", artifactIdsArray);
        
        output.addProperty("processing_status", processingStatus);
        output.addProperty("total_duration_ms", durationMs);
        
        return output;
    }
    
    /**
     * Creates observation body for generation events with proper structure.
     * 
     * @param traceId the trace to link this observation to
     * @param type observation type (e.g., "generation")
     * @param name descriptive name for the observation
     * @param input structured input data
     * @param output structured output data
     * @param model the AI model used
     * @param usage token usage information
     * @param durationMs duration in milliseconds
     * @param metadata additional metadata
     * @return JsonObject ready for ingestion API
     */
    private JsonObject createObservationBody(String traceId, String type, String name,
                                           JsonObject input, JsonObject output,
                                           String model, JsonObject usage,
                                           long durationMs, JsonObject metadata) {
        JsonObject observationBody = new JsonObject();
        
        // Generate unique observation ID
        String observationId = java.util.UUID.randomUUID().toString();
        observationBody.addProperty("id", observationId);
        
        if (traceId != null && !traceId.isEmpty()) {
            observationBody.addProperty("traceId", traceId);
        }
        observationBody.addProperty("type", type);
        observationBody.addProperty("name", name);
        
        if (model != null) {
            observationBody.addProperty("model", model);
        }
        
        // Set timestamps
        observationBody.addProperty("startTime", java.time.Instant.now().minusMillis(durationMs).toString());
        observationBody.addProperty("endTime", java.time.Instant.now().toString());
        
        // Add input/output
        if (input != null) {
            observationBody.add("input", input);
        }
        if (output != null) {
            observationBody.add("output", output);
        }
        
        // Add usage if provided
        if (usage != null) {
            observationBody.add("usage", usage);
        }
        
        // Add metadata if provided
        if (metadata != null) {
            observationBody.add("metadata", metadata);
        }
        
        return observationBody;
    }
    
    /**
     * Creates a trace through ingestion API with structured input/output support.
     * 
     * @param traceName name of the trace
     * @param campaignId campaign UUID
     * @param noteId note ID being processed
     * @param noteContent full note content for preview generation
     * @param categories list of available categories
     * @param workflowType type of workflow (e.g., "ai-powered-extraction")
     * @return trace ID if successful, null otherwise
     */
    public String createTraceWithInput(String traceName, String campaignId, String noteId,
                                     String noteContent, List<String> categories, String workflowType) {
        try {
            // Generate trace ID
            String traceId = java.util.UUID.randomUUID().toString();
            
            // Create trace body
            JsonObject traceBody = new JsonObject();
            traceBody.addProperty("id", traceId);
            traceBody.addProperty("name", traceName);
            
            // Create structured input
            String notePreview = noteContent != null && noteContent.length() > 500 
                ? noteContent.substring(0, 500) + "..." 
                : noteContent;
            JsonObject input = createTraceInput(noteId, campaignId, notePreview, categories);
            traceBody.add("input", input);
            
            // Add metadata
            JsonObject metadata = new JsonObject();
            metadata.addProperty("workflow_type", workflowType);
            metadata.addProperty("campaign_id", campaignId);
            metadata.addProperty("note_id", noteId);
            traceBody.add("metadata", metadata);
            
            // Add tags
            JsonArray tagsArray = new JsonArray();
            tagsArray.add("system:campaign-notes");
            tagsArray.add("workflow:artifact-extraction");
            tagsArray.add("campaign:" + campaignId.substring(0, 8));
            traceBody.add("tags", tagsArray);
            
            // Create ingestion event
            JsonObject event = createIngestionEvent("trace-create", traceBody);
            
            // Create batch payload
            JsonObject payload = createIngestionBatch(event);
            
            HttpResponse<String> response = httpClient.post("/api/public/ingestion", payload);
            
            if (httpClient.isIngestionSuccessful(response)) {
                System.out.println("Trace created successfully with ID: " + traceId);
                return traceId;
            } else {
                System.err.println("Failed to create trace. Status: " + response.statusCode() + 
                                 ", Response: " + response.body());
                return null;
            }
            
        } catch (Exception e) {
            System.err.println("Error creating trace with input: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Updates an existing trace with output results.
     * 
     * @param traceId the trace ID to update
     * @param artifactsCount number of artifacts extracted
     * @param relationshipsCount number of relationships found
     * @param artifactIds list of artifact IDs created
     * @param processingStatus final status (success/failure)
     * @param durationMs total processing duration
     * @return true if update successful, false otherwise
     */
    public boolean updateTraceOutput(String traceId, int artifactsCount, int relationshipsCount,
                                   List<String> artifactIds, String processingStatus, long durationMs) {
        try {
            // Create trace update body
            JsonObject traceUpdateBody = new JsonObject();
            traceUpdateBody.addProperty("id", traceId);
            
            // Create structured output
            JsonObject output = createTraceOutput(artifactsCount, relationshipsCount, 
                                                artifactIds, processingStatus, durationMs);
            traceUpdateBody.add("output", output);
            
            // Create ingestion event for trace update
            JsonObject event = createIngestionEvent("trace-create", traceUpdateBody);
            
            // Create batch payload
            JsonObject payload = createIngestionBatch(event);
            
            HttpResponse<String> response = httpClient.post("/api/public/ingestion", payload);
            
            if (httpClient.isIngestionSuccessful(response)) {
                System.out.println("Trace updated successfully with output results");
                return true;
            } else {
                System.err.println("Failed to update trace. Status: " + response.statusCode() + 
                                 ", Response: " + response.body());
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("Error updating trace output: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Creates structured input for LLM generation based on component type.
     * 
     * @param prompt the raw prompt text
     * @param componentName the component name (nae-generation or are-generation)
     * @return structured input JsonObject
     */
    private JsonObject createStructuredInput(String prompt, String componentName) {
        JsonObject input = new JsonObject();
        
        try {
            // Try to parse the prompt as JSON to extract structured data
            JsonObject promptJson = JsonParser.parseString(prompt).getAsJsonObject();
            
            if (componentName.contains("nae")) {
                // NAE input structure
                if (promptJson.has("note")) {
                    input.addProperty("user", prompt);
                } else {
                    input.addProperty("text", prompt);
                }
            } else if (componentName.contains("are")) {
                // ARE input structure
                if (promptJson.has("note") && promptJson.has("artefacts")) {
                    input.addProperty("user", prompt);
                    input.addProperty("artifacts_count", promptJson.getAsJsonArray("artefacts").size());
                } else {
                    input.addProperty("text", prompt);
                }
            } else {
                input.addProperty("text", prompt);
            }
        } catch (Exception e) {
            // If parsing fails, just use text format
            input.addProperty("text", prompt);
        }
        
        return input;
    }
    
    /**
     * Creates structured output for LLM generation based on component type.
     * 
     * @param response the raw response text
     * @param componentName the component name (nae-generation or are-generation)
     * @return structured output JsonObject
     */
    private JsonObject createStructuredOutput(String response, String componentName) {
        JsonObject output = new JsonObject();
        output.addProperty("text", response);
        
        try {
            // Try to parse the response as JSON for structured output
            JsonObject responseJson = JsonParser.parseString(response).getAsJsonObject();
            
            if (componentName.contains("nae")) {
                // NAE output structure
                if (responseJson.has("artefacts")) {
                    JsonObject parsed = new JsonObject();
                    parsed.add("artifacts", responseJson.getAsJsonArray("artefacts"));
                    output.add("parsed", parsed);
                } else if (responseJson.has("artifacts")) {
                    JsonObject parsed = new JsonObject();
                    parsed.add("artifacts", responseJson.getAsJsonArray("artifacts"));
                    output.add("parsed", parsed);
                }
            } else if (componentName.contains("are")) {
                // ARE output structure
                if (responseJson.has("relations")) {
                    JsonObject parsed = new JsonObject();
                    parsed.add("relationships", responseJson.getAsJsonArray("relations"));
                    output.add("parsed", parsed);
                } else if (responseJson.has("relationships")) {
                    JsonObject parsed = new JsonObject();
                    parsed.add("relationships", responseJson.getAsJsonArray("relationships"));
                    output.add("parsed", parsed);
                }
            }
        } catch (Exception e) {
            // If parsing fails, output already has text field
        }
        
        return output;
    }
}
