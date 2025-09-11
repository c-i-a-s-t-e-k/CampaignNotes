package CampaignNotes.tracking.trace.traces;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;

import CampaignNotes.tracking.LangfuseHttpClient;
import CampaignNotes.tracking.trace.observations.Observation;
import CampaignNotes.tracking.trace.payload.PayloadBuilder;

/**
 * Abstract base class for all trace implementations.
 * 
 * This class provides the common functionality for trace lifecycle management,
 * including creation, observation tracking, and finalization. Based on Langfuse
 * documentation, a trace represents the top-level grouping of observations
 * for a particular workflow or session.
 */
public abstract class Trace {
    
    // Core trace properties
    protected final String traceId;
    protected final String name;
    protected final String campaignId;
    protected final String noteId;
    protected final String userId;
    protected final List<String> customTags;
    protected final String workflowType;
    protected final Instant startTime;
    
    // Dependencies for API communication
    protected final LangfuseHttpClient httpClient;
    protected final PayloadBuilder payloadBuilder;
    
    // Trace state
    protected JsonObject input;
    protected JsonObject output;
    protected boolean isFinalized = false;
    
    /**
     * Constructor for trace initialization.
     * 
     * @param name the trace name
     * @param campaignId the campaign UUID
     * @param noteId the note ID being processed
     * @param userId the user ID (optional)
     * @param customTags additional custom tags
     * @param workflowType type of workflow
     * @param httpClient HTTP client for API communication
     * @param payloadBuilder payload builder for JSON creation
     */
    protected Trace(String name, String campaignId, String noteId, String userId,
                   List<String> customTags, String workflowType,
                   LangfuseHttpClient httpClient, PayloadBuilder payloadBuilder) {
        this.traceId = UUID.randomUUID().toString();
        this.name = name;
        this.campaignId = campaignId;
        this.noteId = noteId;
        this.userId = userId;
        this.customTags = customTags;
        this.workflowType = workflowType;
        this.startTime = Instant.now();
        this.httpClient = httpClient;
        this.payloadBuilder = payloadBuilder;
    }
    
    // Getters
    public String getTraceId() { return traceId; }
    public String getName() { return name; }
    public String getCampaignId() { return campaignId; }
    public String getNoteId() { return noteId; }
    public boolean isFinalized() { return isFinalized; }
    
    /**
     * Adds an observation to this trace.
     * 
     * @param observation the observation to add
     * @return CompletableFuture<Boolean> indicating success or failure
     */
    public abstract CompletableFuture<Boolean> addObservation(Observation observation);
    
    /**
     * Updates the trace input.
     * 
     * @param input the input JSON object
     * @return CompletableFuture<Boolean> indicating success or failure
     */
    public abstract CompletableFuture<Boolean> updateTraceInput(JsonObject input);
    
    /**
     * Updates the trace output and finalizes the trace.
     * 
     * @param output the output JSON object
     * @return CompletableFuture<Boolean> indicating success or failure
     */
    public abstract CompletableFuture<Boolean> updateTraceOutput(JsonObject output);
    
    /**
     * Creates and sends the initial trace to Langfuse.
     * This method should be called after trace construction to register it with Langfuse.
     * 
     * @return CompletableFuture<Boolean> indicating success or failure
     */
    public abstract CompletableFuture<Boolean> initialize();
    
    /**
     * Finalizes the trace and performs cleanup.
     * Should be called when the trace is complete.
     */
    protected void finalizeTrace() {
        this.isFinalized = true;
    }
}
