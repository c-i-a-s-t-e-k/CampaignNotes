package CampaignNotes.dto;

/**
 * DTO representing a node in the campaign graph.
 */
public class NodeDTO {
    private String id;
    private String name;
    private String type;
    private String description;
    private String campaignUuid;
    private String noteId;
    
    public NodeDTO() {}
    
    public NodeDTO(String id, String name, String type, String description, String campaignUuid, String noteId) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.description = description;
        this.campaignUuid = campaignUuid;
        this.noteId = noteId;
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
}

