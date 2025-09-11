package CampaignNotes.tracking.trace.payload;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import CampaignNotes.tracking.LangfuseModelService;
import model.ModelPricing;
import model.Note;

/**
 * Implementation of PayloadBuilder for Langfuse Ingestion API format.
 * 
 * This class extracts all payload creation logic from the monolithic LangfuseTracker,
 * focusing solely on JSON payload construction. It follows Spring Boot best practices:
 * - Single Responsibility: Only payload creation
 * - Dependency Injection: Uses constructor injection for dependencies
 * - Immutability: All methods are stateless and thread-safe
 * 
 * Extracted from LangfuseTracker (~410 lines of payload creation logic)
 */
public class IngestionPayloadBuilder implements PayloadBuilder {
    
    private final LangfuseModelService modelService;
    
    /**
     * Constructor with dependency injection.
     * 
     * @param modelService service for model pricing information
     */
    public IngestionPayloadBuilder(LangfuseModelService modelService) {
        this.modelService = modelService;
    }
    
    @Override
    public JsonObject buildEmbeddingGeneration(String traceId, Note note, String model, 
                                             String campaignId, int tokensUsed, long durationMs) {
        // Get model pricing for cost calculation
        ModelPricing pricing = modelService.getModelPricing(model);
        Double calculatedCost = null;
        if (pricing != null) {
            calculatedCost = pricing.calculateCost(tokensUsed);
        }
        
        String inputText = note.getFullTextForEmbedding();
        
        // Validation
        if (inputText == null || inputText.trim().isEmpty()) {
            throw new IllegalArgumentException("Input text for embedding cannot be null or empty");
        }
        
        JsonObject generationBody = new JsonObject();
        
        // Generate unique generation ID
        String generationId = UUID.randomUUID().toString();
        generationBody.addProperty("id", generationId);
        
        if (traceId != null && !traceId.isEmpty()) {
            generationBody.addProperty("traceId", traceId);
        }
        generationBody.addProperty("name", "note-embedding");
        generationBody.addProperty("model", model);
        generationBody.addProperty("input", inputText);
        generationBody.addProperty("output", "embedding-vector");
        generationBody.addProperty("startTime", Instant.now().minusMillis(durationMs).toString());
        generationBody.addProperty("endTime", Instant.now().toString());
        
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
        generationBody.add("tags", tagsArray);
        
        return generationBody;
    }
    
    @Override
    public JsonObject buildLLMGeneration(String traceId, String model, String prompt, 
                                       String response, int inputTokens, int outputTokens, 
                                       int totalTokens, long duration) {
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
        
        // Create metadata
        JsonObject metadata = new JsonObject();
        metadata.addProperty("component", "artifact-extraction");
        metadata.addProperty("operation_duration_ms", duration);
        metadata.addProperty("token_usage_source", "exact_from_provider");
        metadata.addProperty("original_prompt", prompt);
        metadata.addProperty("original_completion", response);
        
        // Use helper method to create observation body
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
    
    @Override
    public JsonObject buildStructuredLLMGeneration(String traceId, String model, String prompt,
                                                 String response, int inputTokens, int outputTokens,
                                                 int totalTokens, long duration, String componentName,
                                                 String stage) {
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
        
        // Use helper method to create observation body
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
        tagsArray.add("component:" + componentName);
        tagsArray.add("model:" + model);
        tagsArray.add("stage:" + stage);
        observationBody.add("tags", tagsArray);
        
        return observationBody;
    }
    
    @Override
    public JsonObject buildTrace(String traceName, String campaignId, String noteId, 
                               String userId, List<String> customTags, String workflowType) {
        JsonObject payload = new JsonObject();
        payload.addProperty("name", traceName);
        payload.addProperty("timestamp", Instant.now().toString());
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
    
    @Override
    public JsonObject buildTraceWithInput(String traceName, String campaignId, String noteId,
                                        String noteContent, List<String> categories, String userId,
                                        String workflowType) {
        // Generate trace ID
        String traceId = UUID.randomUUID().toString();
        
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
        if (userId != null) {
            metadata.addProperty("user_id", userId);
        }
        traceBody.add("metadata", metadata);
        
        // Add tags
        JsonArray tagsArray = new JsonArray();
        tagsArray.add("system:campaign-notes");
        tagsArray.add("workflow:artifact-extraction");
        tagsArray.add("campaign:" + campaignId.substring(0, 8));
        traceBody.add("tags", tagsArray);
        
        return traceBody;
    }
    
    @Override
    public JsonObject buildTraceOutput(String traceId, int artifactsCount, int relationshipsCount,
                                     List<String> artifactIds, String processingStatus, long durationMs) {
        // Create trace update body
        JsonObject traceUpdateBody = new JsonObject();
        traceUpdateBody.addProperty("id", traceId);
        
        // Create structured output
        JsonObject output = createTraceOutputStructure(artifactsCount, relationshipsCount, 
                                                     artifactIds, processingStatus, durationMs);
        traceUpdateBody.add("output", output);
        
        return traceUpdateBody;
    }
    
    @Override
    public JsonObject buildIngestionEvent(String eventType, JsonObject body) {
        JsonObject event = new JsonObject();
        event.addProperty("id", UUID.randomUUID().toString()); // Unique event ID for deduplication
        event.addProperty("type", eventType);
        event.addProperty("timestamp", Instant.now().toString());
        event.add("body", body);
        return event;
    }
    
    @Override
    public JsonObject buildIngestionBatch(JsonObject event) {
        JsonObject payload = new JsonObject();
        JsonArray batch = new JsonArray();
        batch.add(event);
        payload.add("batch", batch);
        return payload;
    }
    
    // Private helper methods
    
    /**
     * Creates structured input for trace creation containing note and campaign information.
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
     */
    private JsonObject createTraceOutputStructure(int artifactsCount, int relationshipsCount, 
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
     */
    private JsonObject createObservationBody(String traceId, String type, String name,
                                           JsonObject input, JsonObject output,
                                           String model, JsonObject usage,
                                           long durationMs, JsonObject metadata) {
        JsonObject observationBody = new JsonObject();
        
        // Generate unique observation ID
        String observationId = UUID.randomUUID().toString();
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
        observationBody.addProperty("startTime", Instant.now().minusMillis(durationMs).toString());
        observationBody.addProperty("endTime", Instant.now().toString());
        
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
     * Creates structured input for LLM generation based on component type.
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
