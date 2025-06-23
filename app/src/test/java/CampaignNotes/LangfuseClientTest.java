package CampaignNotes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.google.gson.JsonObject;

import model.Note;

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
        // Test parameters
        String input = "This is a test note content for embedding";
        String model = "text-embedding-ada-002";
        String campaignId = "12345678-1234-1234-1234-123456789abc";
        String noteId = "test-note-001";
        int tokensUsed = 10;
        long durationMs = 1500;

        // This test is disabled because it uses a legacy signature.
        // If needed, it should be updated to create a Note object.
        // boolean result = client.trackEmbedding(null, new Note(campaignId, "Test Title", input), model, campaignId, tokensUsed, durationMs,
        //         Arrays.asList("source:testing-function", "test:unit-test"));
        
        // For now, we assert true to keep the structure, but the @Disabled annotation is the key.
        assertTrue(true);
    }

    @Test
    @DisplayName("Should successfully track embedding for a standard Note object")
    void trackEmbeddingWithStandardNoteTest() {
        // Tworzymy standardową notatkę
        Note standardNote = new Note("campaign-uuid-std", "Standard Note Title", "This is the content of a standard note.");
        String model = "text-embedding-ada-002"; // Model z prawdopodobną wyceną
        int tokensUsed = 25;
        long durationMs = 800;

        // Wywołujemy metodę z obiektem Note, traceId=null
        boolean result = client.trackEmbedding(null, standardNote, model, standardNote.getCampaignUuid(),
                tokensUsed, durationMs, Arrays.asList("test-case:standard-note", "emptyTrace"));

        // Sprawdzamy, czy śledzenie zakończyło się sukcesem
        assertTrue(result, "Embedding tracking for a standard Note object should be successful");
    }

    @Test
    @DisplayName("Should successfully track embedding for an override Note object")
    void trackEmbeddingWithOverrideNoteTest() {
        // Tworzymy notatkę typu override
        Note overrideNote = new Note("campaign-uuid-override", "Override Note Title",
                "This content overrides a previous note.", "Reason for overriding");
        String model = "text-embedding-3-small";
        int tokensUsed = 30;
        long durationMs = 950;

        // Wywołujemy metodę z obiektem Note, traceId=null
        boolean result = client.trackEmbedding(null, overrideNote, model, overrideNote.getCampaignUuid(),
                tokensUsed, durationMs, Arrays.asList("test-case:override-note", "priority:high", "emptyTrace"));

        // Sprawdzamy, czy śledzenie zakończyło się sukcesem
        assertTrue(result, "Embedding tracking for an override Note object should be successful");
    }

    @Test
    @DisplayName("Should successfully track note processing session and return trace ID")
    @Timeout(30)
    void trackNoteProcessingSessionTest() {
        // Test parameters
        String sessionName = "test-note-processing";
        String campaignId = "87654321-4321-4321-4321-abcdef123456";
        String noteId = "test-note-session-002";
        String userId = "test-user-123";

        // Test the trackNoteProcessingSession method
        String traceId = client.trackNoteProcessingSession(sessionName, campaignId, noteId, userId,
                Arrays.asList("source:testing-function", "test:unit-test"));

        // Assert that tracking was successful and we got a trace ID
        assertAll("Trace ID validation",
                () -> assertNotNull(traceId, "Should receive a trace ID"),
                () -> assertFalse(traceId.trim().isEmpty(), "Trace ID should not be empty")
        );
    }

    @Test
    @DisplayName("Should successfully retrieve trace and validate its content")
    @Timeout(30)
    void getTraceTest() throws InterruptedException, ExecutionException {
        // Najpierw sprawdzamy czy połączenie z Langfuse działa
        assertTrue(client.checkConnection(), "Langfuse connection must be available for this test");
        
        // Tworzymy trace
        String sessionName = "test-get-trace-session";
        String campaignId = "11111111-2222-3333-4444-555555555555";
        String noteId = "test-note-get-trace";
        String userId = "test-user-get-trace";
        
        String traceId = client.trackNoteProcessingSession(sessionName, campaignId, noteId, userId, 
            Arrays.asList("source:testing-function", "test:get-trace", "validation:content"));
        
        assertNotNull(traceId, "Should receive a trace ID from trackNoteProcessingSession");
        assertFalse(traceId.trim().isEmpty(), "Trace ID should not be empty");
        
        // Czekamy na propagację danych w Langfuse z retry logic
        JsonObject retrievedTrace = waitForTraceToBeAvailable(traceId, 5);

        // Test MUSI failować jeśli nie możemy pobrać trace'a po wszystkich próbach
        assertNotNull(retrievedTrace, 
            "Should successfully retrieve trace from Langfuse API after retries. " +
            "If this fails consistently, check API latency or increase retry attempts.");
        
        // Sprawdzamy podstawowe właściwości trace'a
        assertAll("Basic trace properties validation",
            () -> assertTrue(retrievedTrace.has("id"), "Trace should have id field"),
            () -> assertEquals(traceId, retrievedTrace.get("id").getAsString(), 
                "Trace ID should match the requested ID"),
            () -> assertTrue(retrievedTrace.has("name"), "Trace should have name field"),
            () -> assertEquals(sessionName, retrievedTrace.get("name").getAsString(), 
                "Trace name should match the created session name"),
            () -> assertFalse(retrievedTrace.get("userId").isJsonNull(), "Trace userId should not be null"),
            () -> assertEquals(userId, retrievedTrace.get("userId").getAsString(), "Trace userId should match the provided user ID")
        );
        
        // Sprawdzamy metadata - powinny istnieć i zawierać oczekiwane dane
        assertTrue(retrievedTrace.has("metadata"), "Trace should have metadata field");
        assertFalse(retrievedTrace.get("metadata").isJsonNull(), "Metadata should not be null");
        
        JsonObject metadata = retrievedTrace.getAsJsonObject("metadata");
        assertAll("Metadata validation",
            () -> assertTrue(metadata.has("campaign_id"), "Should contain campaign_id"),
            () -> assertTrue(metadata.has("note_id"), "Should contain note_id"),
            () -> assertTrue(metadata.has("user_id"), "Should contain user_id"),
            () -> assertEquals(campaignId, metadata.get("campaign_id").getAsString(), 
                "Campaign ID should match"),
            () -> assertEquals(noteId, metadata.get("note_id").getAsString(), 
                "Note ID should match"),
            () -> assertEquals(userId, metadata.get("user_id").getAsString(), 
                "User ID should match"),
            () -> assertTrue(metadata.has("system_component"), "Should contain system_component"),
            () -> assertEquals("note-processing", metadata.get("system_component").getAsString(), 
                "System component should be note-processing")
        );
        
        // Sprawdzamy tags - powinny istnieć i zawierać oczekiwane tagi
        assertTrue(retrievedTrace.has("tags"), "Trace should have tags field");
        assertFalse(retrievedTrace.get("tags").isJsonNull(), "Tags should not be null");
        
        String tags = retrievedTrace.get("tags").toString(); // Konwertujemy na string dla łatwego sprawdzania zawartości
        assertAll("Tags validation",
            () -> assertTrue(tags.contains("system:campaign-notes"), 
                "Should contain system:campaign-notes tag"),
            () -> assertTrue(tags.contains("workflow:note-processing"), 
                "Should contain workflow:note-processing tag"),
            () -> assertTrue(tags.contains("test:get-trace"), 
                "Should contain test:get-trace tag"),
            () -> assertTrue(tags.contains("campaign:" + campaignId.substring(0, 8)), 
                "Should contain campaign prefix tag")
        );
    }
    
    /**
     * Helper method to wait for trace to become available with retry logic
     */
    private JsonObject waitForTraceToBeAvailable(String traceId, int maxAttempts) 
            throws InterruptedException, ExecutionException {
        JsonObject retrievedTrace = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                // Czekamy przed kolejną próbą (exponential backoff)
                Thread.sleep(Math.min(1000 * attempt, 5000));
            }
            
            try {
                CompletableFuture<JsonObject> traceFuture = client.getTrace(traceId);
                retrievedTrace = traceFuture.get();
                
                if (retrievedTrace != null) {
                    System.out.println("Successfully retrieved trace on attempt " + attempt);
                    return retrievedTrace;
                }
                
                System.out.println("Attempt " + attempt + "/" + maxAttempts + ": Trace not yet available, retrying...");
                
            } catch (Exception e) {
                System.err.println("Attempt " + attempt + " failed: " + e.getMessage());
                if (attempt == maxAttempts) {
                    throw e; // Rzucamy wyjątek tylko przy ostatniej próbie
                }
            }
        }
        
        return retrievedTrace; // null jeśli nie udało się pobrać
    }

    @Test
    @DisplayName("Should return null for non-existent trace ID")
    void getTraceNotFoundTest() throws InterruptedException, ExecutionException {
        // Testujemy z nieistniejącym trace ID
        String nonExistentTraceId = "non-existent-trace-id-12345";
        
        CompletableFuture<JsonObject> traceFuture = client.getTrace(nonExistentTraceId);
        assertNotNull(traceFuture, "Should receive a CompletableFuture from getTrace");
        
        JsonObject result = traceFuture.get();
        
        // Sprawdzamy czy funkcja prawidłowo zwraca null dla nieistniejącego trace
        assertNull(result, "Should return null for non-existent trace ID");
    }
}
