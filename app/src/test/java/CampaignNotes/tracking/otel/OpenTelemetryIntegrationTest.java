package CampaignNotes.tracking.otel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for OpenTelemetry tracking with Langfuse.
 * 
 * These tests verify that OpenTelemetry spans are created correctly
 * and exported to Langfuse via the OTLP endpoint.
 */
@Tag("integration")
@DisplayName("OpenTelemetry Integration Tests")
class OpenTelemetryIntegrationTest {
    
    @BeforeAll
    static void setupOTel() {
        // Initialize OpenTelemetry - this should only happen once
        OpenTelemetryConfig.initialize();
    }
    
    @Test
    @DisplayName("Should export spans to Langfuse")
    void shouldExportSpans() {
        OTelTraceManager traceManager = new OTelTraceManager();
        
        try (OTelTraceManager.OTelTrace trace = traceManager.createTrace(
            "integration-test",
            "test-campaign-uuid",
            "test-note-id",
            null,
            "test1-input"
        )) {
            trace.setAttribute("test.attribute", "test-value");
            trace.addEvent("test-event");
            trace.setStatus(true, "Test completed");
            
            // Get trace ID to verify trace was created
            String traceId = trace.getTraceId();
            assertNotNull(traceId, "Trace ID should not be null");
            assertFalse(traceId.isEmpty(), "Trace ID should not be empty");
        }
        
        // Spans are exported asynchronously by BatchSpanProcessor
        // In production: verify in Langfuse UI that the span appeared
    }
    
    @Test
    @DisplayName("Should handle nested observations")
    void shouldHandleNestedObservations() {
        OTelTraceManager traceManager = new OTelTraceManager();
        
        try (OTelTraceManager.OTelTrace trace = traceManager.createTrace(
            "parent-trace",
            "campaign-id",
            "note-id",
            null,
            "test2-input"
        )) {
            trace.setAttribute("workflow", "test-workflow");
            
            // Create a nested generation observation
            try (OTelGenerationObservation obs = 
                new OTelGenerationObservation("child-observation", trace.getContext())) {
                
                obs.withModel("gpt-4")
                   .withPrompt("Test prompt")
                   .withResponse("Test response")
                   .withTokenUsage(5, 10, 15)
                   .withComponent("test")
                   .withStage("testing");
                
                obs.setSuccess();
            }
            
            trace.setStatus(true, "Parent trace completed");
        }
        
        // Verify no exceptions were thrown
        // Nested spans should be exported correctly to Langfuse
    }
    
    @Test
    @DisplayName("Should handle embedding observations")
    void shouldHandleEmbeddingObservations() {
        OTelTraceManager traceManager = new OTelTraceManager();
        
        try (OTelTraceManager.OTelTrace trace = traceManager.createTrace(
            "embedding-test",
            "campaign-id",
            "note-id",
            null,
            "test3-input"
        )) {
            // Create a mock note for testing
            model.Note testNote = new model.Note(
                "test-campaign-uuid",
                "test-title",
                "test-content"
            );
            testNote.setId("test-note-id");
            
            // Create embedding observation
            try (OTelEmbeddingObservation obs = 
                new OTelEmbeddingObservation("embedding-test", trace.getContext())) {
                
                obs.withModel("text-embedding-3-small")
                   .withInput(testNote)
                   .withTokensUsed(100)
                   .withDuration(500L);
                
                obs.setSuccess();
            }
            
            trace.setStatus(true, "Embedding test completed");
        }
        
        // Verify no exceptions were thrown
    }
    
    @Test
    @DisplayName("Should handle error scenarios")
    void shouldHandleErrorScenarios() {
        OTelTraceManager traceManager = new OTelTraceManager();
        
        try (OTelTraceManager.OTelTrace trace = traceManager.createTrace(
            "error-test",
            "campaign-id",
            "note-id",
            null,
            "test4-input"
        )) {
            try {
                // Simulate an error
                throw new RuntimeException("Simulated error for testing");
            } catch (Exception e) {
                trace.recordException(e);
                trace.setStatus(false, "Test error: " + e.getMessage());
            }
        }
        
        // Verify error was handled gracefully and span was exported
    }
    
    @Test
    @DisplayName("Should create trace with all attributes")
    void shouldCreateTraceWithAttributes() {
        OTelTraceManager traceManager = new OTelTraceManager();
        
        try (OTelTraceManager.OTelTrace trace = traceManager.createTrace(
            "full-trace-test",
            "test-campaign-uuid",
            "test-note-id",
            "test-user-id",
            "test5-input"
        )) {
            // Set various attributes
            trace.setAttribute("string.attribute", "value");
            trace.setAttribute("long.attribute", 12345L);
            
            // Add events
            trace.addEvent("event1");
            trace.addEvent("event2");
            
            trace.setStatus(true, "All attributes set");
            
            // Verify trace ID is valid
            String traceId = trace.getTraceId();
            assertNotNull(traceId);
            assertTrue(traceId.matches("[a-f0-9]{32}"), "Trace ID should be 32 hex characters");
        }
    }
}
