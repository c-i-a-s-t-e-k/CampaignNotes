package CampaignNotes;

import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import CampaignNotes.dto.NoteProcessingStatus;

/**
 * Service for managing note processing status with in-memory caching.
 * 
 * Uses Spring Cache with Caffeine backend to track async note processing status.
 * Statuses are cached for 10 minutes and automatically expired.
 */
@Service
public class NoteProcessingStatusService {
    
    /**
     * Retrieves the processing status for a note.
     * Returns cached status or creates a new "not_found" status if not in cache.
     * 
     * @param noteId the ID of the note
     * @return the processing status
     */
    @Cacheable(value = "noteProcessingStatus", key = "#noteId")
    public NoteProcessingStatus getStatus(String noteId) {
        // Cache miss - return not found status
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
        return status;
    }
    
    /**
     * Convenience method to update stage information with progress.
     * 
     * @param noteId the ID of the note
     * @param stage the current processing stage (e.g., "embedding", "nae", "are")
     * @param description human-readable stage description
     * @param progress progress percentage (0-100)
     */
    public void updateStage(String noteId, String stage, String description, int progress) {
        NoteProcessingStatus status = getStatus(noteId);
        status.setStage(stage);
        status.setStageDescription(description);
        status.setProgress(progress);
        status.setStatus("processing");
        updateStatus(status);
    }
}

