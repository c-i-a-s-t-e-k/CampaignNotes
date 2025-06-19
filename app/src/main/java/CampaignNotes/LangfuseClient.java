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
     * Tracks an embedding generation call to Langfuse.
     * Creates a generation entry with proper tagging for OpenAI embedding calls.
     * 
     * @param input the input text that was embedded
     * @param model the embedding model used
     * @param campaignId the campaign UUID
     * @param noteId the note ID (if applicable)
     * @param tokensUsed number of tokens consumed
     * @param durationMs time taken in milliseconds
     * @return true if tracking was successful, false otherwise
     */
    public boolean trackEmbedding(String input, String model, String campaignId, String noteId, 
                                 int tokensUsed, long durationMs) {
        return trackEmbedding(input, model, campaignId, noteId, tokensUsed, durationMs, Collections.emptyList());
    }
    
    /**
     * Tracks an embedding generation call to Langfuse with custom tags.
     * Creates a generation entry with proper tagging for OpenAI embedding calls.
     * 
     * @param input the input text that was embedded
     * @param model the embedding model used
     * @param campaignId the campaign UUID
     * @param noteId the note ID (if applicable)
     * @param tokensUsed number of tokens consumed
     * @param durationMs time taken in milliseconds
     * @param customTags additional custom tags to include
     * @return true if tracking was successful, false otherwise
     */
    public boolean trackEmbedding(String input, String model, String campaignId, String noteId, 
                                 int tokensUsed, long durationMs, List<String> customTags) {
        try {
            String generationEndpoint = langfuseHost + "/api/public/generations";
            
            // Create generation payload
            JsonObject payload = new JsonObject();
            payload.addProperty("name", "note-embedding");
            payload.addProperty("model", model);
            payload.addProperty("input", input);
            payload.addProperty("output", "embedding-vector");
            payload.addProperty("startTime", java.time.Instant.now().minusMillis(durationMs).toString());
            payload.addProperty("endTime", java.time.Instant.now().toString());
            
            // Add usage information
            JsonObject usage = new JsonObject();
            usage.addProperty("input", tokensUsed);
            usage.addProperty("output", 0);
            usage.addProperty("total", tokensUsed);
            payload.add("usage", usage);
            
            // Add metadata with campaign and note information
            JsonObject metadata = new JsonObject();
            metadata.addProperty("campaign_id", campaignId);
            metadata.addProperty("note_id", noteId);
            metadata.addProperty("system_component", "note-embedding");
            metadata.addProperty("operation_type", "text-embedding");
            payload.add("metadata", metadata);
            
            // Add tags for filtering and organization
            JsonArray tagsArray = new JsonArray();
            tagsArray.add("system:campaign-notes");
            tagsArray.add("component:embedding");
            tagsArray.add("model:" + model);
            tagsArray.add("campaign:" + campaignId.substring(0, 8)); // First 8 chars of UUID for grouping
            
            // Add custom tags if provided
            if (customTags != null) {
                for (String customTag : customTags) {
                    tagsArray.add(customTag);
                }
            }
            payload.add("tags", tagsArray);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(generationEndpoint))
                    .header("Authorization", basicAuthHeader)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            // Check if response is successful (2xx status codes)
            boolean isSuccessful = response.statusCode() >= 200 && response.statusCode() < 300;
            
            if (isSuccessful) {
                System.out.println("Embedding tracked successfully in Langfuse");
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
} 

    // Future methods can be added here for extended functionality:
    // - createTrace()
    // - createSpan() 
    // - createGeneration()
    // - createScore()
    // - getAnnotationQueues()
    // - etc.
    