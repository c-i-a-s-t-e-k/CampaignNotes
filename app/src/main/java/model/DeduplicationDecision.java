package model;

/**
 * Represents a decision made by the LLM in Phase 2 (LLM reasoning) of the deduplication process.
 * Contains the verdict on whether two artifacts/relationships should be merged and confidence level.
 */
public class DeduplicationDecision {
    private boolean isSame;
    private int confidence;  // 0-100
    private String reasoning;
    private String candidateId;
    private String candidateName;
    
    /**
     * Default constructor
     */
    public DeduplicationDecision() {
    }
    
    /**
     * Constructor with all fields
     */
    public DeduplicationDecision(boolean isSame, int confidence, String reasoning, 
                               String candidateId, String candidateName) {
        this.isSame = isSame;
        this.confidence = Math.max(0, Math.min(100, confidence));  // Clamp to 0-100
        this.reasoning = reasoning;
        this.candidateId = candidateId;
        this.candidateName = candidateName;
    }
    
    // Getters and setters
    public boolean isSame() {
        return isSame;
    }
    
    public void setSame(boolean same) {
        isSame = same;
    }
    
    public int getConfidence() {
        return confidence;
    }
    
    public void setConfidence(int confidence) {
        this.confidence = Math.max(0, Math.min(100, confidence));
    }
    
    public String getReasoning() {
        return reasoning;
    }
    
    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }
    
    public String getCandidateId() {
        return candidateId;
    }
    
    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }
    
    public String getCandidateName() {
        return candidateName;
    }
    
    public void setCandidateName(String candidateName) {
        this.candidateName = candidateName;
    }
    
    /**
     * Checks if this decision should trigger an automatic merge (high confidence).
     * Requires both isSame=true and confidence at threshold or higher.
     */
    public boolean shouldAutoMerge(int confidenceThreshold) {
        return isSame && confidence >= confidenceThreshold;
    }
    
    /**
     * Checks if this decision requires user confirmation (low confidence).
     * Would require manual approval before merging.
     */
    public boolean requiresUserConfirmation(int confidenceThreshold) {
        return isSame && confidence < confidenceThreshold;
    }
    
    @Override
    public String toString() {
        return String.format("DeduplicationDecision[isSame=%s, confidence=%d, candidate=%s]",
                isSame, confidence, candidateName);
    }
}

