package CampaignNotes.dto.deduplication;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a candidate relationship found in Phase 1 (ANN search) of the deduplication process.
 * Contains similarity score and context information for Phase 2 (LLM reasoning).
 */
public class RelationshipCandidate {
    private String relationshipId;
    private String sourceArtifactName;
    private String targetArtifactName;
    private String label;
    private String description;
    private double similarityScore;
    private List<String> sourceNoteIds;
    
    /**
     * Default constructor
     */
    public RelationshipCandidate() {
        this.sourceNoteIds = new ArrayList<>();
    }
    
    /**
     * Constructor with essential fields
     */
    public RelationshipCandidate(String relationshipId, String sourceArtifactName, String targetArtifactName,
                                String label, String description, double similarityScore) {
        this();
        this.relationshipId = relationshipId;
        this.sourceArtifactName = sourceArtifactName;
        this.targetArtifactName = targetArtifactName;
        this.label = label;
        this.description = description;
        this.similarityScore = similarityScore;
    }
    
    /**
     * Constructor with all fields
     */
    public RelationshipCandidate(String relationshipId, String sourceArtifactName, String targetArtifactName,
                                String label, String description, double similarityScore, List<String> sourceNoteIds) {
        this();
        this.relationshipId = relationshipId;
        this.sourceArtifactName = sourceArtifactName;
        this.targetArtifactName = targetArtifactName;
        this.label = label;
        this.description = description;
        this.similarityScore = similarityScore;
        this.sourceNoteIds = sourceNoteIds != null ? new ArrayList<>(sourceNoteIds) : new ArrayList<>();
    }
    
    // Getters and setters
    public String getRelationshipId() {
        return relationshipId;
    }
    
    public void setRelationshipId(String relationshipId) {
        this.relationshipId = relationshipId;
    }
    
    public String getSourceArtifactName() {
        return sourceArtifactName;
    }
    
    public void setSourceArtifactName(String sourceArtifactName) {
        this.sourceArtifactName = sourceArtifactName;
    }
    
    public String getTargetArtifactName() {
        return targetArtifactName;
    }
    
    public void setTargetArtifactName(String targetArtifactName) {
        this.targetArtifactName = targetArtifactName;
    }
    
    public String getLabel() {
        return label;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public double getSimilarityScore() {
        return similarityScore;
    }
    
    public void setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
    }
    
    public List<String> getSourceNoteIds() {
        return new ArrayList<>(sourceNoteIds);
    }
    
    public void setSourceNoteIds(List<String> sourceNoteIds) {
        this.sourceNoteIds = sourceNoteIds != null ? new ArrayList<>(sourceNoteIds) : new ArrayList<>();
    }
    
    public void addSourceNoteId(String noteId) {
        if (noteId != null && !sourceNoteIds.contains(noteId)) {
            sourceNoteIds.add(noteId);
        }
    }
    
    @Override
    public String toString() {
        return String.format("RelationshipCandidate[id=%s, %s -[%s]-> %s, similarity=%.3f]",
                relationshipId, sourceArtifactName, label, targetArtifactName, similarityScore);
    }
}

