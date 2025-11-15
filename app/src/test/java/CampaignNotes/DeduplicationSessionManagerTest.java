package CampaignNotes;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import CampaignNotes.DeduplicationSessionManager.DeduplicationSession;
import model.Artifact;
import model.Relationship;

/**
 * Unit tests for DeduplicationSessionManager.
 */
public class DeduplicationSessionManagerTest {
    
    private DeduplicationSessionManager sessionManager;
    
    @BeforeEach
    public void setUp() {
        sessionManager = DeduplicationSessionManager.getInstance();
        sessionManager.clearAllSessions();
    }
    
    @AfterEach
    public void tearDown() {
        sessionManager.clearAllSessions();
    }
    
    @Test
    public void testCreateSession() {
        // Arrange
        String noteId = "test-note-1";
        String campaignUuid = "test-campaign-1";
        List<Artifact> artifacts = new ArrayList<>();
        artifacts.add(new Artifact("Test Artifact", "Character", campaignUuid, noteId));
        List<Relationship> relationships = new ArrayList<>();
        
        // Act
        DeduplicationSession session = sessionManager.createSession(noteId, campaignUuid, artifacts, relationships);
        
        // Assert
        assertNotNull(session);
        assertEquals(noteId, session.getNoteId());
        assertEquals(campaignUuid, session.getCampaignUuid());
        assertEquals(1, session.getPendingArtifacts().size());
        assertEquals(0, session.getPendingRelationships().size());
        assertFalse(session.isExpired());
    }
    
    @Test
    public void testGetSession() {
        // Arrange
        String noteId = "test-note-2";
        String campaignUuid = "test-campaign-2";
        List<Artifact> artifacts = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();
        
        sessionManager.createSession(noteId, campaignUuid, artifacts, relationships);
        
        // Act
        DeduplicationSession retrievedSession = sessionManager.getSession(noteId);
        
        // Assert
        assertNotNull(retrievedSession);
        assertEquals(noteId, retrievedSession.getNoteId());
        assertEquals(campaignUuid, retrievedSession.getCampaignUuid());
    }
    
    @Test
    public void testGetNonExistentSession() {
        // Act
        DeduplicationSession session = sessionManager.getSession("non-existent");
        
        // Assert
        assertNull(session);
    }
    
    @Test
    public void testRemoveSession() {
        // Arrange
        String noteId = "test-note-3";
        String campaignUuid = "test-campaign-3";
        List<Artifact> artifacts = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();
        
        sessionManager.createSession(noteId, campaignUuid, artifacts, relationships);
        assertTrue(sessionManager.hasValidSession(noteId));
        
        // Act
        boolean removed = sessionManager.removeSession(noteId);
        
        // Assert
        assertTrue(removed);
        assertFalse(sessionManager.hasValidSession(noteId));
    }
    
    @Test
    public void testSessionExpiration() {
        // Arrange
        String noteId = "test-note-4";
        String campaignUuid = "test-campaign-4";
        List<Artifact> artifacts = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();
        
        // Create session with past expiration time
        DeduplicationSession session = new DeduplicationSession(
            noteId, 
            campaignUuid, 
            artifacts, 
            relationships, 
            LocalDateTime.now().minusMinutes(1) // Already expired
        );
        
        // Act & Assert
        assertTrue(session.isExpired());
        assertEquals(0, session.getTimeToLiveMinutes());
    }
    
    @Test
    public void testActiveSessionCount() {
        // Arrange
        sessionManager.clearAllSessions();
        assertEquals(0, sessionManager.getActiveSessionCount());
        
        // Act
        sessionManager.createSession("note-1", "campaign-1", new ArrayList<>(), new ArrayList<>());
        sessionManager.createSession("note-2", "campaign-2", new ArrayList<>(), new ArrayList<>());
        
        // Assert
        assertEquals(2, sessionManager.getActiveSessionCount());
    }
    
    @Test
    public void testClearAllSessions() {
        // Arrange
        sessionManager.createSession("note-1", "campaign-1", new ArrayList<>(), new ArrayList<>());
        sessionManager.createSession("note-2", "campaign-2", new ArrayList<>(), new ArrayList<>());
        assertEquals(2, sessionManager.getActiveSessionCount());
        
        // Act
        sessionManager.clearAllSessions();
        
        // Assert
        assertEquals(0, sessionManager.getActiveSessionCount());
    }
    
    @Test
    public void testSessionImmutability() {
        // Arrange
        String noteId = "test-note-5";
        String campaignUuid = "test-campaign-5";
        List<Artifact> artifacts = new ArrayList<>();
        artifacts.add(new Artifact("Artifact 1", "Character", campaignUuid, noteId));
        
        // Act
        DeduplicationSession session = sessionManager.createSession(noteId, campaignUuid, artifacts, new ArrayList<>());
        List<Artifact> retrievedArtifacts = session.getPendingArtifacts();
        retrievedArtifacts.add(new Artifact("Artifact 2", "Location", campaignUuid, noteId));
        
        // Assert - original session should not be modified
        assertEquals(1, session.getPendingArtifacts().size());
    }
}

