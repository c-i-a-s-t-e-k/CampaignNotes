package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Model representing a campaign note.
 * Each note belongs to a specific campaign and contains content that can be embedded and stored in Qdrant.
 */
public class Note {
    private String id;
    private String campaignUuid;
    private String title;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean isOverride;
    private String overrideReason;
    private boolean isOverridden;
    private List<String> overriddenByNoteIds;
    
    /**
     * Default constructor
     */
    public Note() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.isOverride = false;
        this.isOverridden = false;
        this.overriddenByNoteIds = new ArrayList<>();
    }
    
    /**
     * Constructor with basic fields
     */
    public Note(String campaignUuid, String title, String content) {
        this();
        this.campaignUuid = campaignUuid;
        this.title = title;
        this.content = content;
    }
    
    /**
     * Constructor for override notes
     */
    public Note(String campaignUuid, String title, String content, String overrideReason) {
        this(campaignUuid, title, content);
        this.isOverride = true;
        this.overrideReason = overrideReason;
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
        this.updatedAt = LocalDateTime.now();
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
    
    public boolean isOverride() {
        return isOverride;
    }
    
    public void setOverride(boolean override) {
        this.isOverride = override;
    }
    
    public String getOverrideReason() {
        return overrideReason;
    }
    
    public void setOverrideReason(String overrideReason) {
        this.overrideReason = overrideReason;
    }
    
    public boolean isOverridden() {
        return isOverridden;
    }
    
    public void setOverridden(boolean overridden) {
        this.isOverridden = overridden;
    }
    
    public List<String> getOverriddenByNoteIds() {
        return new ArrayList<>(overriddenByNoteIds);
    }
    
    public void addOverriddenByNoteId(String noteId) {
        if (!overriddenByNoteIds.contains(noteId)) {
            overriddenByNoteIds.add(noteId);
            this.isOverridden = true;
            this.updatedAt = LocalDateTime.now();
        }
    }
    
    public void removeOverriddenByNoteId(String noteId) {
        overriddenByNoteIds.remove(noteId);
        if (overriddenByNoteIds.isEmpty()) {
            this.isOverridden = false;
        }
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Validates the note content according to business rules.
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        // Check word count limit (500 words as per PRD)
        String[] words = content.trim().split("\\s+");
        if (words.length > 500) {
            return false;
        }
        
        return title != null && !title.trim().isEmpty() && 
               campaignUuid != null && !campaignUuid.trim().isEmpty();
    }
    
    /**
     * Gets the full text content for embedding (title + content)
     * @return combined text for embedding
     */
    public String getFullTextForEmbedding() {
        return title + " " + content;
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s - %s (%s words, override: %b, overridden: %b)", 
            id, title, 
            content.length() > 50 ? content.substring(0, 50) + "..." : content,
            content.trim().split("\\s+").length,
            isOverride, isOverridden);
    }
} 