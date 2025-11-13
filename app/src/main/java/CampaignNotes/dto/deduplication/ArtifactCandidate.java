package CampaignNotes.dto.deduplication;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a candidate artifact found in Phase 1 (ANN search) of the deduplication process.
 * Contains similarity score and context information for Phase 2 (LLM reasoning).
 */
public class ArtifactCandidate {
    private String artifactId;
    private String name;
    private String type;
    private String description;
    private String shortDescription;
    private double similarityScore;
    private List<String> sourceNoteIds;
    
    /**
     * Default constructor
     */
    public ArtifactCandidate() {
        this.sourceNoteIds = new ArrayList<>();
    }
    
    /**
     * Constructor with essential fields
     */
    public ArtifactCandidate(String artifactId, String name, String type, String description, double similarityScore) {
        this();
        this.artifactId = artifactId;
        this.name = name;
        this.type = type;
        this.description = description;
        this.similarityScore = similarityScore;
    }
    
    /**
     * Constructor with all fields
     */
    public ArtifactCandidate(String artifactId, String name, String type, String description, 
                            String shortDescription, double similarityScore, List<String> sourceNoteIds) {
        this();
        this.artifactId = artifactId;
        this.name = name;
        this.type = type;
        this.description = description;
        this.shortDescription = shortDescription;
        this.similarityScore = similarityScore;
        this.sourceNoteIds = sourceNoteIds != null ? new ArrayList<>(sourceNoteIds) : new ArrayList<>();
    }
    
    // Getters and setters
    public String getArtifactId() {
        return artifactId;
    }
    
    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getShortDescription() {
        return shortDescription;
    }
    
    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
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
        return String.format("ArtifactCandidate[id=%s, name='%s', type='%s', similarity=%.3f]",
                artifactId, name, type, similarityScore);
    }
}

