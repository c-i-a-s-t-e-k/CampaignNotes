package CampaignNotes.tracking.otel;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import model.Note;

/**
 * Observation for tracking embedding generation operations.
 * 
 * This class creates a child span under a parent trace to track
 * the generation of embeddings from note content. It captures:
 * - Model used for embedding
 * - Input text and note metadata
 * - Token usage
 * - Operation duration
 * - Success/failure status
 * 
 * Usage within a parent trace:
 * <pre>
 * try (OTelTrace trace = traceManager.createTrace(...)) {
 *     try (OTelEmbeddingObservation obs = 
 *         new OTelEmbeddingObservation("embedding-generation", trace.getContext())) {
 *         
 *         obs.withModel("text-embedding-3-small")
 *            .withInput(note)
 *            .withTokensUsed(tokensUsed)
 *            .withDuration(durationMs);
 *         
 *         // Generate embedding...
 *         
 *         obs.setSuccess();
 *     }
 * }
 * </pre>
 */
public class OTelEmbeddingObservation implements AutoCloseable {
    
    private final Span span;
    
    /**
     * Creates a new embedding observation as a child of the parent context.
     * 
     * @param name observation name (e.g., "embedding-generation")
     * @param parentContext context from the parent trace
     */
    public OTelEmbeddingObservation(String name, Context parentContext) {
        Tracer tracer = OpenTelemetryConfig.getTracer();
        this.span = tracer.spanBuilder(name)
            .setParent(parentContext)
            .setSpanKind(SpanKind.CLIENT)  // Embedding is an external service call
            .startSpan();
        
        // Explicitly set observation type for Langfuse
        span.setAttribute("langfuse.observation.type", "generation");
    }
    
    /**
     * Sets the model used for embedding generation.
     * Uses OpenTelemetry semantic conventions for generative AI.
     * 
     * @param model model identifier (e.g., "text-embedding-3-small")
     * @return this observation for method chaining
     */
    public OTelEmbeddingObservation withModel(String model) {
        span.setAttribute("gen_ai.system", "openai");
        span.setAttribute("gen_ai.request.model", model);
        return this;
    }
    
    /**
     * Sets the input note for embedding.
     * Captures the note text (truncated for performance) and metadata.
     * 
     * @param note the note being embedded
     * @return this observation for method chaining
     */
    public OTelEmbeddingObservation withInput(Note note) {
        String inputText = note.getFullTextForEmbedding();
        // Truncate to 500 chars to avoid excessive span size
        span.setAttribute("gen_ai.prompt", inputText.substring(0, Math.min(500, inputText.length())));
        span.setAttribute("note.id", note.getId());
        span.setAttribute("note.title", note.getTitle());
        return this;
    }
    
    /**
     * Sets the token usage for the embedding operation.
     * 
     * @param tokens number of tokens used
     * @return this observation for method chaining
     */
    public OTelEmbeddingObservation withTokensUsed(int tokens) {
        span.setAttribute("gen_ai.usage.input_tokens", tokens);
        span.setAttribute("gen_ai.usage.total_tokens", tokens);
        return this;
    }
    
    /**
     * Sets the operation duration.
     * 
     * @param durationMs duration in milliseconds
     * @return this observation for method chaining
     */
    public OTelEmbeddingObservation withDuration(long durationMs) {
        span.setAttribute("operation.duration_ms", durationMs);
        return this;
    }
    
    /**
     * Marks the observation as successful.
     */
    public void setSuccess() {
        span.setStatus(StatusCode.OK);
    }
    
    /**
     * Marks the observation as failed with an error message.
     * 
     * @param message error description
     */
    public void setError(String message) {
        span.setStatus(StatusCode.ERROR, message);
    }
    
    /**
     * Records an exception and marks the observation as failed.
     * 
     * @param e the exception that occurred
     */
    public void recordException(Exception e) {
        span.recordException(e);
        span.setStatus(StatusCode.ERROR, e.getMessage());
    }
    
    /**
     * Ends the span and exports it.
     * Automatically called when used with try-with-resources.
     */
    @Override
    public void close() {
        span.end();
    }
}
