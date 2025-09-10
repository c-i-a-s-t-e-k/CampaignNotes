package CampaignNotes.tracking;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Configuration class for LangfuseClient.
 * Manages environment variables loading, authentication setup, and HTTP client configuration.
 * 
 * Responsibilities:
 * - Loading configuration from .env file
 * - Validation of required configuration parameters
 * - HTTP client initialization with proper timeouts
 * - Basic authentication header preparation
 */
public class LangfuseConfig {
    
    private final String langfuseHost;
    private final String publicKey;
    private final String secretKey;
    private final String basicAuthHeader;
    private final HttpClient httpClient;
    
    // Default configuration values
    private static final String DEFAULT_LANGFUSE_HOST = "https://cloud.langfuse.com";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    
    /**
     * Default constructor that loads configuration from environment variables.
     * Uses dotenv library to load from .env file if present.
     * 
     * @throws IllegalStateException if required environment variables are missing
     * @throws RuntimeException if configuration initialization fails
     */
    public LangfuseConfig() {
        this(loadDotenv());
    }
    
    /**
     * Constructor with explicit Dotenv instance for testing purposes.
     * 
     * @param dotenv the Dotenv instance to use for configuration loading
     * @throws IllegalStateException if required environment variables are missing
     * @throws RuntimeException if configuration initialization fails
     */
    public LangfuseConfig(Dotenv dotenv) {
        try {
            // Load configuration from environment variables
            this.publicKey = dotenv.get("LANGFUSE_PUBLIC_KEY");
            this.secretKey = dotenv.get("LANGFUSE_SECRET_KEY");
            this.langfuseHost = dotenv.get("LANGFUSE_HOST", DEFAULT_LANGFUSE_HOST);
            
            // Validate required configuration
            validateConfiguration();
            
            // Create Basic Auth header: Base64 encode "publicKey:secretKey"
            String credentials = publicKey + ":" + secretKey;
            this.basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
            
            // Initialize HTTP client with reasonable timeouts
            this.httpClient = createHttpClient();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize LangfuseConfig: " + e.getMessage(), e);
        }
    }
    
    /**
     * Loads Dotenv configuration with default settings.
     * 
     * @return configured Dotenv instance
     */
    private static Dotenv loadDotenv() {
        return Dotenv.configure().ignoreIfMissing().load();
    }
    
    /**
     * Validates that all required configuration parameters are present.
     * 
     * @throws IllegalStateException if required parameters are missing
     */
    private void validateConfiguration() {
        if (publicKey == null || publicKey.trim().isEmpty()) {
            throw new IllegalStateException("LANGFUSE_PUBLIC_KEY must be set in environment variables");
        }
        
        if (secretKey == null || secretKey.trim().isEmpty()) {
            throw new IllegalStateException("LANGFUSE_SECRET_KEY must be set in environment variables");
        }
        
        if (langfuseHost == null || langfuseHost.trim().isEmpty()) {
            throw new IllegalStateException("LANGFUSE_HOST cannot be null or empty");
        }
        
        // Validate URL format
        try {
            java.net.URI uri = new java.net.URI(langfuseHost);
            // Additional validation - check if URI is absolute
            if (!uri.isAbsolute()) {
                throw new IllegalStateException("LANGFUSE_HOST must be an absolute URL: " + langfuseHost);
            }
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException("LANGFUSE_HOST must be a valid URL: " + langfuseHost, e);
        }
    }
    
    /**
     * Creates and configures the HTTP client with appropriate timeouts.
     * 
     * @return configured HttpClient instance
     */
    private HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .build();
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
     * Note: This is not sensitive information and can be safely exposed.
     * 
     * @return the public key
     */
    public String getPublicKey() {
        return publicKey;
    }
    
    /**
     * Gets the secret key used for authentication.
     * Note: This is sensitive information and should be used carefully.
     * 
     * @return the secret key
     */
    public String getSecretKey() {
        return secretKey;
    }
    
    /**
     * Gets the pre-computed Basic Authentication header.
     * 
     * @return the Basic Auth header value (including "Basic " prefix)
     */
    public String getBasicAuthHeader() {
        return basicAuthHeader;
    }
    
    /**
     * Gets the configured HTTP client instance.
     * 
     * @return the HttpClient instance
     */
    public HttpClient getHttpClient() {
        return httpClient;
    }
    
    /**
     * Gets the default request timeout duration.
     * 
     * @return the default request timeout
     */
    public Duration getDefaultRequestTimeout() {
        return DEFAULT_REQUEST_TIMEOUT;
    }
    
    /**
     * Validates the connection parameters without making actual HTTP calls.
     * 
     * @return true if configuration appears valid, false otherwise
     */
    public boolean isConfigurationValid() {
        try {
            validateConfiguration();
            return httpClient != null && basicAuthHeader != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Returns a string representation of the configuration (safe for logging).
     * Does not include sensitive information like the secret key.
     * 
     * @return string representation of configuration
     */
    @Override
    public String toString() {
        return "LangfuseConfig{" +
                "langfuseHost='" + langfuseHost + '\'' +
                ", publicKey='" + publicKey + '\'' +
                ", secretKey='[REDACTED]'" +
                ", httpClientConfigured=" + (httpClient != null) +
                '}';
    }
}
