package CampaignNotes.tracking;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Basic HTTP client for Langfuse API (Prompts and Models endpoints only).
 * 
 * This is a simplified version used only for non-tracking API operations.
 * For tracing and observations, use OpenTelemetry SDK instead.
 * 
 * Responsibilities:
 * - HTTP GET requests to Langfuse API
 * - Authentication with Basic Auth
 * - Simple retry mechanism
 * - JSON response parsing
 */
public class LangfuseBasicHttpClient {
    
    private final LangfuseConfig config;
    private final HttpClient httpClient;
    private final Gson gson;
    
    private static final int DEFAULT_MAX_RETRIES = 2;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    
    /**
     * Constructor with configuration dependency.
     * 
     * @param config Langfuse configuration
     */
    public LangfuseBasicHttpClient(LangfuseConfig config) {
        this.config = config;
        this.httpClient = config.getHttpClient();
        this.gson = new Gson();
    }
    
    /**
     * Performs a GET request to the specified endpoint.
     * 
     * @param endpoint the API endpoint (relative to host)
     * @return HttpResponse containing the response data
     * @throws Exception if the request fails
     */
    public HttpResponse<String> get(String endpoint) throws Exception {
        String fullUrl = buildFullUrl(endpoint);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header("Authorization", config.getBasicAuthHeader())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(DEFAULT_TIMEOUT)
                .GET()
                .build();
        
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
    
    /**
     * Performs a GET request with retry mechanism.
     * 
     * @param endpoint the API endpoint (relative to host)
     * @param maxRetries maximum number of retry attempts
     * @return HttpResponse containing the response data
     * @throws Exception if all retry attempts fail
     */
    public HttpResponse<String> getWithRetry(String endpoint, int maxRetries) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpResponse<String> response = get(endpoint);
                
                // Check if response indicates a server error and we should retry
                if (response.statusCode() >= 500 && attempt < maxRetries) {
                    System.err.println("Server error (attempt " + (attempt + 1) + "/" + (maxRetries + 1) + 
                                     ") for endpoint: " + endpoint + ". Status: " + response.statusCode());
                    waitForRetry(attempt);
                    continue;
                }
                
                return response;
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Request interrupted for endpoint: " + endpoint, e);
            } catch (Exception e) {
                lastException = e;
                
                if (attempt < maxRetries) {
                    System.err.println("Error in GET request (attempt " + (attempt + 1) + "/" + (maxRetries + 1) + 
                                     ") for endpoint: " + endpoint + ". Error: " + e.getMessage());
                    waitForRetry(attempt);
                }
            }
        }
        
        throw new RuntimeException("All retry attempts failed for GET request to: " + endpoint, lastException);
    }
    
    /**
     * Checks if an HTTP response indicates success.
     * 
     * @param response the HTTP response to check
     * @return true if status code indicates success (2xx), false otherwise
     */
    public boolean isSuccessful(HttpResponse<String> response) {
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }
    
    /**
     * Parses JSON response body to JsonObject.
     * 
     * @param response the HTTP response containing JSON
     * @return parsed JsonObject
     * @throws RuntimeException if JSON parsing fails
     */
    public JsonObject parseJsonResponse(HttpResponse<String> response) throws RuntimeException {
        try {
            return gson.fromJson(response.body(), JsonObject.class);
        } catch (com.google.gson.JsonSyntaxException e) {
            throw new RuntimeException("Failed to parse JSON response: " + response.body(), e);
        }
    }
    
    /**
     * Builds full URL by combining host and endpoint.
     * 
     * @param endpoint the API endpoint (can be relative or absolute)
     * @return the complete URL
     */
    private String buildFullUrl(String endpoint) {
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return endpoint; // Already a full URL
        }
        
        String host = config.getLangfuseHost();
        if (host.endsWith("/") && endpoint.startsWith("/")) {
            return host + endpoint.substring(1);
        } else if (!host.endsWith("/") && !endpoint.startsWith("/")) {
            return host + "/" + endpoint;
        } else {
            return host + endpoint;
        }
    }
    
    /**
     * Waits for retry with exponential backoff.
     * 
     * @param attemptNumber the current attempt number (0-based)
     * @throws InterruptedException if thread is interrupted during wait
     */
    private void waitForRetry(int attemptNumber) throws InterruptedException {
        long delayMs = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attemptNumber);
        Thread.sleep(delayMs);
    }
}
