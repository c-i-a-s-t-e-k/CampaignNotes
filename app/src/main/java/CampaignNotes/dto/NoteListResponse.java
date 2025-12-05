package CampaignNotes.dto;

import java.util.List;

/**
 * Data Transfer Object for paginated note list response.
 * Contains a list of notes along with pagination metadata.
 */
public class NoteListResponse {
    private List<NoteDTO> notes;
    private int totalCount;
    private boolean hasMore;
    private int offset;
    private int limit;
    
    public NoteListResponse() {}
    
    public NoteListResponse(List<NoteDTO> notes, int totalCount, int offset, int limit) {
        this.notes = notes;
        this.totalCount = totalCount;
        this.offset = offset;
        this.limit = limit;
        this.hasMore = (offset + notes.size()) < totalCount;
    }
    
    // Getters and setters
    public List<NoteDTO> getNotes() {
        return notes;
    }
    
    public void setNotes(List<NoteDTO> notes) {
        this.notes = notes;
    }
    
    public int getTotalCount() {
        return totalCount;
    }
    
    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
    
    public boolean isHasMore() {
        return hasMore;
    }
    
    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
    
    public int getOffset() {
        return offset;
    }
    
    public void setOffset(int offset) {
        this.offset = offset;
    }
    
    public int getLimit() {
        return limit;
    }
    
    public void setLimit(int limit) {
        this.limit = limit;
    }
}

