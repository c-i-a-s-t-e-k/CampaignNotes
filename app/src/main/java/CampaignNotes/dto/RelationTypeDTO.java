package CampaignNotes.dto;

/**
 * Data Transfer Object for Relation Type information.
 */
public class RelationTypeDTO {
    private String label;
    private int count;
    
    public RelationTypeDTO() {
    }
    
    public RelationTypeDTO(String label, int count) {
        this.label = label;
        this.count = count;
    }
    
    public String getLabel() {
        return label;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }
    
    public int getCount() {
        return count;
    }
    
    public void setCount(int count) {
        this.count = count;
    }
}
