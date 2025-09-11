package CampaignNotes.tracking.trace.traces;

/**
 * Enumeration of different types of traces supported by the application.
 * 
 * Based on Langfuse documentation, traces represent the top-level grouping
 * of observations and can track different types of workflows.
 */
public enum TraceType {
    /**
     * Trace for embedding operations - tracks the generation of embeddings for notes.
     * Used for single embedding operations without complex workflows.
     */
    EMBEDDING_TRACE,
    
    /**
     * Trace for artifact relation workflows - tracks the complete NAE → ARE pipeline.
     * Used for multi-step processing involving both Note Artifact Extraction
     * and Artifact Relationship Extraction operations.
     */
    ARTIFACT_RELATION_TRACE
}
