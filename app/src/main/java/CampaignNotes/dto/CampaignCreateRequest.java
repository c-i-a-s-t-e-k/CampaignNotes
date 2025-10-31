package CampaignNotes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new campaign.
 */
public class CampaignCreateRequest {
    
    @NotBlank(message = "Campaign name is required")
    @Size(min = 1, max = 200, message = "Campaign name must be between 1 and 200 characters")
    private String name;
    
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;
    
    public CampaignCreateRequest() {}
    
    public CampaignCreateRequest(String name, String description) {
        this.name = name;
        this.description = description;
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
}

