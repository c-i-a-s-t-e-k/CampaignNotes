package CampaignNotes.dto;

import java.util.ArrayList;
import java.util.List;

import CampaignNotes.dto.deduplication.MergeProposal;
import model.DeduplicationResult;

/**
 * Response DTO after creating a new note.
 * Contains information about the created note and extracted artifacts.
 * Includes deduplication suggestions if applicable.
 */
public class NoteCreateResponse {
    private String noteId;
    private String campaignUuid;
    private String title;
    private boolean success;
    private String message;
    private int artifactCount;
    private int relationshipCount;
    private List<ArtifactSummary> artifacts;
    private DeduplicationResult deduplicationResult;
    private boolean requiresUserConfirmation;
    private List<MergeProposal> artifactMergeProposals;
    private int mergedArtifactCount;
    private int mergedRelationshipCount;
    
    public NoteCreateResponse() {
        this.artifacts = new ArrayList<>();
        this.artifactMergeProposals = new ArrayList<>();
        this.requiresUserConfirmation = false;
        this.mergedArtifactCount = 0;
        this.mergedRelationshipCount = 0;
    }
    
    public NoteCreateResponse(String noteId, String title, boolean success, String message) {
        this();
        this.noteId = noteId;
        this.title = title;
        this.success = success;
        this.message = message;
    }
    
    // Getters and setters
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
    
    public DeduplicationResult getDeduplicationResult() {
        return deduplicationResult;
    }
    
    public void setDeduplicationResult(DeduplicationResult deduplicationResult) {
        this.deduplicationResult = deduplicationResult;
    }
    
    public boolean isRequiresUserConfirmation() {
        return requiresUserConfirmation;
    }
    
    public void setRequiresUserConfirmation(boolean requiresUserConfirmation) {
        this.requiresUserConfirmation = requiresUserConfirmation;
    }
    
    public List<MergeProposal> getArtifactMergeProposals() {
        return artifactMergeProposals;
    }
    
    public void setArtifactMergeProposals(List<MergeProposal> artifactMergeProposals) {
        this.artifactMergeProposals = artifactMergeProposals;
    }
    
    public void addMergeProposal(MergeProposal proposal) {
        if (proposal != null) {
            this.artifactMergeProposals.add(proposal);
        }
    }
    
    public int getMergedArtifactCount() {
        return mergedArtifactCount;
    }
    
    public void setMergedArtifactCount(int mergedArtifactCount) {
        this.mergedArtifactCount = mergedArtifactCount;
    }
    
    public int getMergedRelationshipCount() {
        return mergedRelationshipCount;
    }
    
    public void setMergedRelationshipCount(int mergedRelationshipCount) {
        this.mergedRelationshipCount = mergedRelationshipCount;
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

