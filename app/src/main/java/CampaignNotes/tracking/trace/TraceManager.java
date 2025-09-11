package CampaignNotes.tracking.trace;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;

import CampaignNotes.tracking.LangfuseHttpClient;
import CampaignNotes.tracking.trace.observations.Observation;
import CampaignNotes.tracking.trace.payload.PayloadBuilder;
import CampaignNotes.tracking.trace.traces.ArtefactRelationTrace;
import CampaignNotes.tracking.trace.traces.EmbedingTrace;
import CampaignNotes.tracking.trace.traces.Trace;
import CampaignNotes.tracking.trace.traces.TraceType;

/**
 * Clean public API for Langfuse tracking operations with strict type safety.
 * 
 * This class serves as the main entry point for all tracking operations,
 * replacing the monolithic LangfuseTracker with a focused, maintainable design.
 * It follows Spring Boot best practices:
 * - Single Responsibility: Only high-level tracking coordination
 * - Dependency Injection: Uses constructor injection
 * - Composition over Inheritance: Delegates to specialized components
 * - Type Safety: All trace creation requires TraceType enum
 * - Clean API: Provides intuitive method signatures
 * 
 * Key Design Principles:
 * - All trace creation methods require TraceType enum for type safety
 * - Factory pattern via createTraceByType() for proper trace instantiation
 * - Automatic workflow type determination based on TraceType
 * - Asynchronous operations with CompletableFuture
 * 
 * The TraceManager coordinates between PayloadBuilder for JSON creation
 * and LangfuseHttpClient for HTTP communication, maintaining separation of concerns.
 */
public class TraceManager {
    
    private final LangfuseHttpClient httpClient;
    private final PayloadBuilder payloadBuilder;
    
    /**
     * Constructor with dependency injection.
     * 
     * @param httpClient HTTP client for API communication
     * @param payloadBuilder payload builder for JSON creation
     */
    public TraceManager(LangfuseHttpClient httpClient, PayloadBuilder payloadBuilder) {
        this.httpClient = httpClient;
        this.payloadBuilder = payloadBuilder;
    }

    /**
     * Tracks a new observation by adding it to the specified trace.
     * 
     * @param trace the trace to add the observation to
     * @param observation the observation to add
     * @return CompletableFuture<Boolean> indicating success or failure
     */
    public CompletableFuture<Boolean> trackNewObservation(Trace trace, Observation observation) {
        return trace.addObservation(observation);
    }
    

    
    /**
     * Creates a trace with structured input for workflow tracking.
     * 
     * @param traceName name of the trace
     * @param campaignId campaign UUID
     * @param noteId note ID being processed
     * @param noteContent full note content for preview generation
     * @param categories list of available categories
     * @param traceType type of trace to create (required enum)
     * @param userId user ID (optional)
     * @return Trace instance if successful, null otherwise
     */
    public Trace createTraceWithInput(String traceName, String campaignId, String noteId,
                                     String noteContent, List<String> categories, TraceType traceType, String userId) {
        try {
            // Determine workflow type based on trace type
            String workflowType = (traceType == TraceType.ARTIFACT_RELATION_TRACE) ? 
                "ai-powered-extraction" : "embedding-generation";
            
            Trace trace = createTraceByType(traceName, campaignId, noteId, userId, 
                Collections.emptyList(), workflowType, traceType);
            
            if (trace != null) {
                // Initialize the trace
                trace.initialize().join(); // Wait for initialization
                
                // Set structured input
                JsonObject inputPayload = payloadBuilder.buildTraceWithInput(
                    traceName, campaignId, noteId, noteContent, categories, userId, workflowType);
                
                trace.updateTraceInput(inputPayload.getAsJsonObject("input")).join();
            }
            
            return trace;
        } catch (Exception e) {
            System.err.println("Error creating trace with input: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Updates an existing trace with output results.
     * 
     * @param trace the trace to update
     * @param artifactsCount number of artifacts extracted
     * @param relationshipsCount number of relationships found
     * @param artifactIds list of artifact IDs created
     * @param processingStatus final status (success/failure)
     * @param durationMs total processing duration
     * @return CompletableFuture<Boolean> indicating success or failure
     */
    public CompletableFuture<Boolean> updateTraceOutput(Trace trace, int artifactsCount, 
                                                       int relationshipsCount, List<String> artifactIds, 
                                                       String processingStatus, long durationMs) {
        JsonObject outputPayload = payloadBuilder.buildTraceOutput(
            trace.getTraceId(), artifactsCount, relationshipsCount, 
            artifactIds, processingStatus, durationMs);
        
        return trace.updateTraceOutput(outputPayload);
    }
    
    
    /**
     * Factory method to create traces by type.
     * 
     * @param name trace name
     * @param campaignId campaign UUID
     * @param noteId note ID
     * @param userId user ID (optional)
     * @param customTags custom tags
     * @param workflowType workflow type
     * @param traceType trace type
     * @return Trace instance based on type
     */
    public Trace createTraceByType(String name, String campaignId, String noteId, String userId,
                                  List<String> customTags, String workflowType, TraceType traceType) {
        return switch (traceType) {
            case EMBEDDING_TRACE -> new EmbedingTrace(name, campaignId, noteId, userId, customTags, 
                workflowType, httpClient, payloadBuilder);
            
            case ARTIFACT_RELATION_TRACE -> new ArtefactRelationTrace(name, campaignId, noteId, userId, customTags, 
                workflowType, httpClient, payloadBuilder);
            
            default -> throw new IllegalArgumentException("Unsupported trace type: " + traceType);
        };
    }
    
    /**
     * Gets the HTTP client for direct observation creation.
     * 
     * @return LangfuseHttpClient instance
     */
    public LangfuseHttpClient getHttpClient() {
        return httpClient;
    }
    
    /**
     * Gets the payload builder for direct observation creation.
     * 
     * @return PayloadBuilder instance
     */
    public PayloadBuilder getPayloadBuilder() {
        return payloadBuilder;
    }
}
