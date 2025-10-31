package CampaignNotes.dto;

/**
 * DTO representing a single search result.
 */
public class SearchResultDTO {
    private String noteId;
    private String title;
    private String contentPreview;
    private double score;
    
    public SearchResultDTO() {}
    
    public SearchResultDTO(String noteId, String title, String contentPreview, double score) {
        this.noteId = noteId;
        this.title = title;
        this.contentPreview = contentPreview;
        this.score = score;
    }
    
    public String getNoteId() {
        return noteId;
    }
    
    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getContentPreview() {
        return contentPreview;
    }
    
    public void setContentPreview(String contentPreview) {
        this.contentPreview = contentPreview;
    }
    
    public double getScore() {
        return score;
    }
    
    public void setScore(double score) {
        this.score = score;
    }
}

