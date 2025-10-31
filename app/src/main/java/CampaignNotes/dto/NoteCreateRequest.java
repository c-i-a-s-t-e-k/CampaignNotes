package CampaignNotes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new note.
 */
public class NoteCreateRequest {
    
    @NotBlank(message = "Note title is required")
    @Size(min = 1, max = 500, message = "Title must be between 1 and 500 characters")
    private String title;
    
    @NotBlank(message = "Note content is required")
    private String content;
    
    public NoteCreateRequest() {}
    
    public NoteCreateRequest(String title, String content) {
        this.title = title;
        this.content = content;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    /**
     * Validates that content doesn't exceed 500 words.
     */
    public boolean isValid() {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        String[] words = content.trim().split("\\s+");
        return words.length <= 500;
    }
    
    public String getValidationError() {
        if (content == null || content.trim().isEmpty()) {
            return "Content cannot be empty";
        }
        String[] words = content.trim().split("\\s+");
        if (words.length > 500) {
            return "Content exceeds 500 words limit (current: " + words.length + " words)";
        }
        return null;
    }
}

