package CampaignNotes.tracking.trace.observations;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;

import CampaignNotes.tracking.LangfuseHttpClient;
import CampaignNotes.tracking.trace.payload.PayloadBuilder;

/**
 * Observation implementation for LLM text generation operations.
 * 
 * This observation type tracks LLM interactions for NAE (Note Artifact Extraction)
 * and ARE (Artifact Relationship Extraction) operations. It captures input prompts,
 * model responses, token usage, timing information, and component identification.
 * 
 * Based on Langfuse documentation, this creates a generation observation
 * specifically optimized for structured LLM generation tasks.
 */
public class GenerationObservation extends Observation {
    
    private String prompt;
    private String response;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
    private String componentName;
    private String stage;

    /**
     * Constructor for GenerationObservation.
     * 
     * @param name the observation name (e.g., "nae-generation", "are-generation")
     * @param httpClient HTTP client for API communication
     * @param payloadBuilder payload builder for JSON creation
     */
    public GenerationObservation(String name, LangfuseHttpClient httpClient, PayloadBuilder payloadBuilder) {
        super(name, ObservationType.GENERATION_OBSERVATION, httpClient, payloadBuilder);
        this.componentName = "llm-generation"; // Default component name
        this.stage = "processing"; // Default stage
        }

        /**
         * Sets the model used for generation.
         *
         * @param model the AI model (e.g., "o3-mini", "gpt-4")
     * @return this observation for method chaining
         */
    public GenerationObservation withModel(String model) {
        setModel(model);
            return this;
        }

        /**
         * Sets the input prompt as plain text.
         *
         * @param prompt the input prompt text
     * @return this observation for method chaining
     */
    public GenerationObservation withPrompt(String prompt) {
        this.prompt = prompt;
        
        JsonObject inputJson = new JsonObject();
        inputJson.addProperty("text", prompt);
        setInput(inputJson);
        
            return this;
        }

        /**
         * Sets the input as structured JSON object.
         *
         * @param inputJson structured input data
     * @return this observation for method chaining
     */
    public GenerationObservation withStructuredInput(JsonObject inputJson) {
        // Extract text if available for prompt field
        if (inputJson.has("text")) {
            this.prompt = inputJson.get("text").getAsString();
        } else if (inputJson.has("user")) {
            this.prompt = inputJson.get("user").getAsString();
        } else {
            this.prompt = inputJson.toString();
        }
        
        setInput(inputJson);
            return this;
        }

        /**
         * Sets the output response as plain text.
         *
         * @param response the model response text
     * @return this observation for method chaining
     */
    public GenerationObservation withResponse(String response) {
        this.response = response;
        
        JsonObject outputJson = new JsonObject();
        outputJson.addProperty("text", response);
        setOutput(outputJson);
        
            return this;
        }

        /**
         * Sets the output as structured JSON object.
         *
         * @param outputJson structured output data
     * @return this observation for method chaining
     */
    public GenerationObservation withStructuredOutput(JsonObject outputJson) {
        // Extract text if available for response field
        if (outputJson.has("text")) {
            this.response = outputJson.get("text").getAsString();
        } else {
            this.response = outputJson.toString();
        }
        
        setOutput(outputJson);
            return this;
        }

        /**
         * Sets token usage information.
         *
     * @param inputTokens exact input token count
         * @param outputTokens exact output token count
     * @param totalTokens exact total token count
     * @return this observation for method chaining
     */
    public GenerationObservation withTokenUsage(int inputTokens, int outputTokens, int totalTokens) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
            return this;
        }

        /**
         * Sets the component name for identification.
         *
         * @param component component identifier (e.g., "nae", "are")
     * @return this observation for method chaining
         */
    public GenerationObservation withComponent(String component) {
            this.componentName = component;
            return this;
        }

        /**
         * Sets the processing stage.
         *
         * @param stage processing stage (e.g., "artifact-extraction", "relationship-extraction")
     * @return this observation for method chaining
         */
    public GenerationObservation withStage(String stage) {
            this.stage = stage;
            return this;
        }

        /**
     * Finalizes the observation and prepares it for sending.
     * 
     * @return this observation for method chaining
     */
    public GenerationObservation finalizeForSending() {
        finalizeObservation();
        
        // Create comprehensive metadata
        JsonObject metadataJson = new JsonObject();
        metadataJson.addProperty("component", componentName);
        metadataJson.addProperty("stage", stage);
        metadataJson.addProperty("operation_duration_ms", getDurationMs());
        metadataJson.addProperty("token_usage_source", "exact_from_provider");
        metadataJson.addProperty("input_tokens", inputTokens);
        metadataJson.addProperty("output_tokens", outputTokens);
        metadataJson.addProperty("total_tokens", totalTokens);
        
        setMetadata(metadataJson);
        return this;
    }

    @Override
    public CompletableFuture<Boolean> sendToTrace(String traceId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate required fields
                if (getModel() == null || getModel().trim().isEmpty()) {
                throw new IllegalStateException("Model is required for generation observation");
            }
                if (getInput() == null) {
                throw new IllegalStateException("Input is required for generation observation");
            }
                if (getOutput() == null) {
                throw new IllegalStateException("Output is required for generation observation");
            }
                if (totalTokens <= 0) {
                    throw new IllegalStateException("Token usage information is required for generation observation");
                }
                
                // Build payload for this observation
                JsonObject observationPayload = buildPayload(traceId);
                
                // Create ingestion event
                JsonObject event = payloadBuilder.buildIngestionEvent("generation-create", observationPayload);
                JsonObject batchPayload = payloadBuilder.buildIngestionBatch(event);
                
                // Send to Langfuse
                HttpResponse<String> httpResponse = httpClient.post("/api/public/ingestion", batchPayload);
                
                if (httpClient.isIngestionSuccessful(httpResponse)) {
                    System.out.println("GenerationObservation sent successfully: " + getObservationId() + 
                        " (component: " + componentName + ", tokens: " + totalTokens + ")");
                    return true;
            } else {
                    System.err.println("Failed to send GenerationObservation. Status: " + 
                        httpResponse.statusCode() + ", Response: " + httpResponse.body());
                    return false;
                }
            } catch (Exception e) {
                System.err.println("Error sending GenerationObservation: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    protected JsonObject buildPayload(String traceId) {
        // Use the existing payload builder method for structured LLM generation
        return payloadBuilder.buildStructuredLLMGeneration(
            traceId, getModel(), prompt, response, 
            inputTokens, outputTokens, totalTokens, 
            getDurationMs(), componentName, stage);
    }

    // Getters for accessing built observation data
    public String getPrompt() { return prompt; }
    public String getResponse() { return response; }
    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public int getTotalTokens() { return totalTokens; }
    public String getComponentName() { return componentName; }
    public String getStage() { return stage; }
}