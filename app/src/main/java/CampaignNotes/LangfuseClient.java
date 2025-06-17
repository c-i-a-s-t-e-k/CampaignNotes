package CampaignNotes;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

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
    
    // Future methods can be added here for extended functionality:
    // - createTrace()
    // - createSpan() 
    // - createGeneration()
    // - createScore()
    // - getAnnotationQueues()
    // - etc.
} 