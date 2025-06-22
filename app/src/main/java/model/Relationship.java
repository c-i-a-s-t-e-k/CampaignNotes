package model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Model representing a relationship between two artifacts in a campaign.
 * Relationships describe how artifacts are connected or interact with each other.
 */
public class Relationship implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String sourceArtifactName;
    private String targetArtifactName;
    private String label;
    private String description;
    private String reasoning;
    private String noteId;
    private String campaignUuid;
    private LocalDateTime createdAt;
    
    /**
     * Default constructor
     */
    public Relationship() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
    }
    
    /**
     * Constructor with essential fields
     */
    public Relationship(String sourceArtifactName, String targetArtifactName, String label, String noteId, String campaignUuid) {
        this();
        this.sourceArtifactName = sourceArtifactName;
        this.targetArtifactName = targetArtifactName;
        this.label = label;
        this.noteId = noteId;
        this.campaignUuid = campaignUuid;
    }
    
    /**
     * Constructor with all fields
     */
    public Relationship(String sourceArtifactName, String targetArtifactName, String label, 
                       String description, String reasoning, String noteId, String campaignUuid) {
        this(sourceArtifactName, targetArtifactName, label, noteId, campaignUuid);
        this.description = description;
        this.reasoning = reasoning;
    }
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getSourceArtifactName() {
        return sourceArtifactName;
    }
    
    public void setSourceArtifactName(String sourceArtifactName) {
        this.sourceArtifactName = sourceArtifactName;
    }
    
    public String getTargetArtifactName() {
        return targetArtifactName;
    }
    
    public void setTargetArtifactName(String targetArtifactName) {
        this.targetArtifactName = targetArtifactName;
    }
    
    public String getLabel() {
        return label;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getReasoning() {
        return reasoning;
    }
    
    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }
    
    public String getNoteId() {
        return noteId;
    }
    
    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }
    
    public String getCampaignUuid() {
        return campaignUuid;
    }
    
    public void setCampaignUuid(String campaignUuid) {
        this.campaignUuid = campaignUuid;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    /**
     * Validates the relationship according to business rules.
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return sourceArtifactName != null && !sourceArtifactName.trim().isEmpty() &&
               targetArtifactName != null && !targetArtifactName.trim().isEmpty() &&
               label != null && !label.trim().isEmpty() &&
               noteId != null && !noteId.trim().isEmpty() &&
               campaignUuid != null && !campaignUuid.trim().isEmpty() &&
               !sourceArtifactName.equals(targetArtifactName); // Prevent self-relationships
    }
    
    @Override
    public String toString() {
        return String.format("Relationship[%s --%s--> %s] (from note: %s)", 
                sourceArtifactName, label, targetArtifactName, noteId);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Relationship that = (Relationship) obj;
        return id != null ? id.equals(that.id) : that.id == null;
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
} 