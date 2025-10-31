package CampaignNotes.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO after creating a new note.
 * Contains information about the created note and extracted artifacts.
 */
public class NoteCreateResponse {
    private String noteId;
    private String title;
    private boolean success;
    private String message;
    private int artifactCount;
    private int relationshipCount;
    private List<ArtifactSummary> artifacts;
    
    public NoteCreateResponse() {
        this.artifacts = new ArrayList<>();
    }
    
    public NoteCreateResponse(String noteId, String title, boolean success, String message) {
        this.noteId = noteId;
        this.title = title;
        this.success = success;
        this.message = message;
        this.artifacts = new ArrayList<>();
    }
    
    // Getters and setters
    public String getNoteId() {
        return noteId;
    }
    
    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public int getArtifactCount() {
        return artifactCount;
    }
    
    public void setArtifactCount(int artifactCount) {
        this.artifactCount = artifactCount;
    }
    
    public int getRelationshipCount() {
        return relationshipCount;
    }
    
    public void setRelationshipCount(int relationshipCount) {
        this.relationshipCount = relationshipCount;
    }
    
    public List<ArtifactSummary> getArtifacts() {
        return artifacts;
    }
    
    public void setArtifacts(List<ArtifactSummary> artifacts) {
        this.artifacts = artifacts;
    }
    
    /**
     * Simple summary of an artifact for the response.
     */
    public static class ArtifactSummary {
        private String name;
        private String type;
        private String description;
        
        public ArtifactSummary() {}
        
        public ArtifactSummary(String name, String type, String description) {
            this.name = name;
            this.type = type;
            this.description = description;
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
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
    }
}

