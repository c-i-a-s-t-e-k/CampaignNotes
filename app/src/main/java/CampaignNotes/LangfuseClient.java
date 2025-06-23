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
 * 
 * Enhanced Prompt Management Features:
 * - Client-side caching with configurable TTL (default 60 seconds)
 * - Automatic retry mechanism with exponential backoff
 * - Support for prompt versioning and labels
 * - Support for both text and chat prompts
 * - Production-ready with proper error handling
 * 
 * Usage Examples:
 * 
 * Basic usage with default settings:
 * ```
 * Map<String, Object> variables = Map.of("name", "John", "topic", "AI");
 * String prompt = langfuseClient.getPromptWithVariables("my-prompt", variables);
 * ```
 * 
 * Get specific version:
 * ```
 * String prompt = langfuseClient.getPromptVersionWithVariables("my-prompt", 2, variables);
 * ```
 * 
 * Get with specific label (e.g., staging):
 * ```
 * String prompt = langfuseClient.getPromptWithLabel("my-prompt", "staging", variables);
 * ```
 * 
 * Disable caching for development:
 * ```
 * String prompt = langfuseClient.getPromptWithVariablesNoCache("my-prompt", variables);
 * ```
 * 
 * Pre-load prompts at application startup:
 * ```
 * List<String> prompts = Arrays.asList("prompt1", "prompt2", "prompt3");
 * langfuseClient.preloadPrompts(prompts);
 * ```
 */
public class LangfuseClient {
    
    private final String langfuseHost;
    private final String publicKey;
    private final String secretKey;
    private final String basicAuthHeader;
    private final HttpClient httpClient;
    private final Gson gson;
    
    // Cache for prompts to improve performance and reduce API calls
    private final java.util.concurrent.ConcurrentHashMap<String, CachedPrompt> promptCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long DEFAULT_CACHE_TTL_MS = 60_000; // 60 seconds as per Langfuse recommendation
    private static final int DEFAULT_MAX_RETRIES = 2; // Default retry attempts
    
    /**
     * Cached prompt wrapper with TTL support
     */
    private static class CachedPrompt {
        private final JsonObject promptData;
        private final long timestamp;
        private final long ttlMs;
        
        public CachedPrompt(JsonObject promptData, long ttlMs) {
            this.promptData = promptData;
            this.timestamp = System.currentTimeMillis();
            this.ttlMs = ttlMs;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > ttlMs;
        }
        
        public JsonObject getPromptData() {
            return promptData;
        }
    }
    
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
     * Enhanced version with caching, retry mechanism, and proper API v2 support.
     * 
     * @param promptName the name of the prompt to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @return the prompt content with variables interpolated, or null if not found
     */
    public String getPromptWithVariables(String promptName, java.util.Map<String, Object> variables) {
        return getPromptWithVariables(promptName, variables, null, null, DEFAULT_CACHE_TTL_MS, DEFAULT_MAX_RETRIES);
    }
    
    /**
     * Retrieves a prompt from Langfuse with variables interpolated and advanced options.
     * 
     * @param promptName the name of the prompt to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @param version specific version number (optional, if null uses production label)
     * @param label specific label (optional, defaults to "production" if null)
     * @param cacheTtlMs cache TTL in milliseconds (use 0 to disable caching)
     * @param maxRetries maximum retry attempts on failure
     * @return the prompt content with variables interpolated, or null if not found
     */
    public String getPromptWithVariables(String promptName, java.util.Map<String, Object> variables, 
                                        Integer version, String label, long cacheTtlMs, int maxRetries) {
        if (promptName == null || promptName.trim().isEmpty()) {
            throw new IllegalArgumentException("promptName cannot be null or empty");
        }
        
        // Create cache key including version/label parameters
        String cacheKey = buildCacheKey(promptName, version, label);
        
        try {
            // Check cache first (if caching is enabled)
            JsonObject promptData = getCachedPrompt(cacheKey, cacheTtlMs);
            
            if (promptData == null) {
                // Fetch from API with retry mechanism
                promptData = fetchPromptFromAPI(promptName, version, label, maxRetries);
                
                if (promptData != null && cacheTtlMs > 0) {
                    // Cache the result
                    promptCache.put(cacheKey, new CachedPrompt(promptData, cacheTtlMs));
                }
            }
            
            if (promptData == null) {
                System.err.println("Failed to retrieve prompt: " + promptName);
                return null;
            }
            
            // Extract prompt content based on type
            String promptContent = extractPromptContent(promptData);
            if (promptContent == null) {
                System.err.println("Invalid prompt structure for: " + promptName);
                return null;
            }
            
            // Interpolate variables
            String interpolatedPrompt = interpolateVariables(promptContent, variables);
            
            System.out.println("Successfully retrieved and processed prompt: " + promptName + 
                              (version != null ? " (version " + version + ")" : "") +
                              (label != null ? " (label " + label + ")" : ""));
            
            return interpolatedPrompt;
            
        } catch (Exception e) {
            System.err.println("Error retrieving prompt from Langfuse: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Builds cache key from prompt name and optional parameters
     */
    private String buildCacheKey(String promptName, Integer version, String label) {
        StringBuilder keyBuilder = new StringBuilder(promptName);
        if (version != null) {
            keyBuilder.append(":v").append(version);
        } else if (label != null) {
            keyBuilder.append(":l").append(label);
        } else {
            keyBuilder.append(":l:production"); // Default label
        }
        return keyBuilder.toString();
    }
    
    /**
     * Retrieves cached prompt if available and not expired
     */
    private JsonObject getCachedPrompt(String cacheKey, long cacheTtlMs) {
        if (cacheTtlMs <= 0) {
            return null; // Caching disabled
        }
        
        CachedPrompt cached = promptCache.get(cacheKey);
        if (cached != null) {
            if (!cached.isExpired()) {
                System.out.println("Using cached prompt: " + cacheKey);
                return cached.getPromptData();
            } else {
                // Remove expired entry
                promptCache.remove(cacheKey);
            }
        }
        return null;
    }
    
    /**
     * Fetches prompt from Langfuse API with retry mechanism
     */
    private JsonObject fetchPromptFromAPI(String promptName, Integer version, String label, int maxRetries) {
        // Use v2 API endpoint as recommended by Langfuse documentation
        StringBuilder urlBuilder = new StringBuilder(langfuseHost + "/api/public/v2/prompts/" + promptName);
            
        // Add query parameters for version or label
        java.util.List<String> queryParams = new java.util.ArrayList<>();
        if (version != null) {
            queryParams.add("version=" + version);
        } else if (label != null) {
            queryParams.add("label=" + label);
        } else {
            queryParams.add("label=production"); // Default to production label
        }
        
        if (!queryParams.isEmpty()) {
            urlBuilder.append("?").append(String.join("&", queryParams));
        }
        
        String promptEndpoint = urlBuilder.toString();
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(promptEndpoint))
                    .header("Authorization", basicAuthHeader)
                    .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return gson.fromJson(response.body(), JsonObject.class);
                } else if (response.statusCode() == 404) {
                    System.err.println("Prompt not found: " + promptName);
                    return null; // Don't retry for 404
                } else if (response.statusCode() >= 500 && attempt < maxRetries) {
                    // Retry on server errors
                    System.err.println("Server error (attempt " + (attempt + 1) + "/" + (maxRetries + 1) + 
                                     ") for prompt: " + promptName + ". Status: " + response.statusCode());
                    Thread.sleep(1000 * (attempt + 1)); // Exponential backoff
                    continue;
                } else {
                    System.err.println("Failed to retrieve prompt. Status: " + response.statusCode() + 
                                     ", Response: " + response.body());
                    return null;
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Request interrupted for prompt: " + promptName);
                return null;
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    System.err.println("Error fetching prompt (attempt " + (attempt + 1) + "/" + (maxRetries + 1) + 
                                     "): " + e.getMessage());
                    try {
                        Thread.sleep(1000 * (attempt + 1)); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    System.err.println("Final attempt failed for prompt: " + promptName + ". Error: " + e.getMessage());
                    return null;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extracts prompt content from API response, supporting both text and chat prompts
     */
    private String extractPromptContent(JsonObject promptData) {
        try {
            // Check prompt type and extract content accordingly
            String promptType = promptData.has("type") ? promptData.get("type").getAsString() : "text";
            
            if ("chat".equals(promptType)) {
                // For chat prompts, convert array of messages to a formatted string
                if (promptData.has("prompt") && promptData.get("prompt").isJsonArray()) {
                    JsonArray messages = promptData.getAsJsonArray("prompt");
                    StringBuilder chatContent = new StringBuilder();
                    
                    for (int i = 0; i < messages.size(); i++) {
                        JsonObject message = messages.get(i).getAsJsonObject();
                        String role = message.get("role").getAsString();
                        String content = message.get("content").getAsString();
                        
                        if (i > 0) chatContent.append("\n");
                        chatContent.append("[").append(role.toUpperCase()).append("]: ").append(content);
                    }
                    
                    return chatContent.toString();
                }
            } else {
                // For text prompts, extract the prompt string directly
                if (promptData.has("prompt")) {
                    return promptData.get("prompt").getAsString();
                }
            }
            
            System.err.println("Invalid prompt structure: missing 'prompt' field or unsupported type");
            return null;
            
        } catch (Exception e) {
            System.err.println("Error extracting prompt content: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Interpolates variables in prompt content using {{variable}} syntax
     */
    private String interpolateVariables(String promptContent, java.util.Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return promptContent;
        }
        
        String result = promptContent;
        
        // Enhanced variable interpolation with validation
                    for (java.util.Map.Entry<String, Object> entry : variables.entrySet()) {
                        String placeholder = "{{" + entry.getKey() + "}}";
                        String value = entry.getValue() != null ? entry.getValue().toString() : "";
            
            // Replace all occurrences of the placeholder
            result = result.replace(placeholder, value);
        }
        
        // Check for unresolved variables (optional warning)
        if (result.contains("{{") && result.contains("}}")) {
            System.out.println("Warning: Unresolved variables found in prompt content");
        }
        
        return result;
    }
    
    /**
     * Clears the prompt cache. Useful for testing or when you want to force refresh.
     */
    public void clearPromptCache() {
        promptCache.clear();
        System.out.println("Prompt cache cleared");
    }
    
    /**
     * Gets current cache size for monitoring purposes
     */
    public int getPromptCacheSize() {
        return promptCache.size();
    }
    
    // Convenience methods for common use cases
    
    /**
     * Retrieves a prompt without caching (always fresh from API)
     * Useful for development/testing scenarios
     */
    public String getPromptWithVariablesNoCache(String promptName, java.util.Map<String, Object> variables) {
        return getPromptWithVariables(promptName, variables, null, null, 0, DEFAULT_MAX_RETRIES);
    }
    
    /**
     * Retrieves the latest version of a prompt (using 'latest' label)
     * Useful for development scenarios where you want the newest version
     */
    public String getLatestPromptWithVariables(String promptName, java.util.Map<String, Object> variables) {
        return getPromptWithVariables(promptName, variables, null, "latest", DEFAULT_CACHE_TTL_MS, DEFAULT_MAX_RETRIES);
    }
    
    /**
     * Retrieves a specific version of a prompt
     */
    public String getPromptVersionWithVariables(String promptName, int version, java.util.Map<String, Object> variables) {
        return getPromptWithVariables(promptName, variables, version, null, DEFAULT_CACHE_TTL_MS, DEFAULT_MAX_RETRIES);
    }
    
    /**
     * Retrieves a prompt with a specific label (e.g., "staging", "production")
     */
    public String getPromptWithLabel(String promptName, String label, java.util.Map<String, Object> variables) {
        return getPromptWithVariables(promptName, variables, null, label, DEFAULT_CACHE_TTL_MS, DEFAULT_MAX_RETRIES);
                    }
    
    /**
     * Retrieves a prompt with extended cache TTL for production scenarios where prompts don't change often
     */
    public String getPromptWithVariablesExtendedCache(String promptName, java.util.Map<String, Object> variables) {
        long extendedCacheTtl = 300_000; // 5 minutes
        return getPromptWithVariables(promptName, variables, null, null, extendedCacheTtl, DEFAULT_MAX_RETRIES);
    }
    
    /**
     * Retrieves raw prompt data without variable interpolation
     * Useful for inspection or custom processing
     */
    public JsonObject getRawPromptData(String promptName, Integer version, String label) {
        String cacheKey = buildCacheKey(promptName, version, label);
        
        // Check cache first
        JsonObject cachedData = getCachedPrompt(cacheKey, DEFAULT_CACHE_TTL_MS);
        if (cachedData != null) {
            return cachedData;
        }
        
        // Fetch from API
        JsonObject promptData = fetchPromptFromAPI(promptName, version, label, DEFAULT_MAX_RETRIES);
        
        if (promptData != null) {
            promptCache.put(cacheKey, new CachedPrompt(promptData, DEFAULT_CACHE_TTL_MS));
        }
        
        return promptData;
    }
    
    /**
     * Pre-loads prompts into cache for improved performance
     * Useful for application startup scenarios
     */
    public void preloadPrompts(java.util.List<String> promptNames) {
        preloadPrompts(promptNames, null, "production");
    }
    
    /**
     * Pre-loads prompts with specific label into cache
     */
    public void preloadPrompts(java.util.List<String> promptNames, Integer version, String label) {
        if (promptNames == null || promptNames.isEmpty()) {
            return;
        }
        
        System.out.println("Pre-loading " + promptNames.size() + " prompts into cache...");
        
        for (String promptName : promptNames) {
            try {
                JsonObject promptData = fetchPromptFromAPI(promptName, version, label, DEFAULT_MAX_RETRIES);
                if (promptData != null) {
                    String cacheKey = buildCacheKey(promptName, version, label);
                    promptCache.put(cacheKey, new CachedPrompt(promptData, DEFAULT_CACHE_TTL_MS));
                    System.out.println("Pre-loaded prompt: " + promptName);
            } else {
                    System.err.println("Failed to pre-load prompt: " + promptName);
                }
            } catch (Exception e) {
                System.err.println("Error pre-loading prompt " + promptName + ": " + e.getMessage());
            }
        }
        
        System.out.println("Pre-loading completed. Cache size: " + promptCache.size());
    }
    
    /**
     * Validates if a prompt exists without loading it fully
     */
    public boolean promptExists(String promptName) {
        return promptExists(promptName, null, "production");
    }
    
    /**
     * Validates if a specific prompt version/label exists
     */
    public boolean promptExists(String promptName, Integer version, String label) {
        try {
            JsonObject promptData = fetchPromptFromAPI(promptName, version, label, 1); // Single attempt for validation
            return promptData != null;
        } catch (Exception e) {
            return false;
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
