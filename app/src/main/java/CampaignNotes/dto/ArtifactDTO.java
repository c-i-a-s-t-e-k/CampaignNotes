package CampaignNotes.dto;

/**
 * Data Transfer Object for Artifact information.
 */
public class ArtifactDTO {
    private String id;
    private String name;
    private String type;
    private String description;
    
    public ArtifactDTO() {
    }
    
    public ArtifactDTO(String id, String name, String type, String description) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.description = description;
    }
    
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
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
}
