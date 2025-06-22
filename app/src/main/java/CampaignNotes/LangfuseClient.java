package CampaignNotes;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.github.cdimascio.dotenv.Dotenv;
import model.ModelPricing;
import model.Note;

/**
 * Client for connecting to Langfuse API.
 * Provides functionality to interact with Langfuse service for AI observability and monitoring.
 */
public class LangfuseClient {
    
    private final String langfuseHost;
    private final String publicKey;
    private final String secretKey;
    private final String basicAuthHeader;
    private final HttpClient httpClient;
    private final Gson gson;
    
    /**
     * Constructor that initializes the LangfuseClient with configuration from environment variables.
     * Loads configuration from .env file using dotenv library.
     */
    public LangfuseClient() {
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            
            this.publicKey = dotenv.get("LANGFUSE_PUBLIC_KEY");
            this.secretKey = dotenv.get("LANGFUSE_SECRET_KEY");
            this.langfuseHost = dotenv.get("LANGFUSE_HOST", "https://cloud.langfuse.com");
            
            if (publicKey == null || secretKey == null) {
                throw new IllegalStateException("LANGFUSE_PUBLIC_KEY and LANGFUSE_SECRET_KEY must be set in environment variables");
            }
            
            // Create Basic Auth header: Base64 encode "publicKey:secretKey"
            String credentials = publicKey + ":" + secretKey;
            this.basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
            
            // Initialize HTTP client with reasonable timeouts
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            
            this.gson = new Gson();
                    
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize LangfuseClient: " + e.getMessage(), e);
        }
    }
    
    /**
     * Checks if connection to Langfuse service is available.
     * Uses the /api/public/health endpoint to verify connectivity and authentication.
     * 
     * @return true if connection is successful, false otherwise
     */
    public boolean checkConnection() {
        try {
            String healthEndpoint = langfuseHost + "/api/public/health";
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthEndpoint))
                    .header("Authorization", basicAuthHeader)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            // Check if response is successful (2xx status codes)
            boolean isSuccessful = response.statusCode() >= 200 && response.statusCode() < 300;
            
            if (isSuccessful) {
                System.out.println("Langfuse connection successful. Status: " + response.statusCode());
                return true;
            } else {
                System.err.println("Langfuse connection failed. Status: " + response.statusCode() + 
                                 ", Response: " + response.body());
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("Error connecting to Langfuse: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Retrieves model pricing information from Langfuse API.
     * 
     * @param modelName the name of the model to get pricing for
     * @return ModelPricing object with pricing information, or null if not found
     */
    public ModelPricing getModelPricing(String modelName) {
        try {
            String modelsEndpoint = langfuseHost + "/api/public/models";
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(modelsEndpoint))
                    .header("Authorization", basicAuthHeader)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
                JsonArray dataArray = responseJson.getAsJsonArray("data");
                
                // Search for the model by name or match pattern
                for (int i = 0; i < dataArray.size(); i++) {
                    JsonObject model = dataArray.get(i).getAsJsonObject();
                    String modelNameFromApi = model.get("modelName").getAsString();
                    
                    // Check exact match or pattern match
                    if (modelName.equals(modelNameFromApi) || modelName.matches(model.get("matchPattern").getAsString())) {
                        double inputPrice = 0.0;
                        double outputPrice = 0.0;
                        double totalPrice = 0.0;
                        
                        // Extract pricing information
                        if (model.has("inputPrice") && !model.get("inputPrice").isJsonNull()) {
                            inputPrice = model.get("inputPrice").getAsDouble();
                        }
                        if (model.has("outputPrice") && !model.get("outputPrice").isJsonNull()) {
                            outputPrice = model.get("outputPrice").getAsDouble();
                        }
                        if (model.has("totalPrice") && !model.get("totalPrice").isJsonNull()) {
                            totalPrice = model.get("totalPrice").getAsDouble();
                        }
                        
                        // Check for modern prices object structure
                        if (model.has("prices") && !model.get("prices").isJsonNull()) {
                            JsonObject prices = model.getAsJsonObject("prices");
                            if (prices.has("input_tokens") && !prices.get("input_tokens").isJsonNull()) {
                                JsonObject inputPriceObj = prices.getAsJsonObject("input_tokens");
                                if (inputPriceObj.has("price")) {
                                    inputPrice = inputPriceObj.get("price").getAsDouble();
                                }
                            }
                            if (prices.has("output") && !prices.get("output").isJsonNull()) {
                                JsonObject outputPriceObj = prices.getAsJsonObject("output");
                                if (outputPriceObj.has("price")) {
                                    outputPrice = outputPriceObj.get("price").getAsDouble();
                                }
                            }
                        }
                        
                        return new ModelPricing(modelNameFromApi, inputPrice, outputPrice, totalPrice);
                    }
                }
                
                System.err.println("Model pricing not found for: " + modelName);
                return null;
                
            } else {
                System.err.println("Failed to retrieve model pricing. Status: " + response.statusCode() + 
                                 ", Response: " + response.body());
                return null;
            }
            
        } catch (Exception e) {
            System.err.println("Error retrieving model pricing from Langfuse: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets the configured Langfuse host URL.
     * 
     * @return the Langfuse host URL
     */
    public String getLangfuseHost() {
        return langfuseHost;
    }
    
    /**
     * Gets the public key used for authentication.
     * 
     * @return the public key (note: this is not sensitive information)
     */
    public String getPublicKey() {
        return publicKey;
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
            // Use the ingestion endpoint as recommended by Langfuse documentation
            String ingestionEndpoint = langfuseHost + "/api/public/ingestion";
            
            // Get model pricing for cost calculation
            ModelPricing pricing = getModelPricing(model);
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
            
            // Create the ingestion event envelope
            JsonObject event = new JsonObject();
            event.addProperty("id", java.util.UUID.randomUUID().toString()); // Unique event ID for deduplication
            event.addProperty("type", "generation-create");
            event.addProperty("timestamp", java.time.Instant.now().toString());
            event.add("body", generationBody);
            
            // Create the ingestion batch payload
            JsonObject payload = new JsonObject();
            JsonArray batch = new JsonArray();
            batch.add(event);
            payload.add("batch", batch);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ingestionEndpoint))
                    .header("Authorization", basicAuthHeader)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            // Check if response is successful (207 Multi-Status is expected for ingestion API)
            boolean isSuccessful = response.statusCode() == 207 || (response.statusCode() >= 200 && response.statusCode() < 300);
            
            if (isSuccessful) {
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
            String traceEndpoint = langfuseHost + "/api/public/traces";
            
            // Create trace payload
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
            metadata.addProperty("system_component", "note-processing");
            if (userId != null) {
                metadata.addProperty("user_id", userId);
            }
            payload.add("metadata", metadata);
            
            // Add tags
            JsonArray tagsArray = new JsonArray();
            tagsArray.add("system:campaign-notes");
            tagsArray.add("workflow:note-processing");
            tagsArray.add("campaign:" + campaignId.substring(0, 8));
            
            // Add custom tags if provided
            if (customTags != null) {
                for (String customTag : customTags) {
                    tagsArray.add(customTag);
                }
            }
            payload.add("tags", tagsArray);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(traceEndpoint))
                    .header("Authorization", basicAuthHeader)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
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
     * Retrieves a trace from Langfuse by its ID asynchronously.
     * Used for verification purposes, typically in testing scenarios.
     * 
     * @param traceId the ID of the trace to retrieve
     * @return CompletableFuture containing JsonObject with trace data, or null if not found
     * @throws TimeoutException if the request times out
     */
    public CompletableFuture<JsonObject> getTrace(String traceId) {
        String getTraceEndpoint = langfuseHost + "/api/public/traces/" + traceId;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getTraceEndpoint))
                .header("Authorization", basicAuthHeader)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return gson.fromJson(response.body(), JsonObject.class);
                    } else if (response.statusCode() == 404) {
                        System.err.println("Trace not found: " + traceId);
                        return null;
                    } else {
                        System.err.println("Failed to retrieve trace. Status: " + response.statusCode() + 
                                         ", Response: " + response.body());
                        return null;
                    }
                })
                .exceptionally(ex -> {
                    if (ex.getCause() instanceof java.net.http.HttpTimeoutException) {
                        throw new RuntimeException(new TimeoutException("Request timeout while retrieving trace: " + traceId));
                    }
                    System.err.println("Error retrieving trace from Langfuse: " + ex.getMessage());
                    return null;
                });
    }
    
    /**
     * Retrieves a prompt from Langfuse with variables interpolated.
     * Used to fetch prompts for AI operations like artifact extraction.
     * 
     * @param promptName the name of the prompt to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @return the prompt content with variables interpolated, or null if not found
     */
    public String getPromptWithVariables(String promptName, java.util.Map<String, Object> variables) {
        try {
            String promptEndpoint = langfuseHost + "/api/public/prompts/" + promptName;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(promptEndpoint))
                    .header("Authorization", basicAuthHeader)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
                String promptContent = responseJson.get("prompt").getAsString();
                
                // Simple variable interpolation - replace {{variable}} with values
                if (variables != null) {
                    for (java.util.Map.Entry<String, Object> entry : variables.entrySet()) {
                        String placeholder = "{{" + entry.getKey() + "}}";
                        String value = entry.getValue() != null ? entry.getValue().toString() : "";
                        promptContent = promptContent.replace(placeholder, value);
                    }
                }
                
                System.out.println("Retrieved prompt: " + promptName);
                return promptContent;
                
            } else {
                System.err.println("Failed to retrieve prompt. Status: " + response.statusCode() + 
                                 ", Response: " + response.body());
                return null;
            }
            
        } catch (Exception e) {
            System.err.println("Error retrieving prompt from Langfuse: " + e.getMessage());
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
            String ingestionEndpoint = langfuseHost + "/api/public/ingestion";
            
            // Create generation body
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
            
            // Create the ingestion event envelope
            JsonObject event = new JsonObject();
            event.addProperty("id", java.util.UUID.randomUUID().toString());
            event.addProperty("type", "generation-create");
            event.addProperty("timestamp", java.time.Instant.now().toString());
            event.add("body", generationBody);
            
            // Create the ingestion batch payload
            JsonObject payload = new JsonObject();
            JsonArray batch = new JsonArray();
            batch.add(event);
            payload.add("batch", batch);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ingestionEndpoint))
                    .header("Authorization", basicAuthHeader)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                    .build();
            
            HttpResponse<String> response1 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            boolean isSuccessful = response1.statusCode() == 207 || (response1.statusCode() >= 200 && response1.statusCode() < 300);
            
            if (isSuccessful) {
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
            String traceEndpoint = langfuseHost + "/api/public/traces";
            
            // Create trace payload
            JsonObject payload = new JsonObject();
            payload.addProperty("name", sessionName);
            payload.addProperty("timestamp", java.time.Instant.now().toString());
            
            // Add metadata
            JsonObject metadata = new JsonObject();
            metadata.addProperty("campaign_id", campaignId);
            metadata.addProperty("note_id", noteId);
            metadata.addProperty("system_component", "artifact-extraction");
            metadata.addProperty("workflow_type", "ai-powered-extraction");
            payload.add("metadata", metadata);
            
            // Add tags
            JsonArray tagsArray = new JsonArray();
            tagsArray.add("system:campaign-notes");
            tagsArray.add("workflow:artifact-extraction");
            tagsArray.add("campaign:" + campaignId.substring(0, 8));
            tagsArray.add("ai-operation:artifact-identification");
            payload.add("tags", tagsArray);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(traceEndpoint))
                    .header("Authorization", basicAuthHeader)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
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
    
    // Future methods can be added here for extended functionality:
    // - createTrace()
    // - createSpan() 
    // - createGeneration()
    // - createScore()
    // - getAnnotationQueues()
    // - etc.
    
} 
