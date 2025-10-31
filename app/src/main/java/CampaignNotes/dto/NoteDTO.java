package CampaignNotes.dto;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for Note information.
 * Contains full note details for API responses.
 */
public class NoteDTO {
    private String id;
    private String campaignUuid;
    private String title;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int wordCount;
    
    public NoteDTO() {}
    
    public NoteDTO(String id, String campaignUuid, String title, String content, 
                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.campaignUuid = campaignUuid;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.wordCount = content != null ? content.trim().split("\\s+").length : 0;
    }
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getCampaignUuid() {
        return campaignUuid;
    }
    
    public void setCampaignUuid(String campaignUuid) {
        this.campaignUuid = campaignUuid;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
        this.wordCount = content != null ? content.trim().split("\\s+").length : 0;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public int getWordCount() {
        return wordCount;
    }
    
    public void setWordCount(int wordCount) {
        this.wordCount = wordCount;
    }
}

