package CampaignNotes.config;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Configuration for the hybrid artifact deduplication system.
 * Controls parameters for both Phase 1 (ANN search) and Phase 2 (LLM reasoning).
 * 
 * Parameters can be overridden via environment variables:
 * - DEDUP_CANDIDATE_LIMIT
 * - DEDUP_SIMILARITY_THRESHOLD
 * - DEDUP_LLM_CONFIDENCE_THRESHOLD
 */
public class DeduplicationConfig {
    
    // Default values
    private static final int DEFAULT_CANDIDATE_LIMIT = 5;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.7;
    private static final int DEFAULT_LLM_CONFIDENCE_THRESHOLD = 95;
    
    private static final int MIN_CANDIDATE_LIMIT = 1;
    private static final int MAX_CANDIDATE_LIMIT = 20;
    private static final double MIN_SIMILARITY_THRESHOLD = 0.0;
    private static final double MAX_SIMILARITY_THRESHOLD = 1.0;
    private static final int MIN_CONFIDENCE_THRESHOLD = 0;
    private static final int MAX_CONFIDENCE_THRESHOLD = 100;
    
    private int candidateLimit;
    private double similarityThreshold;
    private int llmConfidenceThreshold;
    private Dotenv dotenv;
    
    /**
     * Constructor that loads configuration from environment variables.
     * Falls back to default values if environment variables are not set.
     */
    public DeduplicationConfig() {
        try {
            this.dotenv = Dotenv.configure().ignoreIfMissing().load();
        } catch (Exception e) {
            System.err.println("Failed to load .env file: " + e.getMessage());
            new Dotenv();
        }
        loadConfig();
    }
    
    /**
     * Constructor with explicit Dotenv instance (useful for testing).
     */
    public DeduplicationConfig(Dotenv dotenv) {
        this.dotenv = dotenv;
        loadConfig();
    }
    
    /**
     * Loads configuration from environment variables or uses defaults.
     */
    private void loadConfig() {
        this.candidateLimit = loadIntFromEnv("DEDUP_CANDIDATE_LIMIT", DEFAULT_CANDIDATE_LIMIT, 
                                             MIN_CANDIDATE_LIMIT, MAX_CANDIDATE_LIMIT);
        this.similarityThreshold = loadDoubleFromEnv("DEDUP_SIMILARITY_THRESHOLD", DEFAULT_SIMILARITY_THRESHOLD,
                                                     MIN_SIMILARITY_THRESHOLD, MAX_SIMILARITY_THRESHOLD);
        this.llmConfidenceThreshold = loadIntFromEnv("DEDUP_LLM_CONFIDENCE_THRESHOLD", DEFAULT_LLM_CONFIDENCE_THRESHOLD,
                                                     MIN_CONFIDENCE_THRESHOLD, MAX_CONFIDENCE_THRESHOLD);
    }
    
    /**
     * Loads an integer value from environment variables with validation.
     */
    private int loadIntFromEnv(String key, int defaultValue, int minValue, int maxValue) {
        if (dotenv == null) {
            return defaultValue;
        }
        
        String value = dotenv.get(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        
        try {
            int parsedValue = Integer.parseInt(value.trim());
            if (parsedValue < minValue || parsedValue > maxValue) {
                System.err.println("Config value out of range: " + key + "=" + parsedValue + 
                                 ". Using default: " + defaultValue);
                return defaultValue;
            }
            return parsedValue;
        } catch (NumberFormatException e) {
            System.err.println("Invalid config value for " + key + ": " + value + 
                             ". Using default: " + defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * Loads a double value from environment variables with validation.
     */
    private double loadDoubleFromEnv(String key, double defaultValue, double minValue, double maxValue) {
        if (dotenv == null) {
            return defaultValue;
        }
        
        String value = dotenv.get(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        
        try {
            double parsedValue = Double.parseDouble(value.trim());
            if (parsedValue < minValue || parsedValue > maxValue) {
                System.err.println("Config value out of range: " + key + "=" + parsedValue + 
                                 ". Using default: " + defaultValue);
                return defaultValue;
            }
            return parsedValue;
        } catch (NumberFormatException e) {
            System.err.println("Invalid config value for " + key + ": " + value + 
                             ". Using default: " + defaultValue);
            return defaultValue;
        }
    }
    
    // === GETTERS AND SETTERS ===
    
    /**
     * Gets the maximum number of candidate artifacts to retrieve in Phase 1 (ANN search).
     * @return candidate limit (1-20, default: 5)
     */
    public int getCandidateLimit() {
        return candidateLimit;
    }
    
    /**
     * Sets the maximum number of candidate artifacts for ANN search.
     * @param candidateLimit value between 1 and 20
     * @throws IllegalArgumentException if value is out of range
     */
    public void setCandidateLimit(int candidateLimit) {
        if (candidateLimit < MIN_CANDIDATE_LIMIT || candidateLimit > MAX_CANDIDATE_LIMIT) {
            throw new IllegalArgumentException("Candidate limit must be between " + MIN_CANDIDATE_LIMIT + 
                                             " and " + MAX_CANDIDATE_LIMIT);
        }
        this.candidateLimit = candidateLimit;
    }
    
    /**
     * Gets the similarity threshold for embedding vectors in Phase 1.
     * Only candidates with similarity >= this threshold are considered.
     * @return similarity threshold (0.0-1.0, default: 0.7)
     */
    public double getSimilarityThreshold() {
        return similarityThreshold;
    }
    
    /**
     * Sets the similarity threshold for embedding vectors.
     * @param similarityThreshold value between 0.0 and 1.0
     * @throws IllegalArgumentException if value is out of range
     */
    public void setSimilarityThreshold(double similarityThreshold) {
        if (similarityThreshold < MIN_SIMILARITY_THRESHOLD || similarityThreshold > MAX_SIMILARITY_THRESHOLD) {
            throw new IllegalArgumentException("Similarity threshold must be between " + MIN_SIMILARITY_THRESHOLD + 
                                             " and " + MAX_SIMILARITY_THRESHOLD);
        }
        this.similarityThreshold = similarityThreshold;
    }
    
    /**
     * Gets the LLM confidence threshold for Phase 2 (LLM reasoning).
     * When confidence is >= this threshold, merge is performed automatically.
     * When confidence is < this threshold, user confirmation is required.
     * When threshold is 100, auto-merge is disabled.
     * @return confidence threshold (0-100, default: 95)
     */
    public int getLLMConfidenceThreshold() {
        return llmConfidenceThreshold;
    }
    
    /**
     * Sets the LLM confidence threshold for auto-merge decisions.
     * @param llmConfidenceThreshold value between 0 and 100 (100 = disabled auto-merge)
     * @throws IllegalArgumentException if value is out of range
     */
    public void setLLMConfidenceThreshold(int llmConfidenceThreshold) {
        if (llmConfidenceThreshold < MIN_CONFIDENCE_THRESHOLD || llmConfidenceThreshold > MAX_CONFIDENCE_THRESHOLD) {
            throw new IllegalArgumentException("LLM confidence threshold must be between " + MIN_CONFIDENCE_THRESHOLD + 
                                             " and " + MAX_CONFIDENCE_THRESHOLD);
        }
        this.llmConfidenceThreshold = llmConfidenceThreshold;
    }
    
    /**
     * Checks if auto-merge is enabled.
     * @return false only if confidence threshold is set to 100
     */
    public boolean isAutoMergeEnabled() {
        return llmConfidenceThreshold < MAX_CONFIDENCE_THRESHOLD;
    }
    
    /**
     * Returns a summary of the current configuration.
     */
    @Override
    public String toString() {
        return String.format("DeduplicationConfig[candidateLimit=%d, similarityThreshold=%.2f, " +
                           "llmConfidenceThreshold=%d, autoMergeEnabled=%s]",
                           candidateLimit, similarityThreshold, llmConfidenceThreshold, isAutoMergeEnabled());
    }
}

