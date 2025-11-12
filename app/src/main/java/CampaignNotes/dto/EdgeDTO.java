package CampaignNotes.dto;

import java.util.ArrayList;
import java.util.List;

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
    private List<String> noteIds;
    
    public EdgeDTO() {
        this.noteIds = new ArrayList<>();
    }
    
    public EdgeDTO(String id, String source, String target, String label, String description, String reasoning) {
        this.id = id;
        this.source = source;
        this.target = target;
        this.label = label;
        this.description = description;
        this.reasoning = reasoning;
        this.noteIds = new ArrayList<>();
    }
    
    public EdgeDTO(String id, String source, String target, String label, String description, String reasoning, List<String> noteIds) {
        this.id = id;
        this.source = source;
        this.target = target;
        this.label = label;
        this.description = description;
        this.reasoning = reasoning;
        this.noteIds = noteIds != null ? new ArrayList<>(noteIds) : new ArrayList<>();
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
    
    public List<String> getNoteIds() {
        return noteIds != null ? noteIds : new ArrayList<>();
    }
    
    public void setNoteIds(List<String> noteIds) {
        this.noteIds = noteIds != null ? new ArrayList<>(noteIds) : new ArrayList<>();
    }
}

