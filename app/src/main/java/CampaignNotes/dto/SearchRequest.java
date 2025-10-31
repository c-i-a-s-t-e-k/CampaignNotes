package CampaignNotes.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for semantic search.
 */
public class SearchRequest {
    
    @NotBlank(message = "Search query is required")
    private String query;
    
    @Min(value = 1, message = "Limit must be at least 1")
    private int limit = 5; // Default limit
    
    public SearchRequest() {}
    
    public SearchRequest(String query, int limit) {
        this.query = query;
        this.limit = limit;
    }
    
    public String getQuery() {
        return query;
    }
    
    public void setQuery(String query) {
        this.query = query;
    }
    
    public int getLimit() {
        return limit;
    }
    
    public void setLimit(int limit) {
        this.limit = limit;
    }
}

