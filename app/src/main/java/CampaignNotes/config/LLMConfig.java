package CampaignNotes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import CampaignNotes.llm.HeadLLMService;
import CampaignNotes.llm.LLMService;
import CampaignNotes.llm.OpenAIEmbeddingService;

/**
 * Configuration class for LLM and embedding services.
 * Contains information about all LLM models used in the application
 * and provides bean definitions for LLM and embedding services.
 */
@Configuration
public class LLMConfig {
    
    // OpenAI models
    private static final String OPENAI_O3_MINI = "o3-mini";
    private static final String OPENAI_O1 = "o1";
    private static final String OPENAI_GPT_4O = "gpt-4o";
    private static final String OPENAI_GPT_3_5_TURBO = "gpt-3.5-turbo";
    
    // Qwen3 models
    private static final String QWEN3 = "qwen3";
    private static final String QWEN3_THINKING = "qwen3-thinking";
    
    // Embedding model
    private static final String EMBEDDING_MODEL = "text-embedding-3-large";
    
    // Application-specific model assignments
    private static final String ARTIFACT_EXTRACTION_MODEL = QWEN3_THINKING;
    private static final String RELATIONSHIP_EXTRACTION_MODEL = QWEN3_THINKING;
    private static final String DEDUP_MODEL = QWEN3;
    
    /**
     * Gets the embedding model name.
     * 
     * @return embedding model name
     */
    public String getEmbeddingModel() {
        return EMBEDDING_MODEL;
    }
    
    /**
     * Gets the model used for artifact extraction.
     * 
     * @return artifact extraction model name
     */
    public String getArtifactExtractionModel() {
        return ARTIFACT_EXTRACTION_MODEL;
    }
    
    /**
     * Gets the model used for relationship extraction.
     * 
     * @return relationship extraction model name
     */
    public String getRelationshipExtractionModel() {
        return RELATIONSHIP_EXTRACTION_MODEL;
    }
    
    /**
     * Gets the model used for deduplication reasoning.
     * 
     * @return deduplication model name
     */
    public String getDedupModel() {
        return DEDUP_MODEL;
    }
    
    /**
     * Bean for LLM Service.
     * Returns HeadLLMService which automatically selects the appropriate
     * service implementation based on the model name.
     * 
     * @return HeadLLMService instance
     */
    @Bean
    public LLMService getLLMServise() {
        return new HeadLLMService();
    }
    
    /**
     * Bean for Embedding Service.
     * Returns OpenAIEmbeddingService for generating embeddings.
     * 
     * @return OpenAIEmbeddingService instance
     */
    @Bean
    public OpenAIEmbeddingService getEmbeddingService() {
        return new OpenAIEmbeddingService();
    }
}

