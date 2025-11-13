package model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import CampaignNotes.dto.deduplication.MergeProposal;

/**
 * Result of the complete artifact processing workflow.
 * Contains extracted artifacts, relationships, and metadata about the processing.
 */
public class ArtifactProcessingResult implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private List<Artifact> artifacts;
    private List<Relationship> relationships;
    private boolean successful;
    private String errorMessage;
    private int totalTokensUsed;
    private long totalDurationMs;
    private String noteId;
    private String campaignUuid;
    private DeduplicationResult deduplicationResult;
    private List<MergeProposal> mergeProposals;
    
    /**
     * Default constructor
     */
    public ArtifactProcessingResult() {
        this.artifacts = new ArrayList<>();
        this.relationships = new ArrayList<>();
        this.successful = false;
        this.totalTokensUsed = 0;
        this.totalDurationMs = 0;
        this.mergeProposals = new ArrayList<>();
    }
    
    /**
     * Constructor with note and campaign info
     */
    public ArtifactProcessingResult(String noteId, String campaignUuid) {
        this();
        this.noteId = noteId;
        this.campaignUuid = campaignUuid;
    }
    
    /**
     * Constructor for successful result
     */
    public ArtifactProcessingResult(List<Artifact> artifacts, List<Relationship> relationships, 
                                  int totalTokensUsed, long totalDurationMs, String noteId, String campaignUuid) {
        this(noteId, campaignUuid);
        this.artifacts = artifacts != null ? new ArrayList<>(artifacts) : new ArrayList<>();
        this.relationships = relationships != null ? new ArrayList<>(relationships) : new ArrayList<>();
        this.totalTokensUsed = totalTokensUsed;
        this.totalDurationMs = totalDurationMs;
        this.successful = true;
    }
    
    /**
     * Constructor for error result
     */
    public ArtifactProcessingResult(String errorMessage, String noteId, String campaignUuid) {
        this(noteId, campaignUuid);
        this.errorMessage = errorMessage;
        this.successful = false;
    }
    
    // Getters and setters
    public List<Artifact> getArtifacts() {
        return new ArrayList<>(artifacts);
    }
    
    public void setArtifacts(List<Artifact> artifacts) {
        this.artifacts = artifacts != null ? new ArrayList<>(artifacts) : new ArrayList<>();
    }
    
    public void addArtifact(Artifact artifact) {
        if (artifact != null) {
            this.artifacts.add(artifact);
        }
    }
    
    public List<Relationship> getRelationships() {
        return new ArrayList<>(relationships);
    }
    
    public void setRelationships(List<Relationship> relationships) {
        this.relationships = relationships != null ? new ArrayList<>(relationships) : new ArrayList<>();
    }
    
    public void addRelationship(Relationship relationship) {
        if (relationship != null) {
            this.relationships.add(relationship);
        }
    }
    
    public boolean isSuccessful() {
        return successful;
    }
    
    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.successful = false;
    }
    
    public int getTotalTokensUsed() {
        return totalTokensUsed;
    }
    
    public void setTotalTokensUsed(int totalTokensUsed) {
        this.totalTokensUsed = totalTokensUsed;
    }
    
    public void addTokensUsed(int tokens) {
        this.totalTokensUsed += tokens;
    }
    
    public long getTotalDurationMs() {
        return totalDurationMs;
    }
    
    public void setTotalDurationMs(long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }
    
    public void addDuration(long durationMs) {
        this.totalDurationMs += durationMs;
    }
    
    public String getNoteId() {
        return noteId;
    }
    
    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }
    
    public String getCampaignUuid() {
        return campaignUuid;
    }
    
    public void setCampaignUuid(String campaignUuid) {
        this.campaignUuid = campaignUuid;
    }
    
    public DeduplicationResult getDeduplicationResult() {
        return deduplicationResult;
    }
    
    public void setDeduplicationResult(DeduplicationResult deduplicationResult) {
        this.deduplicationResult = deduplicationResult;
    }
    
    public List<MergeProposal> getMergeProposals() {
        return new ArrayList<>(mergeProposals);
    }
    
    public void setMergeProposals(List<MergeProposal> mergeProposals) {
        this.mergeProposals = mergeProposals != null ? new ArrayList<>(mergeProposals) : new ArrayList<>();
    }
    
    public void addMergeProposal(MergeProposal proposal) {
        if (proposal != null) {
            this.mergeProposals.add(proposal);
        }
    }
    
    /**
     * Gets total count of extracted artifacts and relationships
     * @return total count
     */
    public int getTotalItemsCount() {
        return artifacts.size() + relationships.size();
    }
    
    /**
     * Calculates estimated cost based on tokens used and cost per 1k tokens
     * @param costPer1kTokens cost per 1000 tokens
     * @return estimated cost
     */
    public double calculateEstimatedCost(double costPer1kTokens) {
        return (totalTokensUsed / 1000.0) * costPer1kTokens;
    }
    
    @Override
    public String toString() {
        if (successful) {
            return String.format("ArtifactProcessingResult[artifacts=%d, relationships=%d, tokens=%d, duration=%dms]", 
                    artifacts.size(), relationships.size(), totalTokensUsed, totalDurationMs);
        } else {
            return String.format("ArtifactProcessingResult[ERROR: %s]", errorMessage);
        }
    }
} 