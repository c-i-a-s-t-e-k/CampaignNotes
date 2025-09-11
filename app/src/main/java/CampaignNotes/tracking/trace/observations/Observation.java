package CampaignNotes.tracking.trace.observations;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;

import CampaignNotes.tracking.LangfuseHttpClient;
import CampaignNotes.tracking.trace.payload.PayloadBuilder;

/**
 * Abstract base class for all observation implementations.
 * 
 * Based on Langfuse documentation, observations are the individual operations
 * that occur within a trace. They represent specific interactions with LLMs
 * or other AI services and contain the actual input/output data, timing,
 * and usage information.
 */
public abstract class Observation {
    
    // Core observation properties
    protected final String observationId;
    protected final String name;
    protected final ObservationType type;
    protected final Instant startTime;
    
    // Dependencies for API communication
    protected final LangfuseHttpClient httpClient;
    protected final PayloadBuilder payloadBuilder;
    
    // Observation data
    protected JsonObject input;
    protected JsonObject output;
    protected JsonObject metadata;
    protected String model;
    protected Long durationMs;
    protected Instant endTime;
    
    /**
     * Constructor for observation initialization.
     * 
     * @param name the observation name
     * @param type the observation type
     * @param httpClient HTTP client for API communication
     * @param payloadBuilder payload builder for JSON creation
     */
    protected Observation(String name, ObservationType type, 
                         LangfuseHttpClient httpClient, PayloadBuilder payloadBuilder) {
        this.observationId = UUID.randomUUID().toString();
        this.name = name;
        this.type = type;
        this.startTime = Instant.now();
        this.httpClient = httpClient;
        this.payloadBuilder = payloadBuilder;
    }
    
    // Getters
    public String getObservationId() { return observationId; }
    public String getName() { return name; }
    public ObservationType getType() { return type; }
    public JsonObject getInput() { return input; }
    public JsonObject getOutput() { return output; }
    public String getModel() { return model; }
    public Long getDurationMs() { return durationMs; }
    
    // Protected setters for subclasses
    protected void setInput(JsonObject input) { this.input = input; }
    protected void setOutput(JsonObject output) { this.output = output; }
    protected void setModel(String model) { this.model = model; }
    protected void setMetadata(JsonObject metadata) { this.metadata = metadata; }
    
    /**
     * Finalizes the observation by setting end time and calculating duration.
     */
    protected void finalizeObservation() {
        this.endTime = Instant.now();
        this.durationMs = this.endTime.toEpochMilli() - this.startTime.toEpochMilli();
    }
    
    /**
     * Sends this observation to Langfuse as part of the specified trace.
     * 
     * @param traceId the trace ID to associate this observation with
     * @return CompletableFuture<Boolean> indicating success or failure
     */
    public abstract CompletableFuture<Boolean> sendToTrace(String traceId);
    
    /**
     * Builds the JSON payload for this observation.
     * 
     * @param traceId the trace ID to associate with
     * @return JsonObject containing the observation payload
     */
    protected abstract JsonObject buildPayload(String traceId);
}
