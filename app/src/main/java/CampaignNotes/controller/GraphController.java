package CampaignNotes.controller;

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
import CampaignNotes.dto.GraphDTO;
import model.Campain;

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
    
    public GraphController(GraphService graphService, CampaignManager campaignManager) {
        this.graphService = graphService;
        this.campaignManager = campaignManager;
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
}

