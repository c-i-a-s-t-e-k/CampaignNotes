package CampaignNotes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AssistantQueryRequest {
    @NotBlank(message = "Query cannot be empty")
    @Size(max = 500, message = "Query too long")
    private String query;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}
