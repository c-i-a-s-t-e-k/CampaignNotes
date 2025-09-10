package CampaignNotes.tracking;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import model.ChatMessage;
import model.PromptContent;

/**
 * Manager for Langfuse prompt operations.
 * Handles prompt retrieval, caching, variable interpolation, and version management.
 * This is the largest component extracted from the original LangfuseClient, representing
 * approximately 32% of the original class functionality.
 * 
 * Features:
 * - Client-side caching with configurable TTL
 * - Automatic retry mechanism with exponential backoff
 * - Support for prompt versioning and labels
 * - Support for both text and chat prompts
 * - Variable interpolation with {{variable}} syntax
 * - Pre-loading capabilities for performance optimization
 * - Production-ready error handling and validation
 * 
 * Responsibilities:
 * - Prompt retrieval from Langfuse API
 * - Cache management with TTL
 * - Variable interpolation
 * - Version and label management
 * - Pre-loading and validation
 * - Support for different prompt types (text/chat)
 */
public class LangfusePromptManager {
    
    private final LangfuseHttpClient httpClient;
    
    // Cache for prompts to improve performance and reduce API calls
    private final ConcurrentHashMap<String, CachedPrompt> promptCache = new ConcurrentHashMap<>();
    
    // Default configuration constants
    private static final long DEFAULT_CACHE_TTL_MS = 60_000; // 60 seconds as per Langfuse recommendation
    private static final int DEFAULT_MAX_RETRIES = 3; // Default retry attempts
    private static final long EXTENDED_CACHE_TTL_MS = 300_000; // 5 minutes for production scenarios
    
    /**
     * Cached prompt wrapper with TTL support and preprocessing
     */
    private static class CachedPrompt {
        private final PromptContent promptContent;
        private final long timestamp;
        private final long ttlMs;
        
        public CachedPrompt(JsonObject rawData, long ttlMs) {
            this.promptContent = preprocessPromptData(rawData);
            this.timestamp = System.currentTimeMillis();
            this.ttlMs = ttlMs;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > ttlMs;
        }
        
        public PromptContent getPromptContent() {
            return promptContent;
        }
        
    }
    
    /**
     * Constructor with HTTP client dependency.
     * 
     * @param httpClient the HTTP client for API communication
     */
    public LangfusePromptManager(LangfuseHttpClient httpClient) {
        this.httpClient = httpClient;
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
     * Legacy method maintained for backward compatibility.
     * 
     * @param promptName the name of the prompt to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @param version specific version number (optional, if null uses production label)
     * @param label specific label (optional, defaults to "production" if null)
     * @param cacheTtlMs cache TTL in milliseconds (use 0 to disable caching)
     * @param maxRetries maximum retry attempts on failure
     * @return the prompt content with variables interpolated, or null if not found
     */
    @Deprecated
    public String getPromptWithVariables(String promptName, java.util.Map<String, Object> variables, 
                                        Integer version, String label, long cacheTtlMs, int maxRetries) {
        // Use new infrastructure but return string for backward compatibility
        PromptContent content = getPromptContentWithVariables(promptName, variables, version, label, cacheTtlMs, maxRetries);
        return content != null ? content.asText() : null;
    }
    
    /**
     * Retrieves a prompt without caching (always fresh from API).
     * Useful for development/testing scenarios.
     * 
     * @param promptName the name of the prompt to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @return the prompt content with variables interpolated, or null if not found
     */
    public String getPromptWithVariablesNoCache(String promptName, java.util.Map<String, Object> variables) {
        return getPromptWithVariables(promptName, variables, null, null, 0, DEFAULT_MAX_RETRIES);
    }
    
    /**
     * Retrieves the latest version of a prompt (using 'latest' label).
     * Useful for development scenarios where you want the newest version.
     * 
     * @param promptName the name of the prompt to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @return the prompt content with variables interpolated, or null if not found
     */
    public String getLatestPromptWithVariables(String promptName, java.util.Map<String, Object> variables) {
        return getPromptWithVariables(promptName, variables, null, "latest", DEFAULT_CACHE_TTL_MS, DEFAULT_MAX_RETRIES);
    }
    
    /**
     * Retrieves a specific version of a prompt.
     * 
     * @param promptName the name of the prompt to retrieve
     * @param version the specific version number to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @return the prompt content with variables interpolated, or null if not found
     */
    public String getPromptVersionWithVariables(String promptName, int version, java.util.Map<String, Object> variables) {
        return getPromptWithVariables(promptName, variables, version, null, DEFAULT_CACHE_TTL_MS, DEFAULT_MAX_RETRIES);
    }
    
    /**
     * Retrieves a prompt with a specific label (e.g., "staging", "production").
     * 
     * @param promptName the name of the prompt to retrieve
     * @param label the specific label to use
     * @param variables map of variables to interpolate in the prompt
     * @return the prompt content with variables interpolated, or null if not found
     */
    public String getPromptWithLabel(String promptName, String label, java.util.Map<String, Object> variables) {
        return getPromptWithVariables(promptName, variables, null, label, DEFAULT_CACHE_TTL_MS, DEFAULT_MAX_RETRIES);
    }
    
    /**
     * Retrieves a prompt with extended cache TTL for production scenarios where prompts don't change often.
     * 
     * @param promptName the name of the prompt to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @return the prompt content with variables interpolated, or null if not found
     */
    public String getPromptWithVariablesExtendedCache(String promptName, java.util.Map<String, Object> variables) {
        return getPromptWithVariables(promptName, variables, null, null, EXTENDED_CACHE_TTL_MS, DEFAULT_MAX_RETRIES);
    }
    
    /**
     * Retrieves raw prompt data without variable interpolation.
     * Useful for inspection or custom processing.
     * 
     * @param promptName the name of the prompt to retrieve
     * @param version specific version number (optional)
     * @param label specific label (optional)
     * @return raw JsonObject containing prompt data, or null if not found
     */
    public JsonObject getRawPromptData(String promptName, Integer version, String label) {
        String cacheKey = buildCacheKey(promptName, version, label);
        
        // Check cache first
        PromptContent cachedContent = getCachedPromptContent(cacheKey, DEFAULT_CACHE_TTL_MS);
        if (cachedContent != null) {
            return cachedContent.getRawData();
        }
        
        // Fetch from API
        JsonObject promptData = fetchPromptFromAPI(promptName, version, label, DEFAULT_MAX_RETRIES);
        
        if (promptData != null) {
            promptCache.put(cacheKey, new CachedPrompt(promptData, DEFAULT_CACHE_TTL_MS));
            return promptData;
        }
        
        return null;
    }
    
    /**
     * Pre-loads prompts into cache for improved performance.
     * Useful for application startup scenarios.
     * 
     * @param promptNames list of prompt names to pre-load
     */
    public void preloadPrompts(java.util.List<String> promptNames) {
        preloadPrompts(promptNames, null, "production");
    }
    
    /**
     * Pre-loads prompts with specific label into cache.
     * 
     * @param promptNames list of prompt names to pre-load
     * @param version specific version to pre-load (optional)
     * @param label specific label to pre-load
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
     * Validates if a prompt exists without loading it fully.
     * 
     * @param promptName the name of the prompt to check
     * @return true if prompt exists, false otherwise
     */
    public boolean promptExists(String promptName) {
        return promptExists(promptName, null, "production");
    }
    
    /**
     * Validates if a specific prompt version/label exists.
     * 
     * @param promptName the name of the prompt to check
     * @param version specific version number (optional)
     * @param label specific label (optional)
     * @return true if prompt exists, false otherwise
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
     * Clears the prompt cache. Useful for testing or when you want to force refresh.
     */
    public void clearPromptCache() {
        promptCache.clear();
        System.out.println("Prompt cache cleared");
    }
    
    /**
     * Gets current cache size for monitoring purposes.
     * 
     * @return number of cached prompt entries
     */
    public int getPromptCacheSize() {
        return promptCache.size();
    }
    
    /**
     * Gets cache statistics for monitoring purposes.
     * 
     * @return cache statistics as a string
     */
    public String getCacheStats() {
        return "PromptManager Cache - Size: " + promptCache.size() + 
               ", TTL: " + (DEFAULT_CACHE_TTL_MS / 1000) + "s";
    }
    
    // New Enhanced API Methods
    
    /**
     * Retrieves a prompt as PromptContent with variables interpolated.
     * This is the new type-safe method that preserves prompt structure.
     * 
     * @param promptName the name of the prompt to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @return PromptContent object with interpolated variables, or null if not found
     */
    public PromptContent getPromptContentWithVariables(String promptName, java.util.Map<String, Object> variables) {
        return getPromptContentWithVariables(promptName, variables, null, null, DEFAULT_CACHE_TTL_MS, DEFAULT_MAX_RETRIES);
    }
    
    /**
     * Retrieves a prompt as PromptContent with variables interpolated and advanced options.
     * 
     * @param promptName the name of the prompt to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @param version specific version number (optional, if null uses production label)
     * @param label specific label (optional, defaults to "production" if null)
     * @param cacheTtlMs cache TTL in milliseconds (use 0 to disable caching)
     * @param maxRetries maximum retry attempts on failure
     * @return PromptContent object with interpolated variables, or null if not found
     */
    public PromptContent getPromptContentWithVariables(String promptName, java.util.Map<String, Object> variables, 
                                                      Integer version, String label, long cacheTtlMs, int maxRetries) {
        if (promptName == null || promptName.trim().isEmpty()) {
            throw new IllegalArgumentException("promptName cannot be null or empty");
        }
        
        // Create cache key including version/label parameters
        String cacheKey = buildCacheKey(promptName, version, label);
        
        try {
            // Check cache first (if caching is enabled)
            PromptContent promptContent = getCachedPromptContent(cacheKey, cacheTtlMs);
            
            if (promptContent == null) {
                // Fetch from API with retry mechanism
                JsonObject promptData = fetchPromptFromAPI(promptName, version, label, maxRetries);
                
                if (promptData != null && cacheTtlMs > 0) {
                    // Cache the result (preprocessing happens in CachedPrompt constructor)
                    promptCache.put(cacheKey, new CachedPrompt(promptData, cacheTtlMs));
                    // Get the preprocessed content
                    promptContent = promptCache.get(cacheKey).getPromptContent();
                } else if (promptData != null) {
                    // No caching, preprocess directly
                    promptContent = preprocessPromptData(promptData);
                }
            }
            
            if (promptContent == null) {
                System.err.println("Failed to retrieve prompt: " + promptName);
                return null;
            }
            
            // Interpolate variables
            PromptContent interpolatedContent = interpolateVariablesInContent(promptContent, variables);
            
            System.out.println("Successfully retrieved and processed prompt: " + promptName + 
                              (version != null ? " (version " + version + ")" : "") +
                              (label != null ? " (label " + label + ")" : "") +
                              " [Type: " + interpolatedContent.getType() + "]");
            
            return interpolatedContent;
            
        } catch (Exception e) {
            System.err.println("Error retrieving prompt from Langfuse: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Retrieves a prompt specifically as chat messages with variables interpolated.
     * Only works with chat-type prompts.
     * 
     * @param promptName the name of the prompt to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @return list of ChatMessage objects with interpolated variables, or null if not found or not a chat prompt
     */
    public List<ChatMessage> getChatPromptWithVariables(String promptName, java.util.Map<String, Object> variables) {
        PromptContent content = getPromptContentWithVariables(promptName, variables);
        if (content != null && content.isChat()) {
            return content.asChatMessages();
        }
        if (content != null && !content.isChat()) {
            System.err.println("Prompt '" + promptName + "' is not a chat prompt (type: " + content.getType() + ")");
        }
        return null;
    }
    
    /**
     * Retrieves a prompt specifically as text with variables interpolated.
     * Works with both text and chat prompts (chat prompts are converted to formatted text).
     * 
     * @param promptName the name of the prompt to retrieve
     * @param variables map of variables to interpolate in the prompt
     * @return text content with interpolated variables, or null if not found
     */
    public String getTextPromptWithVariables(String promptName, java.util.Map<String, Object> variables) {
        PromptContent content = getPromptContentWithVariables(promptName, variables);
        return content != null ? content.asText() : null;
    }
    
    /**
     * Checks if a prompt is of chat type.
     * 
     * @param promptName the name of the prompt to check
     * @return true if prompt exists and is of chat type, false otherwise
     */
    public boolean isPromptChatType(String promptName) {
        return getPromptType(promptName) == PromptContent.PromptType.CHAT;
    }
    
    /**
     * Gets the type of a prompt without loading its content.
     * 
     * @param promptName the name of the prompt to check
     * @return the PromptType, or null if prompt doesn't exist
     */
    public PromptContent.PromptType getPromptType(String promptName) {
        try {
            JsonObject promptData = fetchPromptFromAPI(promptName, null, "production", 1);
            if (promptData != null) {
                String type = promptData.has("type") ? promptData.get("type").getAsString() : "text";
                return "chat".equals(type) ? PromptContent.PromptType.CHAT : PromptContent.PromptType.TEXT;
            }
        } catch (Exception e) {
            System.err.println("Error checking prompt type: " + e.getMessage());
        }
        return null;
    }
    
    // Private helper methods
    
    /**
     * Preprocesses prompt data from API response into structured PromptContent.
     * Handles both text and chat prompt types.
     * 
     * @param promptData the raw JSON data from Langfuse API
     * @return preprocessed PromptContent object
     */
    private static PromptContent preprocessPromptData(JsonObject promptData) {
        String type = promptData.has("type") ? 
            promptData.get("type").getAsString() : "text";
        
        if ("chat".equals(type)) {
            List<ChatMessage> messages = parseChatMessages(
                promptData.getAsJsonArray("prompt"));
            return new PromptContent(PromptContent.PromptType.CHAT, messages, promptData);
        } else {
            String textContent = promptData.get("prompt").getAsString();
            return new PromptContent(PromptContent.PromptType.TEXT, textContent, promptData);
        }
    }
    
    /**
     * Parses chat messages from JSON array into ChatMessage objects.
     * 
     * @param messagesArray JSON array containing chat messages
     * @return list of ChatMessage objects
     */
    private static List<ChatMessage> parseChatMessages(JsonArray messagesArray) {
        List<ChatMessage> messages = new ArrayList<>();
        for (JsonElement element : messagesArray) {
            JsonObject messageObj = element.getAsJsonObject();
            String role = messageObj.get("role").getAsString();
            String content = messageObj.get("content").getAsString();
            messages.add(new ChatMessage(role, content));
        }
        return messages;
    }
    
    /**
     * Builds cache key from prompt name and optional parameters.
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
     * Retrieves cached prompt if available and not expired.
     */
    private PromptContent getCachedPromptContent(String cacheKey, long cacheTtlMs) {
        if (cacheTtlMs <= 0) {
            return null; // Caching disabled
        }
        
        CachedPrompt cached = promptCache.get(cacheKey);
        if (cached != null) {
            if (!cached.isExpired()) {
                System.out.println("Using cached prompt: " + cacheKey);
                return cached.getPromptContent();
            } else {
                // Remove expired entry
                promptCache.remove(cacheKey);
            }
        }
        return null;
    }
    
    
    /**
     * Fetches prompt from Langfuse API with retry mechanism.
     */
    private JsonObject fetchPromptFromAPI(String promptName, Integer version, String label, int maxRetries) {
        try {
            // Use v2 API endpoint as recommended by Langfuse documentation
            String encodedPromptName = URLEncoder.encode(promptName, StandardCharsets.UTF_8);
            StringBuilder urlBuilder = new StringBuilder("/api/public/v2/prompts/" + encodedPromptName);
                
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
            
            // Use retry mechanism from HTTP client
            HttpResponse<String> response = httpClient.getWithRetry(promptEndpoint, maxRetries);
            System.out.println("Response: " + response.body());
            
            if (httpClient.isSuccessful(response)) {
                return httpClient.parseJsonResponse(response);
            } else if (response.statusCode() == 404) {
                System.err.println("Prompt not found: " + promptName);
                return null;
            } else {
                System.err.println("Failed to retrieve prompt. Status: " + response.statusCode() + 
                                 ", Response: " + response.body());
                return null;
            }
            
        } catch (Exception e) {
            System.err.println("Error fetching prompt from API: " + e.getMessage());
            return null;
        }
    }
    
    
    /**
     * Interpolates variables in PromptContent, handling both text and chat types.
     * 
     * @param content the PromptContent to interpolate
     * @param variables map of variables to interpolate
     * @return new PromptContent with interpolated variables
     */
    private PromptContent interpolateVariablesInContent(PromptContent content, java.util.Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return content;
        }
        
        if (content.isChat()) {
            List<ChatMessage> interpolatedMessages = content.asChatMessages()
                .stream()
                .map(msg -> new ChatMessage(
                    msg.getRole(),
                    interpolateVariables(msg.getContent(), variables)
                ))
                .collect(Collectors.toList());
            
            return new PromptContent(PromptContent.PromptType.CHAT, interpolatedMessages, 
                                   content.getRawData());
        } else {
            String interpolatedText = interpolateVariables(content.asText(), variables);
            return new PromptContent(PromptContent.PromptType.TEXT, interpolatedText, 
                                   content.getRawData());
        }
    }
    
    /**
     * Interpolates variables in prompt content using {{variable}} syntax.
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
}
