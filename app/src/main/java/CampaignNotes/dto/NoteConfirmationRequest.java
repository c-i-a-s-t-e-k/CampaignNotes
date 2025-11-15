package CampaignNotes.dto;

import java.util.ArrayList;
import java.util.List;
import CampaignNotes.dto.deduplication.MergeProposal;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for confirming note deduplication.
 * Contains user approval decisions for merge proposals.
 */
public class NoteConfirmationRequest {
    
    @NotBlank(message = "Campaign UUID is required")
    private String campaignUuid;
    
    @NotBlank(message = "Note ID is required")
    private String noteId;
    
    private List<MergeProposal> approvedMergeProposals;
    
    /**
     * Default constructor
     */
    public NoteConfirmationRequest() {
        this.approvedMergeProposals = new ArrayList<>();
    }
    
    /**
     * Constructor with campaign and note info
     */
    public NoteConfirmationRequest(String campaignUuid, String noteId) {
        this();
        this.campaignUuid = campaignUuid;
        this.noteId = noteId;
    }
    
    /**
     * Constructor with all fields
     */
    public NoteConfirmationRequest(String campaignUuid, String noteId, List<MergeProposal> approvedMergeProposals) {
        this(campaignUuid, noteId);
        this.approvedMergeProposals = approvedMergeProposals != null ? new ArrayList<>(approvedMergeProposals) : new ArrayList<>();
    }
    
    // Getters and setters
    public String getCampaignUuid() {
        return campaignUuid;
    }
    
    public void setCampaignUuid(String campaignUuid) {
        this.campaignUuid = campaignUuid;
    }
    
    public String getNoteId() {
        return noteId;
    }
    
    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }
    
    public List<MergeProposal> getApprovedMergeProposals() {
        return new ArrayList<>(approvedMergeProposals);
    }
    
    public void setApprovedMergeProposals(List<MergeProposal> approvedMergeProposals) {
        this.approvedMergeProposals = approvedMergeProposals != null ? new ArrayList<>(approvedMergeProposals) : new ArrayList<>();
    }
    
    public void addApprovedProposal(MergeProposal proposal) {
        if (proposal != null && !approvedMergeProposals.contains(proposal)) {
            approvedMergeProposals.add(proposal);
        }
    }
    
    /**
     * Counts approved proposals of a specific type
     */
    public int countApprovedByType(String itemType) {
        return (int) approvedMergeProposals.stream()
            .filter(p -> itemType.equals(p.getItemType()))
            .count();
    }
}

