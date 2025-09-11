package CampaignNotes.tracking.trace.observations;

import com.google.gson.JsonObject;

import CampaignNotes.tracking.trace.TraceManager;

/**
 * Builder for creating LLM generation observations with fluent API.
 * 
 * This class provides a clean, fluent interface for building generation observations
 * specifically optimized for NAE (Note Artifact Extraction) and ARE (Artifact 
 * Relationship Extraction) operations. It follows Spring Boot best practices:
 * - Builder Pattern: Provides fluent API for complex object construction
 * - Immutability: Builder state is encapsulated and validated
 * - Single Responsibility: Focuses only on generation observation creation
 * - Validation: Ensures required fields are provided before building
 * 
 * Usage examples:
 * <pre>
 * // NAE generation
 * boolean success = traceManager.generation("nae-generation")
 *     .model("o3-mini")
 *     .input(prompt)
 *     .output(response)
 *     .usage(inputTokens, outputTokens, totalTokens)
 *     .withComponent("nae")
 *     .withStage("artifact-extraction")
 *     .addTo(traceId);
 * 
 * // ARE generation
 * boolean success = traceManager.generation("are-generation")
 *     .model("o3-mini")
 *     .input(prompt)
 *     .output(response)
 *     .usage(inputTokens, outputTokens, totalTokens)
 *     .withComponent("are")
 *     .withStage("relationship-extraction")
 *     .addTo(traceId);
 * </pre>
 */
public class GenerationObservation extends Observation{
    
    private final String name;
    private final String model;
    private final JsonObject input;
    private final JsonObject output;
    private final JsonObject usage;
    private final String componentName;
    private final String stage;
    private final long duration;
    
    /**
     * Private constructor - use Builder to create instances.
     */
    private GenerationObservation(Builder builder) {
        this.name = builder.name;
        this.model = builder.model;
        this.input = builder.input;
        this.output = builder.output;
        this.usage = builder.usage;
        this.componentName = builder.componentName;
        this.stage = builder.stage;
        this.duration = builder.duration;
    }
    
    // Getters for accessing built observation data
    public String getName() { return name; }
    public String getModel() { return model; }
    public JsonObject getInput() { return input; }
    public JsonObject getOutput() { return output; }
    public JsonObject getUsage() { return usage; }
    public String getComponentName() { return componentName; }
    public String getStage() { return stage; }
    public long getDuration() { return duration; }
    
    /**
     * Fluent builder for GenerationObservation.
     * 
     * Provides a clean API for constructing generation observations with proper
     * validation and default values. The builder ensures all required fields
     * are provided before allowing the observation to be built or sent.
     */
    public static class Builder {

        private final String name;
        private final TraceManager traceManager;

        // Required fields
        private String model;
        private JsonObject input;
        private JsonObject output;
        private JsonObject usage;

        // Optional fields with defaults
        private String componentName = "llm-generation";
        private String stage = "processing";
        private long duration = 0;

        /**
         * Package-private constructor - only TraceManager can create builders.
         *
         * @param name         the name for the generation observation
         * @param traceManager the trace manager for sending observations
         */
        public Builder(String name, TraceManager traceManager) {
            this.name = name;
            this.traceManager = traceManager;
        }

        /**
         * Sets the model used for generation.
         *
         * @param model the AI model (e.g., "o3-mini", "gpt-4")
         * @return this builder for method chaining
         */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * Sets the input prompt as plain text.
         *
         * @param prompt the input prompt text
         * @return this builder for method chaining
         */
        public Builder input(String prompt) {
            this.input = new JsonObject();
            this.input.addProperty("text", prompt);
            return this;
        }

        /**
         * Sets the input as structured JSON object.
         *
         * @param inputJson structured input data
         * @return this builder for method chaining
         */
        public Builder input(JsonObject inputJson) {
            this.input = inputJson;
            return this;
        }

        /**
         * Sets the output response as plain text.
         *
         * @param response the model response text
         * @return this builder for method chaining
         */
        public Builder output(String response) {
            this.output = new JsonObject();
            this.output.addProperty("text", response);
            return this;
        }

        /**
         * Sets the output as structured JSON object.
         *
         * @param outputJson structured output data
         * @return this builder for method chaining
         */
        public Builder output(JsonObject outputJson) {
            this.output = outputJson;
            return this;
        }

        /**
         * Sets token usage information.
         *
         * @param inputTokens  exact input token count
         * @param outputTokens exact output token count
         * @param totalTokens  exact total token count
         * @return this builder for method chaining
         */
        public Builder usage(int inputTokens, int outputTokens, int totalTokens) {
            this.usage = new JsonObject();
            this.usage.addProperty("promptTokens", inputTokens);
            this.usage.addProperty("completionTokens", outputTokens);
            this.usage.addProperty("totalTokens", totalTokens);
            return this;
        }

        /**
         * Sets the component name for identification.
         *
         * @param component component identifier (e.g., "nae", "are")
         * @return this builder for method chaining
         */
        public Builder withComponent(String component) {
            this.componentName = component;
            return this;
        }

        /**
         * Sets the processing stage.
         *
         * @param stage processing stage (e.g., "artifact-extraction", "relationship-extraction")
         * @return this builder for method chaining
         */
        public Builder withStage(String stage) {
            this.stage = stage;
            return this;
        }

        /**
         * Sets the operation duration.
         *
         * @param durationMs duration in milliseconds
         * @return this builder for method chaining
         */
        public Builder duration(long durationMs) {
            this.duration = durationMs;
            return this;
        }

        /**
         * Builds and sends the observation to the specified trace.
         *
         * This is the terminal operation that validates all required fields,
         * builds the observation payload, and sends it via TraceManager.
         *
         * @param traceId the trace ID to associate this observation with
         * @return true if the observation was successfully sent, false otherwise
         * @throws IllegalStateException if required fields are missing
         */
    }
        public boolean addTo(String traceId) {
            validateRequiredFields();
            
            // Create the observation payload using TraceManager's payload builder
            JsonObject observationBody = traceManager.getPayloadBuilder().buildStructuredLLMGeneration(
                traceId, model, getInputText(), getOutputText(),
                getPromptTokens(), getCompletionTokens(), getTotalTokens(),
                duration, componentName, stage
            );
            
            // Send via TraceManager
            return traceManager.sendObservation(observationBody);
        }
        
        /**
         * Builds the GenerationObservation without sending it.
         * 
         * @return the built GenerationObservation
         * @throws IllegalStateException if required fields are missing
         */
        public GenerationObservation build() {
            validateRequiredFields();
            return new GenerationObservation(this);
        }
        
        /**
         * Validates that all required fields have been set.
         * 
         * @throws IllegalStateException if any required field is missing
         */
        private void validateRequiredFields() {
            if (model == null || model.trim().isEmpty()) {
                throw new IllegalStateException("Model is required for generation observation");
            }
            if (input == null) {
                throw new IllegalStateException("Input is required for generation observation");
            }
            if (output == null) {
                throw new IllegalStateException("Output is required for generation observation");
            }
            if (usage == null) {
                throw new IllegalStateException("Usage information is required for generation observation");
            }
        }
        
        /**
         * Extracts input text from the input JSON object.
         */
        private String getInputText() {
            if (input.has("text")) {
                return input.get("text").getAsString();
            } else if (input.has("user")) {
                return input.get("user").getAsString();
            } else {
                return input.toString(); // Fallback to JSON string
            }
        }
        
        /**
         * Extracts output text from the output JSON object.
         */
        private String getOutputText() {
            if (output.has("text")) {
                return output.get("text").getAsString();
            } else {
                return output.toString(); // Fallback to JSON string
            }
        }
        
        /**
         * Gets prompt tokens from usage object.
         */
        private int getPromptTokens() {
            return usage.has("promptTokens") ? usage.get("promptTokens").getAsInt() : 0;
        }
        
        /**
         * Gets completion tokens from usage object.
         */
        private int getCompletionTokens() {
            return usage.has("completionTokens") ? usage.get("completionTokens").getAsInt() : 0;
        }
        
        /**
         * Gets total tokens from usage object.
         */
        private int getTotalTokens() {
            return usage.has("totalTokens") ? usage.get("totalTokens").getAsInt() : 
                   getPromptTokens() + getCompletionTokens();
        }
    }
}
