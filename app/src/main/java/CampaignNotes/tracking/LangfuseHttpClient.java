package CampaignNotes.tracking;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * HTTP client wrapper for Langfuse API communication.
 * Provides standardized HTTP operations with retry mechanisms, proper error handling,
 * and authentication management.
 * 
 * Responsibilities:
 * - HTTP request/response handling
 * - Retry mechanism with exponential backoff
 * - Authentication header management
 * - JSON serialization/deserialization
 * - Error handling and logging
 */
public class LangfuseHttpClient {
    
    private final LangfuseConfig config;
    private final Gson gson;
    
    // Default retry configuration
    private static final int DEFAULT_MAX_RETRIES = 2;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;
    
    /**
     * Constructor with LangfuseConfig dependency.
     * 
     * @param config the Langfuse configuration instance
     */
    public LangfuseHttpClient(LangfuseConfig config) {
        this.config = config;
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
        return get(endpoint, config.getDefaultRequestTimeout());
    }
    
    /**
     * Performs a GET request with custom timeout.
     * 
     * @param endpoint the API endpoint (relative to host)
     * @param timeout request timeout duration
     * @return HttpResponse containing the response data
     * @throws Exception if the request fails
     */
    public HttpResponse<String> get(String endpoint, Duration timeout) throws Exception {
        String fullUrl = buildFullUrl(endpoint);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header("Authorization", config.getBasicAuthHeader())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(timeout)
                .GET()
                .build();
        
        return config.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
    
    /**
     * Performs a POST request with JSON payload.
     * 
     * @param endpoint the API endpoint (relative to host)
     * @param payload the JSON payload to send
     * @return HttpResponse containing the response data
     * @throws Exception if the request fails
     */
    public HttpResponse<String> post(String endpoint, JsonObject payload) throws Exception {
        return post(endpoint, payload, config.getDefaultRequestTimeout());
    }
    
    /**
     * Performs a POST request with JSON payload and custom timeout.
     * 
     * @param endpoint the API endpoint (relative to host)
     * @param payload the JSON payload to send
     * @param timeout request timeout duration
     * @return HttpResponse containing the response data
     * @throws Exception if the request fails
     */
    public HttpResponse<String> post(String endpoint, JsonObject payload, Duration timeout) throws Exception {
        String fullUrl = buildFullUrl(endpoint);
        String jsonBody = gson.toJson(payload);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header("Authorization", config.getBasicAuthHeader())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        
        return config.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
    
    /**
     * Performs a GET request with retry mechanism.
     * Automatically retries on server errors (5xx) with exponential backoff.
     * 
     * @param endpoint the API endpoint (relative to host)
     * @return HttpResponse containing the response data
     * @throws Exception if all retry attempts fail
     */
    public HttpResponse<String> getWithRetry(String endpoint) throws Exception {
        return getWithRetry(endpoint, DEFAULT_MAX_RETRIES);
    }
    
    /**
     * Performs a GET request with custom retry configuration.
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
                } else {
                    System.err.println("Final attempt failed for GET request to: " + endpoint + ". Error: " + e.getMessage());
                }
            }
        }
        
        throw new RuntimeException("All retry attempts failed for GET request to: " + endpoint, lastException);
    }
    
    /**
     * Performs a POST request with retry mechanism.
     * Automatically retries on server errors (5xx) with exponential backoff.
     * 
     * @param endpoint the API endpoint (relative to host)
     * @param payload the JSON payload to send
     * @return HttpResponse containing the response data
     * @throws Exception if all retry attempts fail
     */
    public HttpResponse<String> postWithRetry(String endpoint, JsonObject payload) throws Exception {
        return postWithRetry(endpoint, payload, DEFAULT_MAX_RETRIES);
    }
    
    /**
     * Performs a POST request with custom retry configuration.
     * 
     * @param endpoint the API endpoint (relative to host)
     * @param payload the JSON payload to send
     * @param maxRetries maximum number of retry attempts
     * @return HttpResponse containing the response data
     * @throws Exception if all retry attempts fail
     */
    public HttpResponse<String> postWithRetry(String endpoint, JsonObject payload, int maxRetries) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpResponse<String> response = post(endpoint, payload);
                
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
                    System.err.println("Error in POST request (attempt " + (attempt + 1) + "/" + (maxRetries + 1) + 
                                     ") for endpoint: " + endpoint + ". Error: " + e.getMessage());
                    waitForRetry(attempt);
                } else {
                    System.err.println("Final attempt failed for POST request to: " + endpoint + ". Error: " + e.getMessage());
                }
            }
        }
        
        throw new RuntimeException("All retry attempts failed for POST request to: " + endpoint, lastException);
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
     * Checks if an HTTP response indicates success for ingestion API.
     * Langfuse ingestion API returns 207 Multi-Status for successful operations.
     * 
     * @param response the HTTP response to check
     * @return true if status code indicates success for ingestion API
     */
    public boolean isIngestionSuccessful(HttpResponse<String> response) {
        return response.statusCode() == 207 || isSuccessful(response);
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
    
    /**
     * Gets the Gson instance used for JSON operations.
     * 
     * @return the Gson instance
     */
    public Gson getGson() {
        return gson;
    }
    
    /**
     * Gets the configuration instance.
     * 
     * @return the LangfuseConfig instance
     */
    public LangfuseConfig getConfig() {
        return config;
    }
}
