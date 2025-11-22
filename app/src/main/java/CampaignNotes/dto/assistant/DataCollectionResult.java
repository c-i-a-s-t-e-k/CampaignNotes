package CampaignNotes.dto.assistant;

import java.util.ArrayList;
import java.util.List;

import CampaignNotes.dto.GraphDTO;
import CampaignNotes.dto.SourceReference;
import model.Artifact;
import model.Note;
import model.Relationship;

public class DataCollectionResult {
    private List<String> foundNoteIds;
    private String foundArtifactId;
    private String foundArtifactName;
    private String foundArtifactType;  // Add artifact type
    private String foundRelationshipId;
    private List<SourceReference> sources;
    private int sourcesCount;
    private GraphDTO graphData; // Added to hold graph data
    
    // Full objects for LLM context
    private List<Note> notes;
    private List<Artifact> artifacts;
    private List<Relationship> relationships;

    // Getters and Setters
    public List<String> getFoundNoteIds() {
        return foundNoteIds;
    }

    public void setFoundNoteIds(List<String> foundNoteIds) {
        this.foundNoteIds = foundNoteIds;
    }

    public String getFoundArtifactId() {
        return foundArtifactId;
    }

    public void setFoundArtifactId(String foundArtifactId) {
        this.foundArtifactId = foundArtifactId;
    }

    public String getFoundArtifactName() {
        return foundArtifactName;
    }

    public void setFoundArtifactName(String foundArtifactName) {
        this.foundArtifactName = foundArtifactName;
    }
    
    public String getFoundArtifactType() {
        return foundArtifactType;
    }

    public void setFoundArtifactType(String foundArtifactType) {
        this.foundArtifactType = foundArtifactType;
    }

    public String getFoundRelationshipId() {
        return foundRelationshipId;
    }

    public void setFoundRelationshipId(String foundRelationshipId) {
        this.foundRelationshipId = foundRelationshipId;
    }

    public List<SourceReference> getSources() {
        return sources;
    }

    public void setSources(List<SourceReference> sources) {
        this.sources = sources;
    }

    public int getSourcesCount() {
        return sourcesCount;
    }

    public void setSourcesCount(int sourcesCount) {
        this.sourcesCount = sourcesCount;
    }
    
    public GraphDTO getGraphData() {
        return graphData;
    }

    public void setGraphData(GraphDTO graphData) {
        this.graphData = graphData;
    }
    
    // Getters and Setters for full objects
    public List<Note> getNotes() {
        return notes != null ? notes : new ArrayList<>();
    }
    
    public void setNotes(List<Note> notes) {
        this.notes = notes;
    }
    
    public List<Artifact> getArtifacts() {
        return artifacts != null ? artifacts : new ArrayList<>();
    }
    
    public void setArtifacts(List<Artifact> artifacts) {
        this.artifacts = artifacts;
    }
    
    public List<Relationship> getRelationships() {
        return relationships != null ? relationships : new ArrayList<>();
    }
    
    public void setRelationships(List<Relationship> relationships) {
        this.relationships = relationships;
    }
}
