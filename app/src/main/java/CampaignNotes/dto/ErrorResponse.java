package CampaignNotes.dto;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Standard error response DTO for API errors.
 */
public class ErrorResponse {
    private String message;
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private Map<String, String> details;
    
    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
        this.details = new HashMap<>();
    }
    
    public ErrorResponse(String message, int status, String error) {
        this();
        this.message = message;
        this.status = status;
        this.error = error;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public int getStatus() {
        return status;
    }
    
    public void setStatus(int status) {
        this.status = status;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    public Map<String, String> getDetails() {
        return details;
    }
    
    public void setDetails(Map<String, String> details) {
        this.details = details;
    }
    
    public void addDetail(String key, String value) {
        this.details.put(key, value);
    }
}

