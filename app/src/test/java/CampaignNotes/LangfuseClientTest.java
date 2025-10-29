package CampaignNotes;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import CampaignNotes.tracking.LangfuseClient;

@Tag("integration")
@DisplayName("Langfuse Client Tests")
class LangfuseClientTest {
    private LangfuseClient client;

    @BeforeEach
    void setUp() {
        client = new LangfuseClient();
    }

    @Test
    @DisplayName("Should successfully connect to Langfuse")
    void connectionToLangfuseTest() {
        assertTrue(client.checkConnection(), "Langfuse connection should be available");
    }

    @Test
    @DisplayName("Should successfully track embedding with all parameters")
    @Disabled("This test relies on a legacy method that has been removed.")
    void trackEmbeddingTest() {
        // This test is disabled because it uses a legacy signature.
        // If needed, it should be updated to create a Note object and use TraceManager.
        
        assertTrue(true, "Test disabled - functionality moved to TraceManager");
    }

    @Test
    @DisplayName("Should successfully track embedding for a standard Note object")
    @Disabled("This test uses the legacy trackEmbedding method that has been removed. " +
              "Embedding tracking is now handled directly by TraceManager and EmbedingObservation classes.")
    void trackEmbeddingWithStandardNoteTest() {
        // This test has been disabled because the trackEmbedding method has been removed
        // as part of the architectural refactoring. Embedding tracking is now handled
        // directly by TraceManager using EmbedingTrace and EmbedingObservation classes.
        
        // For testing embedding functionality, create tests that use:
        // 1. TraceManager.createTraceByType(TraceType.EMBEDDING_TRACE)
        // 2. EmbedingObservation with proper builder pattern
        // 3. Trace.addObservation() for async processing
        
        assertTrue(true, "Test disabled - functionality moved to TraceManager");
    }

    @Test
    @DisplayName("Should successfully track embedding for an override Note object")
    @Disabled("This test uses the legacy trackEmbedding method that has been removed. " +
              "Embedding tracking is now handled directly by TraceManager and EmbedingObservation classes.")
    void trackEmbeddingWithOverrideNoteTest() {
        // This test has been disabled because the trackEmbedding method has been removed
        // as part of the architectural refactoring. Embedding tracking is now handled
        // directly by TraceManager using EmbedingTrace and EmbedingObservation classes.
        
        assertTrue(true, "Test disabled - functionality moved to TraceManager");
    }

    @Test
    @DisplayName("Should successfully track note processing session and return trace ID")
    @Timeout(30)
    @Disabled("This test uses the legacy trackNoteProcessingSession method that has been removed. " +
              "Session tracking is now handled directly by TraceManager and ArtefactRelationTrace classes.")
    void trackNoteProcessingSessionTest() {
        // This test has been disabled because the trackNoteProcessingSession method has been removed
        // as part of the architectural refactoring. Session tracking is now handled
        // directly by TraceManager using ArtefactRelationTrace for multi-step workflows.
        
        assertTrue(true, "Test disabled - functionality moved to TraceManager");
    }

    @Test
    @DisplayName("Should successfully retrieve trace and validate its content")
    @Timeout(30)
    @Disabled("This test uses legacy methods trackNoteProcessingSession and getTrace that have been removed. " +
              "Trace retrieval is now handled through direct HTTP client access.")
    void getTraceTest() {
        // This test has been disabled because both trackNoteProcessingSession and getTrace methods
        // have been removed as part of the architectural refactoring. 
        // Trace creation and retrieval is now handled directly by TraceManager and LangfuseHttpClient.
        
        // For testing trace retrieval functionality, create tests that use:
        // 1. TraceManager to create traces
        // 2. LangfuseHttpClient.get() method for direct API access
        // 3. Proper JSON parsing for response validation
        
        assertTrue(true, "Test disabled - functionality moved to TraceManager and HttpClient");
    }
    

    @Test
    @DisplayName("Should return null for non-existent trace ID")
    @Disabled("This test uses the legacy getTrace method that has been removed. " +
              "Direct trace retrieval is now handled through LangfuseHttpClient.")
    void getTraceNotFoundTest() {
        // This test has been disabled because the getTrace method has been removed
        // as part of the architectural refactoring. Direct trace retrieval is now handled
        // through LangfuseHttpClient.get() method.
        
        assertTrue(true, "Test disabled - functionality moved to HttpClient");
    }
}
