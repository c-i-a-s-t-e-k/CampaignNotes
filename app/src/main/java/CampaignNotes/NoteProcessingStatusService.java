package CampaignNotes;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import CampaignNotes.dto.NoteCreateResponse;
import CampaignNotes.dto.NoteProcessingStatus;

/**
 * Service for managing note processing status with in-memory caching.
 * 
 * Uses Spring Cache with Caffeine backend to track async note processing status.
 * Statuses are cached for 10 minutes and automatically expired.
 */
@Service
public class NoteProcessingStatusService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NoteProcessingStatusService.class);
    
    @Autowired
    @Lazy
    private NoteProcessingStatusService self; // Self-inject to ensure proxy is used for internal calls

    /**
     * Retrieves the processing status for a note.
     * Returns cached status or creates a new "not_found" status if not in cache.
     * 
     * @param noteId the ID of the note
     * @return the processing status
     */
    @Cacheable(value = "noteProcessingStatus", key = "#noteId")
    public NoteProcessingStatus getStatus(String noteId) {
        // This part now correctly executes only on a real cache miss
        LOGGER.debug("[{}] Cache miss - creating and caching 'not_found' status", noteId);
        NoteProcessingStatus status = new NoteProcessingStatus();
        status.setNoteId(noteId);
        status.setStatus("not_found");
        return status;
    }
    
    /**
     * Updates the processing status for a note in cache.
     * 
     * @param status the updated status
     * @return the status that was stored
     */
    @CachePut(value = "noteProcessingStatus", key = "#status.noteId")
    public NoteProcessingStatus updateStatus(NoteProcessingStatus status) {
        LOGGER.info("[{}] Status updated: status={}, stage={}, progress={}%, result={}", 
            status.getNoteId(), 
            status.getStatus(),
            status.getStage(),
            status.getProgress(),
            status.getResult() != null ? "present" : "null");
        return status;
    }
    
    /**
     * Convenience method to update stage information with progress.
     * This method is synchronized to prevent race conditions from concurrent updates.
     * 
     * @param noteId the ID of the note
     * @param stage the current processing stage (e.g., "embedding", "nae", "are")
     * @param description human-readable stage description
     * @param progress progress percentage (0-100)
     */
    public synchronized void updateStage(String noteId, String stage, String description, int progress) {
        LOGGER.info("[{}] Updating stage: {} ({}%) - {}", noteId, stage, progress, description);
        // Call via self-proxy to trigger @Cacheable logic
        NoteProcessingStatus currentStatus = self.getStatus(noteId);
        
        // Defensive copy is a good practice to avoid modifying a shared cache object directly
        NoteProcessingStatus newStatus = new NoteProcessingStatus(currentStatus);
        newStatus.setStage(stage);
        newStatus.setStageDescription(description);
        newStatus.setProgress(progress);
        newStatus.setStatus("processing");

        // Call via self-proxy to trigger @CachePut logic
        self.updateStatus(newStatus);
        LOGGER.debug("[{}] Stage update completed", noteId);
    }
    
    /**
     * Atomically sets the result in the final status without overwriting concurrent updates.
     * This method is synchronized to ensure atomic updates.
     * 
     * @param noteId the ID of the note
     * @param result the NoteCreateResponse with deduplication info
     * @param success whether processing was successful
     * @param errorMessage error message if processing failed
     * @return the updated status
     */
    @CachePut(value = "noteProcessingStatus", key = "#noteId")
    public synchronized NoteProcessingStatus setCompletionResult(String noteId, NoteCreateResponse result, 
                                                     boolean success, String errorMessage) {
        // Call via self-proxy to trigger @Cacheable logic
        NoteProcessingStatus currentStatus = self.getStatus(noteId);

        // Work on a defensive copy
        NoteProcessingStatus newStatus = new NoteProcessingStatus(currentStatus);
        
        if (success) {
            newStatus.setStatus("completed");
            newStatus.setStage("completed");
            newStatus.setStageDescription("Notatka została przetworzona pomyślnie");
            newStatus.setProgress(100);
            newStatus.setResult(result);
            newStatus.setCompletedAt(LocalDateTime.now());
            LOGGER.info("[{}] Completion result set: requiresUserConfirmation={}, artifactCount={}, relationshipCount={}", 
                noteId,
                result.isRequiresUserConfirmation(),
                result.getArtifactCount(),
                result.getRelationshipCount());
        } else {
            newStatus.setStatus("failed");
            newStatus.setErrorMessage(errorMessage);
            newStatus.setCompletedAt(LocalDateTime.now());
            LOGGER.error("[{}] Processing failed: {}", noteId, errorMessage);
        }
        
        return newStatus;
    }
}

