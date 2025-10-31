package CampaignNotes.dto;

/**
 * DTO representing an edge (relationship) in the campaign graph.
 */
public class EdgeDTO {
    private String id;
    private String source;
    private String target;
    private String label;
    private String description;
    private String reasoning;
    
    public EdgeDTO() {}
    
    public EdgeDTO(String id, String source, String target, String label, String description, String reasoning) {
        this.id = id;
        this.source = source;
        this.target = target;
        this.label = label;
        this.description = description;
        this.reasoning = reasoning;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
    public String getTarget() {
        return target;
    }
    
    public void setTarget(String target) {
        this.target = target;
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
}

