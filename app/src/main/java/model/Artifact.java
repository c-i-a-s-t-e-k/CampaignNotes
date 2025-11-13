package model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
    private List<String> noteIds;
    private String description;
    private String shortDescription;
    private LocalDateTime createdAt;
    
    /**
     * Default constructor
     */
    public Artifact() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.noteIds = new ArrayList<>();
    }
    
    /**
     * Constructor with essential fields
     */
    public Artifact(String name, String type, String campaignUuid, String noteId) {
        this();
        this.name = name;
        this.type = type;
        this.campaignUuid = campaignUuid;
        if (noteId != null && !noteId.trim().isEmpty()) {
            this.noteIds.add(noteId);
        }
    }
    
    /**
     * Constructor with all fields
     */
    public Artifact(String name, String type, String campaignUuid, String noteId, String description) {
        this(name, type, campaignUuid, noteId);
        this.description = description;
    }
    
    /**
     * Constructor with short description for embedding purposes
     */
    public Artifact(String name, String type, String campaignUuid, String noteId, String description, String shortDescription) {
        this(name, type, campaignUuid, noteId, description);
        this.shortDescription = shortDescription;
    }
    
    /**
     * Constructor with list of note IDs
     */
    public Artifact(String name, String type, String campaignUuid, List<String> noteIds, String description) {
        this();
        this.name = name;
        this.type = type;
        this.campaignUuid = campaignUuid;
        this.noteIds = noteIds != null ? new ArrayList<>(noteIds) : new ArrayList<>();
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
    
    public List<String> getNoteIds() {
        return noteIds != null ? new ArrayList<>(noteIds) : new ArrayList<>();
    }
    
    public void setNoteIds(List<String> noteIds) {
        this.noteIds = noteIds != null ? new ArrayList<>(noteIds) : new ArrayList<>();
    }
    
    /**
     * Adds a note ID to the list if it doesn't already exist.
     * @param noteId the note ID to add
     */
    public void addNoteId(String noteId) {
        if (noteId != null && !noteId.trim().isEmpty()) {
            if (this.noteIds == null) {
                this.noteIds = new ArrayList<>();
            }
            if (!this.noteIds.contains(noteId)) {
                this.noteIds.add(noteId);
            }
        }
    }
    
    /**
     * Removes a note ID from the list.
     * @param noteId the note ID to remove
     */
    public void removeNoteId(String noteId) {
        if (this.noteIds != null && noteId != null) {
            this.noteIds.remove(noteId);
        }
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getShortDescription() {
        return shortDescription;
    }
    
    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    /**
     * Validates the artifact according to business rules.
     * Note: shortDescription is optional for validation purposes.
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return name != null && !name.trim().isEmpty() &&
               type != null && !type.trim().isEmpty() &&
               campaignUuid != null && !campaignUuid.trim().isEmpty() &&
               noteIds != null && !noteIds.isEmpty();
    }
    
    @Override
    public String toString() {
        return String.format("Artifact[id=%s, name='%s', type='%s', campaign=%s, notes=%s]", 
                id, name, type, campaignUuid, noteIds);
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