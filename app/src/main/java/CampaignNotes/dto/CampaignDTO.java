package CampaignNotes.dto;

/**
 * Data Transfer Object for Campaign information.
 * Contains basic campaign details for API responses.
 */
public class CampaignDTO {
    private String uuid;
    private String name;
    private String description;
    private long createdAt;
    private long updatedAt;
    private boolean isActive;
    
    public CampaignDTO() {}
    
    public CampaignDTO(String uuid, String name, String description, long createdAt, long updatedAt, boolean isActive) {
        this.uuid = uuid;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isActive = isActive;
    }
    
    // Getters and setters
    public String getUuid() {
        return uuid;
    }
    
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
    
    public long getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
}

