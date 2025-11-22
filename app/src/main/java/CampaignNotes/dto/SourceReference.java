package CampaignNotes.dto;

public class SourceReference {
    private String noteId;
    private String noteTitle;

    public SourceReference(String noteId, String noteTitle) {
        this.noteId = noteId;
        this.noteTitle = noteTitle;
    }

    // Getters and setters
    public String getNoteId() {
        return noteId;
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    public String getNoteTitle() {
        return noteTitle;
    }

    public void setNoteTitle(String noteTitle) {
        this.noteTitle = noteTitle;
    }
}
