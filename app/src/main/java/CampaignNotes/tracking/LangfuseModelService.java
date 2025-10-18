package CampaignNotes.tracking;

import java.net.http.HttpResponse;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import model.ModelPricing;

/**
 * Service for managing model information and pricing data from Langfuse API.
 * Provides caching and retrieval of model pricing information with support
 * for both legacy and modern API response formats.
 * 
 * Responsibilities:
 * - Retrieving model pricing from Langfuse API
 * - Caching pricing data to reduce API calls
 * - Supporting both legacy and modern pricing formats
 * - Cost calculation utilities
 * - Model existence validation
 */
public class LangfuseModelService {
    
    private final LangfuseBasicHttpClient httpClient;
    
    // Cache for model pricing to reduce API calls
    private final ConcurrentHashMap<String, CachedModelPricing> pricingCache = new ConcurrentHashMap<>();
    private static final long DEFAULT_CACHE_TTL_MS = 300_000; // 5 minutes
    
    /**
     * Cached model pricing wrapper with TTL support
     */
    private static class CachedModelPricing {
        private final ModelPricing pricing;
        private final long timestamp;
        private final long ttlMs;
        
        public CachedModelPricing(ModelPricing pricing, long ttlMs) {
            this.pricing = pricing;
            this.timestamp = System.currentTimeMillis();
            this.ttlMs = ttlMs;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > ttlMs;
        }
        
        public ModelPricing getPricing() {
            return pricing;
        }
    }
    
    /**
     * Constructor with HTTP client dependency.
     * 
     * @param httpClient the HTTP client for API communication
     */
    public LangfuseModelService(LangfuseBasicHttpClient httpClient) {
        this.httpClient = httpClient;
    }
    
    /**
     * Retrieves model pricing information from Langfuse API.
     * Uses caching to reduce API calls with default TTL.
     * 
     * @param modelName the name of the model to get pricing for
     * @return ModelPricing object with pricing information, or null if not found
     */
    public ModelPricing getModelPricing(String modelName) {
        return getModelPricing(modelName, DEFAULT_CACHE_TTL_MS);
    }
    
    /**
     * Retrieves model pricing information with custom cache TTL.
     * 
     * @param modelName the name of the model to get pricing for
     * @param cacheTtlMs cache TTL in milliseconds (use 0 to disable caching)
     * @return ModelPricing object with pricing information, or null if not found
     */
    public ModelPricing getModelPricing(String modelName, long cacheTtlMs) {
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IllegalArgumentException("modelName cannot be null or empty");
        }
        
        // Check cache first (if caching is enabled)
        if (cacheTtlMs > 0) {
            ModelPricing cachedPricing = getCachedModelPricing(modelName);
            if (cachedPricing != null) {
                return cachedPricing;
            }
        }
        
        try {
            // Fetch from API
            String modelsEndpoint = "/api/public/models";
            HttpResponse<String> response = httpClient.get(modelsEndpoint);
            
            if (!httpClient.isSuccessful(response)) {
                System.err.println("Failed to retrieve model pricing. Status: " + response.statusCode() + 
                                 ", Response: " + response.body());
                return null;
            }
            
            JsonObject responseJson = httpClient.parseJsonResponse(response);
            JsonArray dataArray = responseJson.getAsJsonArray("data");
            
            // Search for the model by name or match pattern
            for (int i = 0; i < dataArray.size(); i++) {
                JsonObject model = dataArray.get(i).getAsJsonObject();
                String modelNameFromApi = model.get("modelName").getAsString();
                
                // Check exact match or pattern match
                if (modelName.equals(modelNameFromApi) || 
                    (model.has("matchPattern") && modelName.matches(model.get("matchPattern").getAsString()))) {
                    
                    ModelPricing pricing = parseModelPricing(model, modelNameFromApi);
                    
                    // Cache the result if caching is enabled
                    if (pricing != null && cacheTtlMs > 0) {
                        pricingCache.put(modelName, new CachedModelPricing(pricing, cacheTtlMs));
                    }
                    
                    return pricing;
                }
            }
            
            System.err.println("Model pricing not found for: " + modelName);
            return null;
            
        } catch (Exception e) {
            System.err.println("Error retrieving model pricing from Langfuse: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Retrieves cached model pricing if available and not expired.
     * 
     * @param modelName the model name to look up in cache
     * @return cached ModelPricing or null if not found/expired
     */
    private ModelPricing getCachedModelPricing(String modelName) {
        CachedModelPricing cached = pricingCache.get(modelName);
        if (cached != null) {
            if (!cached.isExpired()) {
                return cached.getPricing();
            } else {
                // Remove expired entry
                pricingCache.remove(modelName);
            }
        }
        return null;
    }
    
    /**
     * Parses model pricing information from API response.
     * Supports both legacy format and modern prices object structure.
     * 
     * @param model the model JSON object from API
     * @param modelNameFromApi the model name from API
     * @return ModelPricing object or null if parsing fails
     */
    private ModelPricing parseModelPricing(JsonObject model, String modelNameFromApi) {
        try {
            double inputPrice = 0.0;
            double outputPrice = 0.0;
            double totalPrice = 0.0;
            
            // Extract legacy pricing format
            if (model.has("inputPrice") && !model.get("inputPrice").isJsonNull()) {
                inputPrice = model.get("inputPrice").getAsDouble();
            }
            if (model.has("outputPrice") && !model.get("outputPrice").isJsonNull()) {
                outputPrice = model.get("outputPrice").getAsDouble();
            }
            if (model.has("totalPrice") && !model.get("totalPrice").isJsonNull()) {
                totalPrice = model.get("totalPrice").getAsDouble();
            }
            
            // Check for modern prices object structure (takes precedence)
            if (model.has("prices") && !model.get("prices").isJsonNull()) {
                JsonObject prices = model.getAsJsonObject("prices");
                
                // Parse input tokens pricing
                if (prices.has("input_tokens") && !prices.get("input_tokens").isJsonNull()) {
                    JsonObject inputPriceObj = prices.getAsJsonObject("input_tokens");
                    if (inputPriceObj.has("price") && !inputPriceObj.get("price").isJsonNull()) {
                        inputPrice = inputPriceObj.get("price").getAsDouble();
                    }
                }
                
                // Parse output tokens pricing
                if (prices.has("output") && !prices.get("output").isJsonNull()) {
                    JsonObject outputPriceObj = prices.getAsJsonObject("output");
                    if (outputPriceObj.has("price") && !outputPriceObj.get("price").isJsonNull()) {
                        outputPrice = outputPriceObj.get("price").getAsDouble();
                    }
                }
                
                // Parse total pricing if available
                if (prices.has("total") && !prices.get("total").isJsonNull()) {
                    JsonObject totalPriceObj = prices.getAsJsonObject("total");
                    if (totalPriceObj.has("price") && !totalPriceObj.get("price").isJsonNull()) {
                        totalPrice = totalPriceObj.get("price").getAsDouble();
                    }
                }
            }
            
            return new ModelPricing(modelNameFromApi, inputPrice, outputPrice, totalPrice);
            
        } catch (Exception e) {
            System.err.println("Error parsing model pricing for " + modelNameFromApi + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Checks if pricing information is available for a specific model.
     * Does not cache the result, used for validation purposes.
     * 
     * @param modelName the model name to check
     * @return true if pricing is available, false otherwise
     */
    public boolean hasPricingFor(String modelName) {
        ModelPricing pricing = getModelPricing(modelName, 0); // No caching for validation
        return pricing != null;
    }
    
    /**
     * Calculates cost for a given model and token usage.
     * Convenience method that retrieves pricing and calculates cost.
     * 
     * @param modelName the model name
     * @param tokensUsed number of tokens used
     * @return calculated cost in USD, or null if pricing not available
     */
    public Double calculateCost(String modelName, int tokensUsed) {
        ModelPricing pricing = getModelPricing(modelName);
        if (pricing != null) {
            return pricing.calculateCost(tokensUsed);
        }
        return null;
    }
    
    /**
     * Calculates cost with separate input/output token counts.
     * 
     * @param modelName the model name
     * @param inputTokens number of input tokens
     * @param outputTokens number of output tokens
     * @return calculated cost in USD, or null if pricing not available
     */
    public Double calculateCost(String modelName, int inputTokens, int outputTokens) {
        ModelPricing pricing = getModelPricing(modelName);
        if (pricing != null) {
            // Calculate cost using the total tokens approach since ModelPricing.calculateCost only accepts one parameter
            int totalTokens = inputTokens + outputTokens;
            return pricing.calculateCost(totalTokens);
        }
        return null;
    }
    
    /**
     * Pre-loads pricing information for multiple models into cache.
     * Useful for application startup scenarios.
     * 
     * @param modelNames list of model names to pre-load
     */
    public void preloadModelPricing(java.util.List<String> modelNames) {
        if (modelNames == null || modelNames.isEmpty()) {
            return;
        }
        
        for (String modelName : modelNames) {
            try {
                ModelPricing pricing = getModelPricing(modelName, DEFAULT_CACHE_TTL_MS);
                if (pricing == null) {
                    System.err.println("Failed to pre-load pricing for model: " + modelName);
                }
            } catch (Exception e) {
                System.err.println("Error pre-loading pricing for model " + modelName + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Clears the pricing cache. Useful for testing or forcing refresh.
     */
    public void clearPricingCache() {
        pricingCache.clear();
    }
    
    /**
     * Gets current cache size for monitoring purposes.
     * 
     * @return number of cached pricing entries
     */
    public int getPricingCacheSize() {
        return pricingCache.size();
    }
    
    /**
     * Gets cache hit ratio for monitoring purposes.
     * 
     * @return cache hit ratio statistics as a string
     */
    public String getCacheStats() {
        return "ModelService Cache - Size: " + pricingCache.size() + 
               ", TTL: " + (DEFAULT_CACHE_TTL_MS / 1000) + "s";
    }
}
