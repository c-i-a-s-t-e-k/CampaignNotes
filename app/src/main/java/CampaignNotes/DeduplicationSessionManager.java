package CampaignNotes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import model.Artifact;
import model.Relationship;

/**
 * Manages temporary deduplication sessions in memory.
 * Sessions have a TTL of 20 minutes and are automatically cleaned up.
 * Thread-safe implementation using ConcurrentHashMap.
 */
public class DeduplicationSessionManager {
    
    private static final int SESSION_TTL_MINUTES = 20;
    private final Map<String, DeduplicationSession> sessions = new ConcurrentHashMap<>();
    private static DeduplicationSessionManager instance;
    
    /**
     * Private constructor for singleton pattern.
     */
    private DeduplicationSessionManager() {
        // Start background cleanup thread
        startCleanupThread();
    }
    
    /**
     * Get singleton instance of DeduplicationSessionManager.
     * 
     * @return the singleton instance
     */
    public static synchronized DeduplicationSessionManager getInstance() {
        if (instance == null) {
            instance = new DeduplicationSessionManager();
        }
        return instance;
    }
    
    /**
     * Creates a new deduplication session.
     * 
     * @param noteId the note ID (used as session ID)
     * @param campaignUuid the campaign UUID
     * @param artifacts pending artifacts awaiting user confirmation
     * @param relationships pending relationships awaiting user confirmation
     * @return the created session
     */
    public DeduplicationSession createSession(String noteId, String campaignUuid,
                                              List<Artifact> artifacts, 
                                              List<Relationship> relationships) {
        if (noteId == null || noteId.trim().isEmpty()) {
            throw new IllegalArgumentException("Note ID cannot be null or empty");
        }
        
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(SESSION_TTL_MINUTES);
        DeduplicationSession session = new DeduplicationSession(
            noteId, campaignUuid, artifacts, relationships, expiresAt);
        
        sessions.put(noteId, session);
        System.out.println("Created deduplication session for note: " + noteId + 
                         " with " + artifacts.size() + " artifacts and " + 
                         relationships.size() + " relationships");
        
        return session;
    }
    
    /**
     * Retrieves a session by note ID.
     * Returns null if session doesn't exist or has expired.
     * 
     * @param noteId the note ID (session ID)
     * @return the session or null if not found/expired
     */
    public DeduplicationSession getSession(String noteId) {
        if (noteId == null || noteId.trim().isEmpty()) {
            return null;
        }
        
        DeduplicationSession session = sessions.get(noteId);
        
        // Check if session exists and is not expired
        if (session != null && session.isExpired()) {
            sessions.remove(noteId);
            System.out.println("Session expired and removed: " + noteId);
            return null;
        }
        
        return session;
    }
    
    /**
     * Removes a session from memory.
     * 
     * @param noteId the note ID (session ID)
     * @return true if session was removed, false if it didn't exist
     */
    public boolean removeSession(String noteId) {
        if (noteId == null || noteId.trim().isEmpty()) {
            return false;
        }
        
        DeduplicationSession removed = sessions.remove(noteId);
        if (removed != null) {
            System.out.println("Removed deduplication session: " + noteId);
            return true;
        }
        return false;
    }
    
    /**
     * Checks if a session exists and is valid (not expired).
     * 
     * @param noteId the note ID (session ID)
     * @return true if session exists and is valid
     */
    public boolean hasValidSession(String noteId) {
        return getSession(noteId) != null;
    }
    
    /**
     * Gets the number of active sessions.
     * 
     * @return number of sessions in memory
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
    
    /**
     * Clears all sessions (useful for testing).
     */
    public void clearAllSessions() {
        sessions.clear();
        System.out.println("Cleared all deduplication sessions");
    }
    
    /**
     * Starts a background thread that periodically cleans up expired sessions.
     */
    private void startCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60000); // Run every minute
                    cleanupExpiredSessions();
                } catch (InterruptedException e) {
                    System.err.println("Cleanup thread interrupted: " + e.getMessage());
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.setName("DeduplicationSessionCleanup");
        cleanupThread.start();
    }
    
    /**
     * Removes all expired sessions from memory.
     */
    private void cleanupExpiredSessions() {
        List<String> expiredKeys = new ArrayList<>();
        
        for (Map.Entry<String, DeduplicationSession> entry : sessions.entrySet()) {
            if (entry.getValue().isExpired()) {
                expiredKeys.add(entry.getKey());
            }
        }
        
        for (String key : expiredKeys) {
            sessions.remove(key);
        }
        
        if (!expiredKeys.isEmpty()) {
            System.out.println("Cleaned up " + expiredKeys.size() + " expired sessions");
        }
    }
    
    /**
     * Represents a temporary deduplication session.
     */
    public static class DeduplicationSession {
        private final String noteId;
        private final String campaignUuid;
        private final List<Artifact> pendingArtifacts;
        private final List<Relationship> pendingRelationships;
        private final LocalDateTime expiresAt;
        private final LocalDateTime createdAt;
        
        public DeduplicationSession(String noteId, String campaignUuid,
                                   List<Artifact> artifacts, 
                                   List<Relationship> relationships,
                                   LocalDateTime expiresAt) {
            this.noteId = noteId;
            this.campaignUuid = campaignUuid;
            this.pendingArtifacts = new ArrayList<>(artifacts != null ? artifacts : new ArrayList<>());
            this.pendingRelationships = new ArrayList<>(relationships != null ? relationships : new ArrayList<>());
            this.expiresAt = expiresAt;
            this.createdAt = LocalDateTime.now();
        }
        
        public String getNoteId() {
            return noteId;
        }
        
        public String getCampaignUuid() {
            return campaignUuid;
        }
        
        public List<Artifact> getPendingArtifacts() {
            return new ArrayList<>(pendingArtifacts);
        }
        
        public List<Relationship> getPendingRelationships() {
            return new ArrayList<>(pendingRelationships);
        }
        
        public LocalDateTime getExpiresAt() {
            return expiresAt;
        }
        
        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }
        
        public long getTimeToLiveMinutes() {
            if (isExpired()) {
                return 0;
            }
            return java.time.Duration.between(LocalDateTime.now(), expiresAt).toMinutes();
        }
        
        @Override
        public String toString() {
            return String.format("DeduplicationSession[noteId=%s, campaignUuid=%s, artifacts=%d, relationships=%d, expiresAt=%s]",
                    noteId, campaignUuid, pendingArtifacts.size(), pendingRelationships.size(), expiresAt);
        }
    }
}

