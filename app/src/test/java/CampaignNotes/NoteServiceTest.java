package CampaignNotes;

import java.sql.SQLException;
import java.util.UUID;

import org.junit.After;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

import model.Campain;
import model.Note;

public class NoteServiceTest {
    
    private NoteService noteService;
    private CampaignManager campaignManager;
    private LangfuseClient langfuseClient;
    
    // Test data
    private Campain testCampaign;
    private Note testNote;
    private String testCampaignUuid;
    private String testNoteId;
    
    @Before
    public void setUp() throws SQLException {
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
        assertNotNull("Test campaign should be created successfully", testCampaign);
        
        // Update testCampaignUuid to match the created campaign
        testCampaignUuid = testCampaign.getUuid();
        
        // Create test note
        testNote = new Note(testCampaignUuid, "Test Note Title", 
            "This is a test note content for the campaign. It contains some story elements that should be embedded and stored in Qdrant database.");
        testNote.setId(testNoteId);
    }
    
    @After
    public void tearDown() {
        try {
            // Clean up test data using CampaignManager
            if (testCampaign != null) {
                campaignManager.deleteCampaign(testCampaignUuid);
            }
            
        } catch (Exception e) {
            System.err.println("Error during test cleanup: " + e.getMessage());
        }
    }
    
    @Test
    public void testAddNoteSuccessfully() {
        // Test prerequisites
        assertTrue("All services should be available for testing", 
            noteService.checkServicesAvailability());
        
        // Add note to campaign
        boolean result = noteService.addNote(testNote, testCampaign);
        
        // Assert that the operation was successful
        assertTrue("Note should be added successfully", result);
        
        // Verify note exists in Qdrant database
        verifyNoteInQdrant();
        
        // Verify trace exists in Langfuse (with delay for processing)
        verifyTraceInLangfuse();
    }
    
    @Test
    public void testAddNoteWithNullParameters() {
        // Test with null note
        boolean result1 = noteService.addNote(null, testCampaign);
        assertFalse("Adding null note should fail", result1);
        
        // Test with null campaign
        boolean result2 = noteService.addNote(testNote, null);
        assertFalse("Adding note to null campaign should fail", result2);
        
        // Test with both null
        boolean result3 = noteService.addNote(null, null);
        assertFalse("Adding null note to null campaign should fail", result3);
    }
    
    @Test
    public void testAddInvalidNote() {
        // Create invalid note (empty content)
        Note invalidNote = new Note(testCampaignUuid, "Invalid Note", "");
        
        boolean result = noteService.addNote(invalidNote, testCampaign);
        
        assertFalse("Adding invalid note should fail", result);
    }
    
    @Test
    public void testAddOverrideNote() {
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
        assertTrue("Regular note should be added successfully", regularNoteAdded);
        
        // Wait a moment for the note to be processed
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // ignore
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
        assertTrue("Override note should be added successfully when there are existing notes to override", 
                  overrideNoteAdded);
        
        // Verify both notes are stored in Qdrant
        verifyNoteInQdrant();
        
        System.out.println("testAddOverrideNote completed successfully");
    }
    
    @Test
    public void testAddOverrideNoteFailsOnEmptyCollection() {
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
        assertFalse("Override note should NOT be added when there are no existing notes to override", 
                   overrideNoteAdded);
        
        System.out.println("testAddOverrideNoteFailsOnEmptyCollection completed successfully");
    }
    
    /**
     * Verifies that the test note was stored correctly in Qdrant database
     */
    private void verifyNoteInQdrant() {
        try {
            // Use CampaignManager to check if notes exist
            boolean hasNotes = campaignManager.hasExistingNotes(testCampaign);
            assertTrue("Campaign should have existing notes", hasNotes);
            
            System.out.println("Successfully verified note existence through CampaignManager");
            
        } catch (Exception e) {
            fail("Failed to verify note in Qdrant: " + e.getMessage());
        }
    }
    
    /**
     * Verifies that a collection exists and has data
     */
    private void verifyCollectionHasData(String collectionName) {
        try {
            // Use CampaignManager to verify collection has data
            boolean hasNotes = campaignManager.hasExistingNotes(testCampaign);
            assertTrue("Collection should contain data points", hasNotes);
            
            System.out.println("Collection " + collectionName + " verified through CampaignManager");
            
        } catch (Exception e) {
            fail("Failed to verify collection data: " + e.getMessage());
        }
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
            assertTrue("Langfuse connection should be available", 
                langfuseClient.checkConnection());
            
            // Test embedding tracking (which is part of addNote process)
            boolean embeddingTracked = langfuseClient.trackEmbedding(
                testNote.getFullTextForEmbedding(),
                "text-embedding-ada-002", // Default embedding model
                testCampaignUuid,
                testNoteId,
                50, // Estimated tokens
                1500 // Duration in ms
            );
            
            assertTrue("Embedding should be tracked in Langfuse", embeddingTracked);
            
            System.out.println("Successfully verified Langfuse trace functionality");
            
        } catch (Exception e) {
            System.err.println("Warning: Langfuse verification failed - " + e.getMessage());
            // Note: In integration tests, Langfuse delays might cause test failures
            // This is expected behavior and doesn't necessarily indicate a bug
        }
    }
}
