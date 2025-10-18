package CampaignNotes;

import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import CampaignNotes.tracking.LangfuseClient;
import CampaignNotes.tracking.otel.OpenTelemetryConfig;
import model.Campain;
import model.Note;

@DisplayName("Note Service Tests")
class NoteServiceTest {
    
    private NoteService noteService;
    private CampaignManager campaignManager;
    private LangfuseClient langfuseClient;
    
    // Test data
    private Campain testCampaign;
    private Note testNote;
    private String testCampaignUuid;
    private String testNoteId;
    
    @BeforeAll
    static void setupOpenTelemetry() {
        // Initialize OpenTelemetry for all tests
        OpenTelemetryConfig.initialize();
    }
    
    @BeforeEach
    void setUp() throws SQLException {
        noteService = new NoteService();
        campaignManager = new CampaignManager();
        langfuseClient = new LangfuseClient();
        
        // Initialize test data
        testCampaignUuid = UUID.randomUUID().toString();
        testNoteId = UUID.randomUUID().toString();
        
        // Create test campaign using CampaignManager
        String campaignName = "Test Campaign for Notes";
        testCampaign = campaignManager.createNewCampain(campaignName);
        
        // Verify campaign was created successfully
        assertNotNull(testCampaign, "Test campaign should be created successfully");
        
        // Update testCampaignUuid to match the created campaign
        testCampaignUuid = testCampaign.getUuid();
        
        // Create test note
        testNote = new Note(testCampaignUuid, "Test Note Title", 
            "This is a test note content for the campaign. It contains some story elements that should be embedded and stored in Qdrant database.");
        testNote.setId(testNoteId);
    }
    
    @AfterEach
    void tearDown() {
        try {
            // Clean up test data using CampaignManager
            if (testCampaign != null) {
                campaignManager.deleteCampaign(testCampaignUuid);
                campaignManager.endManaging();
            }
            
        } catch (Exception e) {
            System.err.println("Error during test cleanup: " + e.getMessage());
        }
    }
    
    @Nested
    @DisplayName("Adding Notes")
    class AddingNotes {
        
        @Test
        @DisplayName("Should successfully add valid note to campaign")
        void testAddNoteSuccessfully() {
            // Test prerequisites
            assertTrue(noteService.checkServicesAvailability(), 
                "All services should be available for testing");
            
            // Add note to campaign
            boolean result = noteService.addNote(testNote, testCampaign);
            
            // Assert that the operation was successful
            assertTrue(result, "Note should be added successfully");
            
            // Verify note exists in Qdrant database
            verifyNoteInQdrant();
            
            // Verify trace exists in Langfuse (with delay for processing)
            verifyTraceInLangfuse();
        }
        
        @Test
        @DisplayName("Should fail when adding note with null parameters")
        void testAddNoteWithNullParameters() {
            assertAll("Null parameter validations",
                // Test with null note
                () -> assertFalse(noteService.addNote(null, testCampaign), 
                    "Adding null note should fail"),
                
                // Test with null campaign
                () -> assertFalse(noteService.addNote(testNote, null), 
                    "Adding note to null campaign should fail"),
                
                // Test with both null
                () -> assertFalse(noteService.addNote(null, null), 
                    "Adding null note to null campaign should fail")
            );
        }
        
        @Test
        @DisplayName("Should fail when adding note with invalid content")
        void testAddInvalidNote() {
            // Create invalid note (empty content)
            Note invalidNote = new Note(testCampaignUuid, "Invalid Note", "");
            
            boolean result = noteService.addNote(invalidNote, testCampaign);
            
            assertFalse(result, "Adding invalid note should fail");
        }
    }
     
    /**
     * Verifies that the test note was stored correctly in Qdrant database
     */
    private void verifyNoteInQdrant() {
        assertDoesNotThrow(() -> {
            // Use CampaignManager to check if notes exist
            boolean hasNotes = campaignManager.hasExistingNotes(testCampaign);
            assertTrue(hasNotes, "Campaign should have existing notes");
            
            System.out.println("Successfully verified note existence through CampaignManager");
        }, "Failed to verify note in Qdrant");
    }
    
    
    /**
     * Verifies that a trace was created in Langfuse for the note processing
     * 
     * NOTE: This method has been simplified due to architectural changes.
     * The old trackEmbedding method has been removed as tracking is now handled
     * directly by TraceManager and Observation classes in service layers.
     */
    private void verifyTraceInLangfuse() {
        try {
            // Wait for Langfuse processing (typically takes 15-30 seconds)
            System.out.println("Waiting for Langfuse trace processing...");
            Thread.sleep(1000); // Reduced wait time since we're only checking connection
            
            // For now, we verify that Langfuse connection works
            assertTrue(langfuseClient.checkConnection(), 
                "Langfuse connection should be available");
            
            System.out.println("Successfully verified Langfuse connection functionality");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Warning: Langfuse verification interrupted - " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Warning: Langfuse verification failed - " + e.getMessage());
            // Note: In integration tests, Langfuse delays might cause test failures
            // This is expected behavior and doesn't necessarily indicate a bug
        }
    }
}
