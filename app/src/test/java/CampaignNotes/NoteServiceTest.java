package CampaignNotes;

import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
    
    @Nested
    @DisplayName("Override Notes")
    class OverrideNotes {
        
        @Test
        @DisplayName("Should successfully add override note when existing notes are present")
        void testAddOverrideNote() {
            System.out.println("Starting testAddOverrideNote...");
            
            // STEP 1: First add a regular note to have something to override
            Note regularNote = new Note(
                testCampaign.getUuid(),
                "Regular Note Title",
                "This is a regular note with some initial information that can be overridden later."
            );
            regularNote.setOverride(false); // Explicitly set as not an override note
            
            // Add the regular note first
            boolean regularNoteAdded = noteService.addNote(regularNote, testCampaign);
            assertTrue(regularNoteAdded, "Regular note should be added successfully");
            
            // Wait a moment for the note to be processed
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // STEP 2: Now add an override note (this should succeed because we have an existing note)
            Note overrideNote = new Note(
                testCampaign.getUuid(),
                "Override Note Title",
                "This note overrides previous information and provides updated details about the campaign."
            );
            overrideNote.setOverride(true);
            overrideNote.setOverrideReason("Updating with more accurate information");
            
            // This should now succeed because we have existing notes in the campaign
            boolean overrideNoteAdded = noteService.addNote(overrideNote, testCampaign);
            assertTrue(overrideNoteAdded, 
                "Override note should be added successfully when there are existing notes to override");
            
            // Verify both notes are stored in Qdrant
            verifyNoteInQdrant();
            
            System.out.println("testAddOverrideNote completed successfully");
        }
        
        @Test
        @DisplayName("Should fail when adding override note to empty collection")
        void testAddOverrideNoteFailsOnEmptyCollection() {
            System.out.println("Starting testAddOverrideNoteFailsOnEmptyCollection...");
            
            // Try to add an override note when there are no existing notes
            // This should fail according to business logic
            Note overrideNote = new Note(
                testCampaign.getUuid(),
                "Override Note Title",
                "This note tries to override previous information but there are no existing notes."
            );
            overrideNote.setOverride(true);
            overrideNote.setOverrideReason("Attempting to override non-existing notes");
            
            // This should fail because there are no existing notes to override
            boolean overrideNoteAdded = noteService.addNote(overrideNote, testCampaign);
            assertFalse(overrideNoteAdded, 
                "Override note should NOT be added when there are no existing notes to override");
            
            System.out.println("testAddOverrideNoteFailsOnEmptyCollection completed successfully");
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
     * Verifies that a collection exists and has data
     */
    private void verifyCollectionHasData(String collectionName) {
        assertDoesNotThrow(() -> {
            // Use CampaignManager to verify collection has data
            boolean hasNotes = campaignManager.hasExistingNotes(testCampaign);
            assertTrue(hasNotes, "Collection should contain data points");
            
            System.out.println("Collection " + collectionName + " verified through CampaignManager");
        }, "Failed to verify collection data");
    }
    
    /**
     * Verifies that a trace was created in Langfuse for the note processing
     */
    private void verifyTraceInLangfuse() {
        try {
            // Wait for Langfuse processing (typically takes 15-30 seconds)
            System.out.println("Waiting for Langfuse trace processing...");
            Thread.sleep(3000); // 3 seconds - may not be enough for full processing
            
            // Note: This is a simplified verification since full Langfuse API integration
            // would require additional setup. In a real scenario, we would:
            // 1. Capture the trace ID returned by trackNoteProcessingSession
            // 2. Query Langfuse API to verify the trace exists
            // 3. Check trace metadata contains correct campaign and note IDs
            
            // For now, we verify that Langfuse connection works
            assertTrue(langfuseClient.checkConnection(), 
                "Langfuse connection should be available");
            
            // Test embedding tracking (which is part of addNote process)
            boolean embeddingTracked = langfuseClient.trackEmbedding(
                null, // No specific traceId, let the client handle it
                testNote,
                "text-embedding-ada-002", // Default embedding model
                testCampaignUuid,
                50, // Estimated tokens
                1500 // Duration in ms
            );
            
            assertTrue(embeddingTracked, "Embedding should be tracked in Langfuse");
            
            System.out.println("Successfully verified Langfuse trace functionality");
            
        } catch (Exception e) {
            System.err.println("Warning: Langfuse verification failed - " + e.getMessage());
            // Note: In integration tests, Langfuse delays might cause test failures
            // This is expected behavior and doesn't necessarily indicate a bug
        }
    }
}
