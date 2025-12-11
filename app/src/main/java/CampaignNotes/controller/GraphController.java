package CampaignNotes.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import CampaignNotes.CampaignManager;
import CampaignNotes.GraphService;
import CampaignNotes.NoteService;
import CampaignNotes.database.DatabaseConnectionManager;
import CampaignNotes.dto.ArtifactDTO;
import CampaignNotes.dto.ArtifactPairDTO;
import CampaignNotes.dto.GraphDTO;
import CampaignNotes.dto.NodeDTO;
import CampaignNotes.dto.NoteDTO;
import CampaignNotes.dto.RelationTypeDTO;
import model.Campain;
import model.Note;

/**
 * REST Controller for graph visualization.
 * Provides endpoints for retrieving campaign knowledge graphs.
 */
@RestController
@RequestMapping("/api/campaigns/{campaignUuid}/graph")
public class GraphController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphController.class);
    
    private final GraphService graphService;
    private final CampaignManager campaignManager;
    private final NoteService noteService;
    private final DatabaseConnectionManager dbConnectionManager;
    
    public GraphController(GraphService graphService, CampaignManager campaignManager, 
                          NoteService noteService, DatabaseConnectionManager dbConnectionManager) {
        this.graphService = graphService;
        this.campaignManager = campaignManager;
        this.noteService = noteService;
        this.dbConnectionManager = dbConnectionManager;
    }
    
    /**
     * Get the full knowledge graph for a campaign.
     * 
     * @param campaignUuid UUID of the campaign
     * @return Graph with nodes and edges
     */
    @GetMapping
    public ResponseEntity<GraphDTO> getCampaignGraph(@PathVariable String campaignUuid) {
        LOGGER.info("GET /api/campaigns/{}/graph - Fetching campaign graph", campaignUuid);
        
        // Validate campaign exists
        Campain campaign = campaignManager.getCampaignByUuid(campaignUuid);
        if (campaign == null) {
            LOGGER.warn("Campaign not found: {}", campaignUuid);
            return ResponseEntity.notFound().build();
        }
        
        try {
            GraphDTO graph = graphService.getGraphForCampaign(campaignUuid);
            
            LOGGER.info("Graph fetched: {} nodes, {} edges", 
                       graph.getNodes().size(), graph.getEdges().size());
            
            return ResponseEntity.ok(graph);
            
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            LOGGER.error("Error fetching graph: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get graph filtered by a specific note.
     * Returns only nodes and edges associated with the given note.
     * 
     * @param campaignUuid UUID of the campaign
     * @param noteId ID of the note to filter by
     * @return Graph with filtered nodes and edges
     */
    @GetMapping("/notes/{noteId}")
    public ResponseEntity<GraphDTO> getGraphForNote(
            @PathVariable String campaignUuid,
            @PathVariable String noteId) {
        
        LOGGER.info("GET /api/campaigns/{}/graph/notes/{} - Fetching graph filtered by note", 
                   campaignUuid, noteId);
        
        // Validate campaign exists
        Campain campaign = campaignManager.getCampaignByUuid(campaignUuid);
        if (campaign == null) {
            LOGGER.warn("Campaign not found: {}", campaignUuid);
            return ResponseEntity.notFound().build();
        }
        
        try {
            GraphDTO graph = graphService.getGraphForNote(campaignUuid, noteId);
            
            LOGGER.info("Filtered graph fetched: {} nodes, {} edges", 
                       graph.getNodes().size(), graph.getEdges().size());
            
            return ResponseEntity.ok(graph);
            
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            LOGGER.error("Error fetching filtered graph: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get neighbors (directly connected artifacts) for a specific artifact.
     * Used for graph expansion on double click.
     * 
     * @param campaignUuid UUID of the campaign
     * @param artifactId ID of the artifact
     * @return Graph with the artifact and its neighbors
     */
    @GetMapping("/artifacts/{artifactId}/neighbors")
    public ResponseEntity<GraphDTO> getArtifactNeighbors(
            @PathVariable String campaignUuid,
            @PathVariable String artifactId) {
        
        LOGGER.info("GET /api/campaigns/{}/graph/artifacts/{}/neighbors - Fetching artifact neighbors", 
                   campaignUuid, artifactId);
        
        // Validate campaign exists
        Campain campaign = campaignManager.getCampaignByUuid(campaignUuid);
        if (campaign == null) {
            LOGGER.warn("Campaign not found: {}", campaignUuid);
            return ResponseEntity.notFound().build();
        }
        
        try {
            GraphDTO graph = graphService.getArtifactNeighbors(campaignUuid, artifactId);
            
            LOGGER.info("Neighbors fetched: {} nodes, {} edges", 
                       graph.getNodes().size(), graph.getEdges().size());
            
            return ResponseEntity.ok(graph);
            
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            LOGGER.error("Error fetching neighbors: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get all notes associated with a specific artifact.
     * 
     * @param campaignUuid UUID of the campaign
     * @param artifactId ID of the artifact
     * @return List of notes associated with the artifact
     */
    @GetMapping("/artifacts/{artifactId}/notes")
    public ResponseEntity<List<NoteDTO>> getArtifactNotes(
            @PathVariable String campaignUuid,
            @PathVariable String artifactId) {
        
        LOGGER.info("GET /api/campaigns/{}/graph/artifacts/{}/notes - Fetching artifact notes", 
                   campaignUuid, artifactId);
        
        // Validate campaign exists
        Campain campaign = campaignManager.getCampaignByUuid(campaignUuid);
        if (campaign == null) {
            LOGGER.warn("Campaign not found: {}", campaignUuid);
            return ResponseEntity.notFound().build();
        }
        
        try {
            // Get the full graph to find the artifact
            GraphDTO graph = graphService.getGraphForCampaign(campaignUuid);
            
            // Find the artifact node
            NodeDTO artifactNode = graph.getNodes().stream()
                .filter(node -> node.getId().equals(artifactId))
                .findFirst()
                .orElse(null);
            
            if (artifactNode == null) {
                LOGGER.warn("Artifact not found: {}", artifactId);
                return ResponseEntity.notFound().build();
            }
            
            // Get note IDs from the artifact
            List<String> noteIds = artifactNode.getNoteIds();
            
            if (noteIds == null || noteIds.isEmpty()) {
                LOGGER.info("No notes found for artifact: {}", artifactId);
                return ResponseEntity.ok(new ArrayList<>());
            }
            
            // Retrieve notes from Qdrant
            String collectionName = campaign.getQuadrantCollectionName();
            List<Note> notes = noteService.getNotesByIds(noteIds, collectionName);
            
            // Convert to DTOs
            List<NoteDTO> noteDTOs = new ArrayList<>();
            for (Note note : notes) {
                noteDTOs.add(convertToDTO(note));
            }
            
            LOGGER.info("Found {} notes for artifact {}", noteDTOs.size(), artifactId);
            return ResponseEntity.ok(noteDTOs);
            
        } catch (Exception e) {
            LOGGER.error("Error fetching artifact notes: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get all artifacts for a campaign.
     * 
     * @param campaignUuid UUID of the campaign
     * @return List of all artifacts in the campaign
     */
    @GetMapping("/artifacts")
    public ResponseEntity<List<ArtifactDTO>> getAllArtifacts(@PathVariable String campaignUuid) {
        LOGGER.info("GET /api/campaigns/{}/graph/artifacts - Fetching all artifacts", campaignUuid);
        
        // Validate campaign exists
        Campain campaign = campaignManager.getCampaignByUuid(campaignUuid);
        if (campaign == null) {
            LOGGER.warn("Campaign not found: {}", campaignUuid);
            return ResponseEntity.notFound().build();
        }
        
        try {
            List<Map<String, Object>> artifacts = dbConnectionManager.getNeo4jRepository()
                .getAllArtifacts(campaignUuid);
            
            // Convert to DTOs
            List<ArtifactDTO> artifactDTOs = new ArrayList<>();
            for (Map<String, Object> artifact : artifacts) {
                artifactDTOs.add(new ArtifactDTO(
                    (String) artifact.get("id"),
                    (String) artifact.get("name"),
                    (String) artifact.get("type"),
                    (String) artifact.get("description")
                ));
            }
            
            LOGGER.info("Returning {} artifacts", artifactDTOs.size());
            return ResponseEntity.ok(artifactDTOs);
            
        } catch (Exception e) {
            LOGGER.error("Error fetching artifacts: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get all relationship types for a campaign.
     * 
     * @param campaignUuid UUID of the campaign
     * @return List of unique relationship types
     */
    @GetMapping("/relations")
    public ResponseEntity<List<RelationTypeDTO>> getAllRelationTypes(@PathVariable String campaignUuid) {
        LOGGER.info("GET /api/campaigns/{}/graph/relations - Fetching all relation types", campaignUuid);
        
        // Validate campaign exists
        Campain campaign = campaignManager.getCampaignByUuid(campaignUuid);
        if (campaign == null) {
            LOGGER.warn("Campaign not found: {}", campaignUuid);
            return ResponseEntity.notFound().build();
        }
        
        try {
            List<String> relationTypes = dbConnectionManager.getNeo4jRepository()
                .getAllRelationshipTypes(campaignUuid);
            
            // Convert to DTOs (count will be computed later if needed)
            List<RelationTypeDTO> relationDTOs = new ArrayList<>();
            for (String label : relationTypes) {
                relationDTOs.add(new RelationTypeDTO(label, 0)); // Count placeholder
            }
            
            LOGGER.info("Returning {} relation types", relationDTOs.size());
            return ResponseEntity.ok(relationDTOs);
            
        } catch (Exception e) {
            LOGGER.error("Error fetching relation types: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get all artifact pairs connected by a specific relationship type.
     * 
     * @param campaignUuid UUID of the campaign
     * @param relationLabel The relationship label to filter by
     * @return List of artifact pairs with the specified relationship
     */
    @GetMapping("/relations/{relationLabel}/pairs")
    public ResponseEntity<List<ArtifactPairDTO>> getArtifactPairsByRelation(
            @PathVariable String campaignUuid,
            @PathVariable String relationLabel) {
        
        LOGGER.info("GET /api/campaigns/{}/graph/relations/{}/pairs - Fetching artifact pairs", 
                   campaignUuid, relationLabel);
        
        // Validate campaign exists
        Campain campaign = campaignManager.getCampaignByUuid(campaignUuid);
        if (campaign == null) {
            LOGGER.warn("Campaign not found: {}", campaignUuid);
            return ResponseEntity.notFound().build();
        }
        
        try {
            List<Map<String, Object>> pairs = dbConnectionManager.getNeo4jRepository()
                .getArtifactPairsByRelationType(campaignUuid, relationLabel);
            
            // Convert to DTOs
            List<ArtifactPairDTO> pairDTOs = new ArrayList<>();
            for (Map<String, Object> pair : pairs) {
                @SuppressWarnings("unchecked")
                Map<String, Object> sourceMap = (Map<String, Object>) pair.get("source");
                @SuppressWarnings("unchecked")
                Map<String, Object> targetMap = (Map<String, Object>) pair.get("target");
                
                ArtifactDTO source = new ArtifactDTO(
                    (String) sourceMap.get("id"),
                    (String) sourceMap.get("name"),
                    (String) sourceMap.get("type"),
                    null
                );
                
                ArtifactDTO target = new ArtifactDTO(
                    (String) targetMap.get("id"),
                    (String) targetMap.get("name"),
                    (String) targetMap.get("type"),
                    null
                );
                
                pairDTOs.add(new ArtifactPairDTO(
                    source,
                    target,
                    (String) pair.get("relationshipId"),
                    (String) pair.get("label"),
                    (String) pair.get("description")
                ));
            }
            
            LOGGER.info("Returning {} artifact pairs for relation: {}", pairDTOs.size(), relationLabel);
            return ResponseEntity.ok(pairDTOs);
            
        } catch (Exception e) {
            LOGGER.error("Error fetching artifact pairs: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Converts a Note entity to NoteDTO.
     */
    private NoteDTO convertToDTO(Note note) {
        return new NoteDTO(
            note.getId(),
            note.getCampaignUuid(),
            note.getTitle(),
            note.getContent(),
            note.getCreatedAt(),
            note.getUpdatedAt()
        );
    }
}

