package CampaignNotes.dto.deduplication;

/**
 * Represents a proposal to merge two artifacts or relationships.
 * Contains information needed for user confirmation or automatic merging.
 */
public class MergeProposal {
    private String newItemId;
    private String newItemName;
    private String existingItemId;
    private String existingItemName;
    private String itemType;  // "artifact" or "relationship"
    private int confidence;
    private String reasoning;
    private boolean autoMerge;
    private boolean approved;  // User confirmation
    
    /**
     * Default constructor
     */
    public MergeProposal() {
        this.approved = false;
    }
    
    /**
     * Constructor with essential fields
     */
    public MergeProposal(String newItemId, String newItemName, String existingItemId, 
                        String existingItemName, String itemType, int confidence, 
                        String reasoning, boolean autoMerge) {
        this();
        this.newItemId = newItemId;
        this.newItemName = newItemName;
        this.existingItemId = existingItemId;
        this.existingItemName = existingItemName;
        this.itemType = itemType;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.autoMerge = autoMerge;
    }
    
    // Getters and setters
    public String getNewItemId() {
        return newItemId;
    }
    
    public void setNewItemId(String newItemId) {
        this.newItemId = newItemId;
    }
    
    public String getNewItemName() {
        return newItemName;
    }
    
    public void setNewItemName(String newItemName) {
        this.newItemName = newItemName;
    }
    
    public String getExistingItemId() {
        return existingItemId;
    }
    
    public void setExistingItemId(String existingItemId) {
        this.existingItemId = existingItemId;
    }
    
    public String getExistingItemName() {
        return existingItemName;
    }
    
    public void setExistingItemName(String existingItemName) {
        this.existingItemName = existingItemName;
    }
    
    public String getItemType() {
        return itemType;
    }
    
    public void setItemType(String itemType) {
        this.itemType = itemType;
    }
    
    public int getConfidence() {
        return confidence;
    }
    
    public void setConfidence(int confidence) {
        this.confidence = Math.max(0, Math.min(100, confidence));
    }
    
    public String getReasoning() {
        return reasoning;
    }
    
    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }
    
    public boolean isAutoMerge() {
        return autoMerge;
    }
    
    public void setAutoMerge(boolean autoMerge) {
        this.autoMerge = autoMerge;
    }
    
    public boolean isApproved() {
        return approved;
    }
    
    public void setApproved(boolean approved) {
        this.approved = approved;
    }
    
    @Override
    public String toString() {
        return String.format("MergeProposal[%s: '%s' -> '%s', confidence=%d, autoMerge=%s]",
                itemType, newItemName, existingItemName, confidence, autoMerge);
    }
}

