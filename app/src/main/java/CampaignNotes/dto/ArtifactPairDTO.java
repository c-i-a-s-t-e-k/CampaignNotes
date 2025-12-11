package CampaignNotes.dto;

/**
 * Data Transfer Object for Artifact Pair (relationship connection).
 */
public class ArtifactPairDTO {
    private ArtifactDTO source;
    private ArtifactDTO target;
    private String relationshipId;
    private String label;
    private String description;
    
    public ArtifactPairDTO() {
    }
    
    public ArtifactPairDTO(ArtifactDTO source, ArtifactDTO target, String relationshipId, 
                          String label, String description) {
        this.source = source;
        this.target = target;
        this.relationshipId = relationshipId;
        this.label = label;
        this.description = description;
    }
    
    public ArtifactDTO getSource() {
        return source;
    }
    
    public void setSource(ArtifactDTO source) {
        this.source = source;
    }
    
    public ArtifactDTO getTarget() {
        return target;
    }
    
    public void setTarget(ArtifactDTO target) {
        this.target = target;
    }
    
    public String getRelationshipId() {
        return relationshipId;
    }
    
    public void setRelationshipId(String relationshipId) {
        this.relationshipId = relationshipId;
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
}
