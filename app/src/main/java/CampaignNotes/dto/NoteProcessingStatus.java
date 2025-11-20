package CampaignNotes.dto;

import java.time.LocalDateTime;

/**
 * DTO for tracking the status of asynchronous note processing.
 * 
 * Used to provide real-time progress updates to the frontend via polling.
 * Status values: pending, processing, completed, failed
 */
public class NoteProcessingStatus {
    
    private String noteId;
    private String status; // "pending", "processing", "completed", "failed"
    private String stage;  // "embedding", "nae", "are", "deduplication", "saving"
    private String stageDescription;
    private Integer progress; // 0-100
    private NoteCreateResponse result; // only for completed
    private String errorMessage; // only for failed
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    
    // Constructors
    public NoteProcessingStatus() {
    }
    
    public NoteProcessingStatus(String noteId) {
        this.noteId = noteId;
        this.status = "pending";
        this.progress = 0;
        this.startedAt = LocalDateTime.now();
    }
    
    /**
     * Copy constructor for creating a defensive copy.
     * 
     * @param other the object to copy from
     */
    public NoteProcessingStatus(NoteProcessingStatus other) {
        this.noteId = other.noteId;
        this.status = other.status;
        this.stage = other.stage;
        this.stageDescription = other.stageDescription;
        this.progress = other.progress;
        this.result = other.result;
        this.errorMessage = other.errorMessage;
        this.startedAt = other.startedAt;
        this.completedAt = other.completedAt;
    }
    
    // Getters and Setters
    public String getNoteId() {
        return noteId;
    }
    
    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getStage() {
        return stage;
    }
    
    public void setStage(String stage) {
        this.stage = stage;
    }
    
    public String getStageDescription() {
        return stageDescription;
    }
    
    public void setStageDescription(String stageDescription) {
        this.stageDescription = stageDescription;
    }
    
    public Integer getProgress() {
        return progress;
    }
    
    public void setProgress(Integer progress) {
        this.progress = progress;
    }
    
    public NoteCreateResponse getResult() {
        return result;
    }
    
    public void setResult(NoteCreateResponse result) {
        this.result = result;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public LocalDateTime getStartedAt() {
        return startedAt;
    }
    
    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}

