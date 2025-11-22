package CampaignNotes.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import CampaignNotes.CampaignManager;
import CampaignNotes.assistant.AssistantOrchestrator;
import CampaignNotes.dto.AssistantQueryRequest;
import CampaignNotes.dto.AssistantResponse;
import jakarta.validation.Valid;
import model.Campain;

/**
 * REST Controller for the Assistant Agent.
 * Provides endpoints for querying campaign data using natural language.
 */
@RestController
@RequestMapping("/api/campaigns/{campaignUuid}/assistant")
@Validated
public class AssistantController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AssistantController.class);
    
    private final AssistantOrchestrator orchestrator;
    private final CampaignManager campaignManager;
    
    public AssistantController(AssistantOrchestrator orchestrator, CampaignManager campaignManager) {
        this.orchestrator = orchestrator;
        this.campaignManager = campaignManager;
    }
    
    /**
     * Process an assistant query for a campaign.
     * 
     * @param campaignUuid UUID of the campaign to query
     * @param request the query request with user's question
     * @return AssistantResponse with the result
     */
    @PostMapping("/query")
    public ResponseEntity<AssistantResponse> query(
            @PathVariable String campaignUuid,
            @Valid @RequestBody AssistantQueryRequest request) {
        
        LOGGER.info("POST /api/campaigns/{}/assistant/query - Query: '{}'", 
                   campaignUuid, request.getQuery());
        
        // Validate campaign exists
        Campain campaign = campaignManager.getCampaignByUuid(campaignUuid);
        if (campaign == null) {
            LOGGER.warn("Campaign not found: {}", campaignUuid);
            return ResponseEntity.notFound().build();
        }
        
        try {
            // Process query through orchestrator
            AssistantResponse response = orchestrator.processQuery(campaignUuid, request.getQuery());
            
            LOGGER.info("Query processed successfully. Response type: {}", response.getResponseType());
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid query request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            LOGGER.error("Error processing assistant query: {}", e.getMessage(), e);
            
            AssistantResponse errorResponse = AssistantResponse.error(
                "internal_error",
                "Przepraszam, wystąpił nieoczekiwany błąd. Spróbuj ponownie.",
                null
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}

