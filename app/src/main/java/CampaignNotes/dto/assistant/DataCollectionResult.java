package CampaignNotes.dto.assistant;

import CampaignNotes.dto.GraphDTO;
import CampaignNotes.dto.SourceReference;
import java.util.List;

public class DataCollectionResult {
    private List<String> foundNoteIds;
    private String foundArtifactId;
    private String foundArtifactName;
    private String foundArtifactType;  // Add artifact type
    private String foundRelationshipId;
    private List<SourceReference> sources;
    private int sourcesCount;
    private GraphDTO graphData; // Added to hold graph data

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
}
