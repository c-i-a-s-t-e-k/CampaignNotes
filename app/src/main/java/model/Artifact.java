package model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Model representing a narrative artifact extracted from a campaign note.
 * Artifacts can be characters, locations, items, or events mentioned in the campaign content.
 */
public class Artifact implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String name;
    private String type;
    private String campaignUuid;
    private String noteId;
    private String description;
    private LocalDateTime createdAt;
    
    /**
     * Default constructor
     */
    public Artifact() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
    }
    
    /**
     * Constructor with essential fields
     */
    public Artifact(String name, String type, String campaignUuid, String noteId) {
        this();
        this.name = name;
        this.type = type;
        this.campaignUuid = campaignUuid;
        this.noteId = noteId;
    }
    
    /**
     * Constructor with all fields
     */
    public Artifact(String name, String type, String campaignUuid, String noteId, String description) {
        this(name, type, campaignUuid, noteId);
        this.description = description;
    }
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getCampaignUuid() {
        return campaignUuid;
    }
    
    public void setCampaignUuid(String campaignUuid) {
        this.campaignUuid = campaignUuid;
    }
    
    public String getNoteId() {
        return noteId;
    }
    
    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    /**
     * Validates the artifact according to business rules.
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return name != null && !name.trim().isEmpty() &&
               type != null && !type.trim().isEmpty() &&
               campaignUuid != null && !campaignUuid.trim().isEmpty() &&
               noteId != null && !noteId.trim().isEmpty();
    }
    
    @Override
    public String toString() {
        return String.format("Artifact[id=%s, name='%s', type='%s', campaign=%s, note=%s]", 
                id, name, type, campaignUuid, noteId);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Artifact artifact = (Artifact) obj;
        return id != null ? id.equals(artifact.id) : artifact.id == null;
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
} 