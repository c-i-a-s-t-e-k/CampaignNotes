package CampaignNotes.tracking.trace.payload;

import java.util.List;

import com.google.gson.JsonObject;

import model.Note;

/**
 * Interface for building Langfuse payload objects.
 * 
 * This interface defines the contract for creating various types of payloads
 * needed for Langfuse ingestion API. Implementations should focus on the
 * specific payload creation logic without handling HTTP communication.
 * 
 * Following Spring Boot best practices:
 * - Single Responsibility: Only payload creation
 * - Interface segregation: Focused on payload building operations
 * - Dependency inversion: Clients depend on abstraction, not implementation
 */
public interface PayloadBuilder {
    
    /**
     * Builds a payload for embedding generation tracking.
     * 
     * @param traceId the trace ID to associate with this generation
     * @param note the note that was embedded
     * @param model the embedding model used
     * @param campaignId the campaign UUID
     * @param tokensUsed exact number of tokens consumed
     * @param durationMs time taken in milliseconds
     * @return JsonObject containing the embedding generation payload
     */
    JsonObject buildEmbeddingGeneration(String traceId, Note note, String model, 
                                       String campaignId, int tokensUsed, long durationMs);
    
    /**
     * Builds a payload for LLM generation tracking with separate token counts.
     * 
     * @param traceId the trace ID to associate with this generation
     * @param model the model used (e.g., "o3-mini")
     * @param prompt the input prompt sent to the model
     * @param response the response received from the model
     * @param inputTokens exact input token count
     * @param outputTokens exact output token count
     * @param totalTokens exact total token count
     * @param duration duration of the operation in milliseconds
     * @return JsonObject containing the LLM generation payload
     */
    JsonObject buildLLMGeneration(String traceId, String model, String prompt, 
                                 String response, int inputTokens, int outputTokens, 
                                 int totalTokens, long duration);
    
    /**
     * Builds a payload for LLM generation with component identification.
     * 
     * @param traceId the trace ID to associate with this generation
     * @param model the model used
     * @param prompt the input prompt
     * @param response the model response
     * @param inputTokens exact input token count
     * @param outputTokens exact output token count
     * @param totalTokens exact total token count
     * @param duration duration in milliseconds
     * @param componentName component identifier (e.g., "nae-generation")
     * @param stage processing stage (e.g., "artifact-extraction")
     * @return JsonObject containing the structured LLM generation payload
     */
    JsonObject buildStructuredLLMGeneration(String traceId, String model, String prompt,
                                           String response, int inputTokens, int outputTokens,
                                           int totalTokens, long duration, String componentName,
                                           String stage);
    
    /**
     * Builds a payload for trace creation.
     * 
     * @param traceName the name for the trace
     * @param campaignId the campaign UUID
     * @param noteId the note ID
     * @param userId the user ID
     * @param customTags additional custom tags to include
     * @param workflowType type of workflow (e.g., "ai-powered-extraction")
     * @return JsonObject containing the trace payload
     */
    JsonObject buildTrace(String traceName, String campaignId, String noteId, 
                         String userId, List<String> customTags, String workflowType);
    
    /**
     * Builds a payload for trace with structured input.
     * 
     * @param traceName the name for the trace
     * @param campaignId the campaign UUID
     * @param noteId the note ID
     * @param noteContent the note content for preview
     * @param categories note categories
     * @param userId the user ID
     * @param workflowType type of workflow
     * @return JsonObject containing the trace with input payload
     */
    JsonObject buildTraceWithInput(String traceName, String campaignId, String noteId,
                                  String noteContent, List<String> categories, String userId,
                                  String workflowType);
    
    /**
     * Builds a payload for trace output update.
     * 
     * @param traceId the trace ID to update
     * @param artifactsCount number of artifacts extracted
     * @param relationshipsCount number of relationships found
     * @param artifactIds list of artifact IDs
     * @param processingStatus processing status (e.g., "success")
     * @param durationMs total processing duration
     * @return JsonObject containing the trace output update payload
     */
    JsonObject buildTraceOutput(String traceId, int artifactsCount, int relationshipsCount,
                               List<String> artifactIds, String processingStatus, long durationMs);
    
    /**
     * Builds an ingestion event envelope.
     * 
     * @param eventType the type of event (e.g., "generation-create")
     * @param body the event body
     * @return JsonObject containing the ingestion event
     */
    JsonObject buildIngestionEvent(String eventType, JsonObject body);
    
    /**
     * Builds an ingestion batch wrapper.
     * 
     * @param event the event to wrap in a batch
     * @return JsonObject containing the ingestion batch
     */
    JsonObject buildIngestionBatch(JsonObject event);
}
