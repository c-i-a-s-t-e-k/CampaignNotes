package CampaignNotes.tracking.otel;

import java.util.ArrayList;
import java.util.List;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

/**
 * Manager for creating and managing OpenTelemetry traces.
 * 
 * This class provides a high-level API for creating traces (root spans)
 * that represent workflows in the CampaignNotes application, such as:
 * - Note embedding generation
 * - Artifact extraction from notes
 * - Relationship discovery
 * 
 * Each trace can contain multiple child observations (nested spans) that
 * represent individual operations like LLM calls or embedding generation.
 * 
 * Usage:
 * <pre>
 * OTelTraceManager traceManager = new OTelTraceManager();
 * try (OTelTrace trace = traceManager.createTrace("note-embedding", campaignId, noteId, userId, input)) {
 *     // Your workflow logic here
 *     trace.setAttribute("custom.attribute", "value");
 *     trace.setStatus(true, "Completed successfully");
 * }
 * </pre>
 */
public class OTelTraceManager {
    
    private final Tracer tracer;
    
    /**
     * Creates a new OTelTraceManager using the globally configured tracer.
     * 
     * @throws IllegalStateException if OpenTelemetry is not initialized
     */
    public OTelTraceManager() {
        this.tracer = OpenTelemetryConfig.getTracer();
    }
    
    /**
     * Creates a new trace (root span) for a workflow.
     * 
     * @param traceName descriptive name of the workflow (e.g., "note-embedding")
     * @param campaignId campaign UUID
     * @param noteId note ID being processed
     * @param userId user ID (optional, can be null)
     * @return OTelTrace wrapper that should be used with try-with-resources
     */
    public OTelTrace createTrace(String traceName, String campaignId, String noteId, String userId, String input){
        Span span = tracer.spanBuilder(traceName)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("campaign.id", campaignId)
            .setAttribute("note.id", noteId)
            .setAttribute("system", "campaign-notes")
            .setAttribute("langfuse.trace.name", traceName)
            .setAttribute("langfuse.version", OpenTelemetryConfig.SERVICE_VERSION)
            .setAttribute("input", input)
            .startSpan();
        
        if (userId != null) {
            span.setAttribute("user.id", userId);
        }
        
        return new OTelTrace(span);
    }
    
    /**
     * Wrapper around OpenTelemetry Span that provides high-level API
     * for trace management. Implements AutoCloseable for automatic span finalization.
     */
    public static class OTelTrace implements AutoCloseable {
        private final Span span;
        private final Context context;
        private final List<String> tags;
        
        /**
         * Creates a new OTelTrace wrapper.
         * 
         * @param span the underlying OpenTelemetry span
         */
        public OTelTrace(Span span) {
            this.span = span;
            this.context = Context.current().with(span);
            this.tags = new ArrayList<>();
        }
        
        /**
         * Adds an event to the trace timeline.
         * Events are useful for marking significant points in the workflow.
         * 
         * @param name event name (e.g., "processing_started")
         */
        public void addEvent(String name) {
            span.addEvent(name);
        }
        
        /**
         * Sets a string attribute on the trace.
         * 
         * @param key attribute key
         * @param value attribute value
         */
        public void setAttribute(String key, String value) {
            span.setAttribute(key, value);
        }
        
        /**
         * Sets a long attribute on the trace.
         * 
         * @param key attribute key
         * @param value attribute value
         */
        public void setAttribute(String key, long value) {
            span.setAttribute(key, value);
        }
        
        /**
         * Sets the final status of the trace.
         * 
         * @param success true if the workflow completed successfully
         * @param message status message
         */
        public void setStatus(boolean success, String message) {
            if (success) {
                span.setStatus(StatusCode.OK, message);
            } else {
                span.setStatus(StatusCode.ERROR, message);
            }
        }
        
        /**
         * Records an exception that occurred during the workflow.
         * This automatically sets the span status to ERROR.
         * 
         * @param e the exception to record
         */
        public void recordException(Exception e) {
            span.recordException(e);
        }
        
        /**
         * Gets the context associated with this trace.
         * This context should be used when creating child observations.
         * 
         * @return the OpenTelemetry context with this span active
         */
        public Context getContext() {
            return context;
        }
        
        /**
         * Sets the session ID for this trace.
         * 
         * @param sessionId the session identifier
         * @return this trace for method chaining
         */
        public OTelTrace setSessionId(String sessionId) {
            span.setAttribute("langfuse.session.id", sessionId);
            return this;
        }
        
        /**
         * Adds a tag to categorize the trace.
         * Tags are stored as a JSON array in Langfuse.
         * 
         * @param tag the tag to add
         * @return this trace for method chaining
         */
        public OTelTrace addTag(String tag) {
            tags.add(tag);
            // Convert tags list to JSON array string for Langfuse
            String tagsJson = "[\"" + String.join("\",\"", tags) + "\"]";
            span.setAttribute("langfuse.trace.tags", tagsJson);
            return this;
        }
        
        /**
         * Sets a metadata value that will be queryable in Langfuse UI.
         * Use this for top-level metadata that you want to filter on.
         * 
         * @param key metadata key
         * @param value metadata value
         * @return this trace for method chaining
         */
        public OTelTrace setMetadata(String key, String value) {
            span.setAttribute("langfuse.trace.metadata." + key, value);
            return this;
        }
        
        /**
         * Sets the release version for this trace.
         * 
         * @param release the release version
         * @return this trace for method chaining
         */
        public OTelTrace setRelease(String release) {
            span.setAttribute("langfuse.release", release);
            return this;
        }
        
        /**
         * Sets the deployment environment for this trace.
         * 
         * @param environment the environment (e.g., "dev", "staging", "production")
         * @return this trace for method chaining
         */
        public OTelTrace setEnvironment(String environment) {
            span.setAttribute("deployment.environment", environment);
            return this;
        }
        
        /**
         * Marks the trace as public, allowing it to be shared via URL.
         * 
         * @param isPublic true to make the trace public
         * @return this trace for method chaining
         */
        public OTelTrace setPublic(boolean isPublic) {
            span.setAttribute("langfuse.trace.public", isPublic);
            return this;
        }
        
        /**
         * Gets the trace ID for this span.
         * 
         * @return trace ID as hex string
         */
        public String getTraceId() {
            return span.getSpanContext().getTraceId();
        }
        

        public void close(String output) {
            span.setAttribute("output", output);
            span.end();
        }
        /**
         * Ends the span and exports it.
         * Automatically called when used with try-with-resources.
         */
        @Override
        public void close() {
            span.setAttribute("output", null);
            span.end();
        }
    }
}
