package CampaignNotes.controller;

import java.util.ArrayList;
import java.util.List;

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
import CampaignNotes.dto.GraphDTO;
import CampaignNotes.dto.NodeDTO;
import CampaignNotes.dto.NoteDTO;
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
    
    public GraphController(GraphService graphService, CampaignManager campaignManager, NoteService noteService) {
        this.graphService = graphService;
        this.campaignManager = campaignManager;
        this.noteService = noteService;
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

