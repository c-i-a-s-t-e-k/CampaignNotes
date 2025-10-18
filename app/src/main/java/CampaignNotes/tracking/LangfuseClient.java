package CampaignNotes.tracking;

import com.google.gson.JsonObject;

import model.ModelPricing;
import model.PromptContent;

/**
 * Facade client for Langfuse API integration.
 * 
 * This refactored client serves as the main entry point for all Langfuse operations,
 * delegating responsibilities to specialized service components:
 * - LangfuseConfig: Configuration management
 * - LangfuseHttpClient: HTTP communication
 * - LangfuseModelService: Model pricing and information
 * - LangfusePromptManager: Prompt retrieval and management
 * 
 * The facade pattern maintains backward compatibility while providing a clean,
 * modular architecture that follows Single Responsibility Principle.
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
 * Get prompt content:
 * ```
 * PromptContent promptContent = langfuseClient.getPromptContentWithVariables("my-prompt", variables);
 * ```
 */
public class LangfuseClient {
    
    // Core service components
    private final LangfuseConfig config;
    private final LangfuseBasicHttpClient httpClient;
    private final LangfuseModelService modelService;
    private final LangfusePromptManager promptManager;
    
    /**
     * Default constructor that initializes all components with default configuration.
     * Loads configuration from environment variables using dotenv library.
     */
    public LangfuseClient() {
        this.config = new LangfuseConfig();
        this.httpClient = new LangfuseBasicHttpClient(config);
        this.modelService = new LangfuseModelService(httpClient);
        this.promptManager = new LangfusePromptManager(httpClient);
        
    }
    
    /**
     * Constructor with explicit configuration for testing and custom setups.
     * 
     * @param config the Langfuse configuration to use
     */
    public LangfuseClient(LangfuseConfig config) {
        this.config = config;
        this.httpClient = new LangfuseBasicHttpClient(config);
        this.modelService = new LangfuseModelService(httpClient);
        this.promptManager = new LangfusePromptManager(httpClient);
        
    }
    
    // === CONNECTION AND CONFIGURATION METHODS ===
    
    /**
     * Checks if connection to Langfuse service is available.
     * Uses the /api/public/health endpoint to verify connectivity and authentication.
     * 
     * @return true if connection is successful, false otherwise
     */
    public boolean checkConnection() {
        try {
            var response = httpClient.get("/api/public/health");
            
            if (httpClient.isSuccessful(response)) {
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
        return config.getLangfuseHost();
    }
    
    /**
     * Gets the public key used for authentication.
     * 
     * @return the public key (note: this is not sensitive information)
     */
    public String getPublicKey() {
        return config.getPublicKey();
    }
    
    // === MODEL SERVICE DELEGATION ===
    
    /**
     * Retrieves model pricing information from Langfuse API.
     * Delegates to LangfuseModelService.
     * 
     * @param modelName the name of the model to get pricing for
     * @return ModelPricing object with pricing information, or null if not found
     */
    public ModelPricing getModelPricing(String modelName) {
        return modelService.getModelPricing(modelName);
    }
    
    // === TRACKING SERVICE DELEGATION ===
    // Note: All tracking methods have been removed as they are now handled directly
    // by TraceManager and Observation classes in the respective service layers.
    // This provides better separation of concerns and eliminates unnecessary delegation.
    
    // === PROMPT MANAGER DELEGATION ===
    
    // === NEW API METHODS (PromptContent) ===
    
    /**
     * Retrieves a prompt from Langfuse with variables interpolated as PromptContent.
     * Delegates to LangfusePromptManager.
     * 
     * @param promptName the name of the prompt to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @return PromptContent object with type-safe prompt data, or null if not found
     */
    public PromptContent getPromptContentWithVariables(String promptName, java.util.Map<String, Object> variables) {
        return promptManager.getPromptContentWithVariables(promptName, variables);
    }
    
    /**
     * Retrieves a prompt without caching as PromptContent (always fresh from API).
     * Delegates to LangfusePromptManager.
     * 
     * @param promptName the name of the prompt to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @return PromptContent object with type-safe prompt data, or null if not found
     */
    public PromptContent getPromptContentWithVariiblesNoCache(String promptName, java.util.Map<String, Object> variables) {
        return promptManager.getPromptContentWithVariables(promptName, variables, null, null, 0, 3); // 0 cache, 3 retries default
    }
    
    /**
     * Retrieves the latest version of a prompt as PromptContent (using 'latest' label).
     * Delegates to LangfusePromptManager.
     * 
     * @param promptName the name of the prompt to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @return PromptContent object with type-safe prompt data, or null if not found
     */
    public PromptContent getLatestPromptContentWithVariables(String promptName, java.util.Map<String, Object> variables) {
        return promptManager.getPromptContentWithVariables(promptName, variables, null, "latest", 60_000, 3); // 60s cache, 3 retries default
    }
    
    /**
     * Retrieves a specific version of a prompt as PromptContent.
     * Delegates to LangfusePromptManager.
     * 
     * @param promptName the name of the prompt to retrieve
     * @param version the specific version number to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @return PromptContent object with type-safe prompt data, or null if not found
     */
    public PromptContent getPromptContentVersionWithVariables(String promptName, int version, java.util.Map<String, Object> variables) {
        return promptManager.getPromptContentWithVariables(promptName, variables, version, null, 60_000, 3); // 60s cache, 3 retries default
    }
    
    /**
     * Retrieves a prompt with a specific label as PromptContent (e.g., "staging", "production").
     * Delegates to LangfusePromptManager.
     * 
     * @param promptName the name of the prompt to retrieve
     * @param label the specific label to use
     * @param variables map of variables to interpolate in the prompt
     * @return PromptContent object with type-safe prompt data, or null if not found
     */
    public PromptContent getPromptContentWithLabel(String promptName, String label, java.util.Map<String, Object> variables) {
        return promptManager.getPromptContentWithVariables(promptName, variables, null, label, 60_000, 3); // 60s cache, 3 retries default
    }
    
    /**
     * Retrieves a prompt with extended cache TTL as PromptContent for production scenarios.
     * Delegates to LangfusePromptManager.
     * 
     * @param promptName the name of the prompt to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @return PromptContent object with type-safe prompt data, or null if not found
     */
    public PromptContent getPromptContentWithVariablesExtendedCache(String promptName, java.util.Map<String, Object> variables) {
        long extendedCacheTtlMs = 60_000 * 6; // 6x longer cache for production (6 minutes)
        return promptManager.getPromptContentWithVariables(promptName, variables, null, null, extendedCacheTtlMs, 3);
    }
    
    // === DEPRECATED API METHODS (String) ===
    
    /**
     * Retrieves a prompt from Langfuse with variables interpolated.
     * Delegates to LangfusePromptManager.
     * 
     * @deprecated Use {@link #getPromptContentWithVariables(String, java.util.Map)} instead for type-safe prompt handling
     * @param promptName the name of the prompt to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @return the prompt content with variables interpolated, or null if not found
     */
    @Deprecated
    public String getPromptWithVariables(String promptName, java.util.Map<String, Object> variables) {
        return promptManager.getPromptWithVariables(promptName, variables);
    }
    
    /**
     * Retrieves a prompt without caching (always fresh from API).
     * Delegates to LangfusePromptManager.
     * 
     * @deprecated Use {@link #getPromptContentWithVariablesNoCache(String, java.util.Map)} instead for type-safe prompt handling
     * @param promptName the name of the prompt to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @return the prompt content with variables interpolated, or null if not found
     */
    @Deprecated
    public String getPromptWithVariablesNoCache(String promptName, java.util.Map<String, Object> variables) {
        return promptManager.getPromptWithVariablesNoCache(promptName, variables);
    }
    
    /**
     * Retrieves the latest version of a prompt (using 'latest' label).
     * Delegates to LangfusePromptManager.
     * 
     * @deprecated Use {@link #getLatestPromptContentWithVariables(String, java.util.Map)} instead for type-safe prompt handling
     * @param promptName the name of the prompt to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @return the prompt content with variables interpolated, or null if not found
     */
    @Deprecated
    public String getLatestPromptWithVariables(String promptName, java.util.Map<String, Object> variables) {
        return promptManager.getLatestPromptWithVariables(promptName, variables);
    }
    
    /**
     * Retrieves a specific version of a prompt.
     * Delegates to LangfusePromptManager.
     * 
     * @deprecated Use {@link #getPromptContentVersionWithVariables(String, int, java.util.Map)} instead for type-safe prompt handling
     * @param promptName the name of the prompt to retrieve
     * @param version the specific version number to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @return the prompt content with variables interpolated, or null if not found
     */
    @Deprecated
    public String getPromptVersionWithVariables(String promptName, int version, java.util.Map<String, Object> variables) {
        return promptManager.getPromptVersionWithVariables(promptName, version, variables);
    }
    
    /**
     * Retrieves a prompt with a specific label (e.g., "staging", "production").
     * Delegates to LangfusePromptManager.
     * 
     * @deprecated Use {@link #getPromptContentWithLabel(String, String, java.util.Map)} instead for type-safe prompt handling
     * @param promptName the name of the prompt to retrieve
     * @param label the specific label to use
     * @param variables map of variables to interpolate in the prompt
     * @return the prompt content with variables interpolated, or null if not found
     */
    @Deprecated
    public String getPromptWithLabel(String promptName, String label, java.util.Map<String, Object> variables) {
        return promptManager.getPromptWithLabel(promptName, label, variables);
    }
    
    /**
     * Retrieves a prompt with extended cache TTL for production scenarios.
     * Delegates to LangfusePromptManager.
     * 
     * @deprecated Use {@link #getPromptContentWithVariablesExtendedCache(String, java.util.Map)} instead for type-safe prompt handling
     * @param promptName the name of the prompt to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @return the prompt content with variables interpolated, or null if not found
     */
    @Deprecated
    public String getPromptWithVariablesExtendedCache(String promptName, java.util.Map<String, Object> variables) {
        return promptManager.getPromptWithVariablesExtendedCache(promptName, variables);
    }
    
    /**
     * Retrieves raw prompt data without variable interpolation.
     * Delegates to LangfusePromptManager.
     * 
     * @param promptName the name of the prompt to retrieve
     * @param version specific version number (optional)
     * @param label specific label (optional)
     * @return raw JsonObject containing prompt data, or null if not found
     */
    public JsonObject getRawPromptData(String promptName, Integer version, String label) {
        return promptManager.getRawPromptData(promptName, version, label);
    }
    
    /**
     * Pre-loads prompts into cache for improved performance.
     * Delegates to LangfusePromptManager.
     * 
     * @param promptNames list of prompt names to pre-load
     */
    public void preloadPrompts(java.util.List<String> promptNames) {
        promptManager.preloadPrompts(promptNames);
    }
    
    /**
     * Pre-loads prompts with specific label into cache.
     * Delegates to LangfusePromptManager.
     * 
     * @param promptNames list of prompt names to pre-load
     * @param version specific version to pre-load (optional)
     * @param label specific label to pre-load
     */
    public void preloadPrompts(java.util.List<String> promptNames, Integer version, String label) {
        promptManager.preloadPrompts(promptNames, version, label);
    }
    
    /**
     * Validates if a prompt exists without loading it fully.
     * Delegates to LangfusePromptManager.
     * 
     * @param promptName the name of the prompt to check
     * @return true if prompt exists, false otherwise
     */
    public boolean promptExists(String promptName) {
        return promptManager.promptExists(promptName);
    }
    
    /**
     * Validates if a specific prompt version/label exists.
     * Delegates to LangfusePromptManager.
     * 
     * @param promptName the name of the prompt to check
     * @param version specific version number (optional)
     * @param label specific label (optional)
     * @return true if prompt exists, false otherwise
     */
    public boolean promptExists(String promptName, Integer version, String label) {
        return promptManager.promptExists(promptName, version, label);
    }
    
    /**
     * Clears the prompt cache.
     * Delegates to LangfusePromptManager.
     */
    public void clearPromptCache() {
        promptManager.clearPromptCache();
    }
    
    /**
     * Gets current prompt cache size for monitoring purposes.
     * Delegates to LangfusePromptManager.
     * 
     * @return number of cached prompt entries
     */
    public int getPromptCacheSize() {
        return promptManager.getPromptCacheSize();
    }
    
    // === SERVICE ACCESS METHODS (for advanced usage) ===
    
    /**
     * Gets the underlying configuration instance.
     * For advanced usage and testing scenarios.
     * 
     * @return the LangfuseConfig instance
     */
    public LangfuseConfig getConfig() {
        return config;
    }
    
    /**
     * Gets the underlying HTTP client instance.
     * For advanced usage and testing scenarios.
     * 
     * @return the LangfuseBasicHttpClient instance
     */
    public LangfuseBasicHttpClient getHttpClient() {
        return httpClient;
    }
    
    /**
     * Gets the underlying model service instance.
     * For advanced usage and testing scenarios.
     * 
     * @return the LangfuseModelService instance
     */
    public LangfuseModelService getModelService() {
        return modelService;
    }
    
    /**
     * Gets the underlying prompt manager instance.
     * For advanced usage and testing scenarios.
     * 
     * @return the LangfusePromptManager instance
     */
    public LangfusePromptManager getPromptManager() {
        return promptManager;
    }
    
}