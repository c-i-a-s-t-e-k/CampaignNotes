package CampaignNotes.tracking.trace.observations;

/**
 * Enumeration of different types of observations supported by the application.
 * 
 * Based on Langfuse documentation, observations are the individual operations
 * that occur within a trace. They represent specific interactions with LLMs
 * or other AI services.
 */
public enum ObservationType {
    /**
     * Observation for LLM text generation operations.
     * Used for tracking NAE (Note Artifact Extraction) and ARE (Artifact Relationship Extraction)
     * operations where an LLM generates structured text responses.
     */
    GENERATION_OBSERVATION,
    
    /**
     * Observation for embedding generation operations.
     * Used for tracking the creation of vector embeddings from text content.
     */
    EMBEDDING_OBSERVATION
}
