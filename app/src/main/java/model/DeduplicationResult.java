package model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated result of the complete hybrid deduplication process (Phase 1 + Phase 2).
 * Contains all decisions, durations, and token usage information.
 */
public class DeduplicationResult implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Map<String, List<DeduplicationDecision>> artifactDecisions;
    private Map<String, List<DeduplicationDecision>> relationshipDecisions;
    private List<Artifact> newArtifacts;
    private List<Relationship> newRelationships;
    private int totalTokensUsed;
    private long phase1DurationMs;
    private long phase2DurationMs;
    private long totalDurationMs;
    private String noteId;
    private String campaignUuid;
    
    /**
     * Default constructor
     */
    public DeduplicationResult() {
        this.artifactDecisions = new HashMap<>();
        this.relationshipDecisions = new HashMap<>();
        this.newArtifacts = new ArrayList<>();
        this.newRelationships = new ArrayList<>();
        this.totalTokensUsed = 0;
        this.phase1DurationMs = 0;
        this.phase2DurationMs = 0;
        this.totalDurationMs = 0;
    }
    
    /**
     * Constructor with campaign and note info
     */
    public DeduplicationResult(String noteId, String campaignUuid) {
        this();
        this.noteId = noteId;
        this.campaignUuid = campaignUuid;
    }
    
    // Getters and setters
    public Map<String, List<DeduplicationDecision>> getArtifactDecisions() {
        return artifactDecisions;
    }
    
    public void setArtifactDecisions(Map<String, List<DeduplicationDecision>> artifactDecisions) {
        this.artifactDecisions = artifactDecisions != null ? artifactDecisions : new HashMap<>();
    }
    
    public void addArtifactDecision(String artifactId, DeduplicationDecision decision) {
        if (decision != null && artifactId != null) {
            artifactDecisions.computeIfAbsent(artifactId, k -> new ArrayList<>()).add(decision);
        }
    }
    
    public Map<String, List<DeduplicationDecision>> getRelationshipDecisions() {
        return relationshipDecisions;
    }
    
    public void setRelationshipDecisions(Map<String, List<DeduplicationDecision>> relationshipDecisions) {
        this.relationshipDecisions = relationshipDecisions != null ? relationshipDecisions : new HashMap<>();
    }
    
    public void addRelationshipDecision(String relationshipId, DeduplicationDecision decision) {
        if (decision != null && relationshipId != null) {
            relationshipDecisions.computeIfAbsent(relationshipId, k -> new ArrayList<>()).add(decision);
        }
    }
    
    public List<Artifact> getNewArtifacts() {
        return new ArrayList<>(newArtifacts);
    }
    
    public void setNewArtifacts(List<Artifact> newArtifacts) {
        this.newArtifacts = newArtifacts != null ? new ArrayList<>(newArtifacts) : new ArrayList<>();
    }
    
    public void addNewArtifact(Artifact artifact) {
        if (artifact != null) {
            this.newArtifacts.add(artifact);
        }
    }
    
    public List<Relationship> getNewRelationships() {
        return new ArrayList<>(newRelationships);
    }
    
    public void setNewRelationships(List<Relationship> newRelationships) {
        this.newRelationships = newRelationships != null ? new ArrayList<>(newRelationships) : new ArrayList<>();
    }
    
    public void addNewRelationship(Relationship relationship) {
        if (relationship != null) {
            this.newRelationships.add(relationship);
        }
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
    
    public long getPhase1DurationMs() {
        return phase1DurationMs;
    }
    
    public void setPhase1DurationMs(long phase1DurationMs) {
        this.phase1DurationMs = phase1DurationMs;
    }
    
    public long getPhase2DurationMs() {
        return phase2DurationMs;
    }
    
    public void setPhase2DurationMs(long phase2DurationMs) {
        this.phase2DurationMs = phase2DurationMs;
    }
    
    public long getTotalDurationMs() {
        return totalDurationMs;
    }
    
    public void setTotalDurationMs(long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
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
    
    /**
     * Gets total number of merge decisions made
     */
    public int getTotalMergeDecisions() {
        int count = 0;
        for (List<DeduplicationDecision> decisions : artifactDecisions.values()) {
            count += decisions.size();
        }
        for (List<DeduplicationDecision> decisions : relationshipDecisions.values()) {
            count += decisions.size();
        }
        return count;
    }
    
    /**
     * Gets number of positive merge decisions (isSame=true)
     */
    public int getPositiveMergeDecisions() {
        int count = 0;
        for (List<DeduplicationDecision> decisions : artifactDecisions.values()) {
            for (DeduplicationDecision decision : decisions) {
                if (decision.isSame()) count++;
            }
        }
        for (List<DeduplicationDecision> decisions : relationshipDecisions.values()) {
            for (DeduplicationDecision decision : decisions) {
                if (decision.isSame()) count++;
            }
        }
        return count;
    }
    
    /**
     * Gets number of auto-merge eligible decisions
     */
    public int getAutoMergeEligibleDecisions(int confidenceThreshold) {
        int count = 0;
        for (List<DeduplicationDecision> decisions : artifactDecisions.values()) {
            for (DeduplicationDecision decision : decisions) {
                if (decision.shouldAutoMerge(confidenceThreshold)) count++;
            }
        }
        for (List<DeduplicationDecision> decisions : relationshipDecisions.values()) {
            for (DeduplicationDecision decision : decisions) {
                if (decision.shouldAutoMerge(confidenceThreshold)) count++;
            }
        }
        return count;
    }
    
    /**
     * Gets number of decisions requiring user confirmation
     */
    public int getRequiresConfirmationCount(int confidenceThreshold) {
        int count = 0;
        for (List<DeduplicationDecision> decisions : artifactDecisions.values()) {
            for (DeduplicationDecision decision : decisions) {
                if (decision.requiresUserConfirmation(confidenceThreshold)) count++;
            }
        }
        for (List<DeduplicationDecision> decisions : relationshipDecisions.values()) {
            for (DeduplicationDecision decision : decisions) {
                if (decision.requiresUserConfirmation(confidenceThreshold)) count++;
            }
        }
        return count;
    }
    
    @Override
    public String toString() {
        return String.format("DeduplicationResult[artifacts=%d, relationships=%d, " +
                           "artifacts_decisions=%d, relationships_decisions=%d, tokens=%d, " +
                           "duration=%dms (Phase1: %dms, Phase2: %dms)]",
                           newArtifacts.size(), newRelationships.size(),
                           artifactDecisions.size(), relationshipDecisions.size(),
                           totalTokensUsed, totalDurationMs, phase1DurationMs, phase2DurationMs);
    }
}

