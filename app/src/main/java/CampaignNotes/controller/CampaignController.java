package CampaignNotes.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import CampaignNotes.CampaignManager;
import CampaignNotes.dto.CampaignCreateRequest;
import CampaignNotes.dto.CampaignDTO;
import jakarta.validation.Valid;
import model.Campain;

/**
 * REST Controller for campaign management.
 * Provides endpoints for creating, listing, and retrieving campaigns.
 */
@RestController
@RequestMapping("/api/campaigns")
@Validated
public class CampaignController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CampaignController.class);
    
    private final CampaignManager campaignManager;
    
    public CampaignController(CampaignManager campaignManager) {
        this.campaignManager = campaignManager;
    }
    
    /**
     * Get all campaigns.
     * 
     * @return List of all campaigns
     */
    @GetMapping
    public ResponseEntity<List<CampaignDTO>> getAllCampaigns() {
        LOGGER.info("GET /api/campaigns - Fetching all campaigns");
        
        List<Campain> campaigns = campaignManager.getAllCampaigns();
        List<CampaignDTO> campaignDTOs = campaigns.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
        
        LOGGER.info("Returning {} campaigns", campaignDTOs.size());
        return ResponseEntity.ok(campaignDTOs);
    }
    
    /**
     * Get campaign by UUID.
     * 
     * @param uuid Campaign UUID
     * @return Campaign details
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<CampaignDTO> getCampaign(@PathVariable String uuid) {
        LOGGER.info("GET /api/campaigns/{} - Fetching campaign", uuid);
        
        Campain campaign = campaignManager.getCampaignByUuid(uuid);
        
        if (campaign == null) {
            LOGGER.warn("Campaign not found: {}", uuid);
            return ResponseEntity.notFound().build();
        }
        
        CampaignDTO dto = convertToDTO(campaign);
        return ResponseEntity.ok(dto);
    }
    
    /**
     * Create a new campaign.
     * 
     * @param request Campaign creation request
     * @return Created campaign details
     */
    @PostMapping
    public ResponseEntity<CampaignDTO> createCampaign(@Valid @RequestBody CampaignCreateRequest request) {
        LOGGER.info("POST /api/campaigns - Creating campaign: {}", request.getName());
        
        try {
            Campain newCampaign = campaignManager.createNewCampain(request.getName());
            
            if (newCampaign == null) {
                LOGGER.error("Failed to create campaign: {}", request.getName());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
            
            // Update description if provided
            if (request.getDescription() != null && !request.getDescription().isEmpty()) {
                newCampaign.setDescription(request.getDescription());
            }
            
            CampaignDTO dto = convertToDTO(newCampaign);
            LOGGER.info("Campaign created successfully: {}", dto.getUuid());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
            
        } catch (Exception e) {
            LOGGER.error("Error creating campaign: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Converts Campaign model to CampaignDTO.
     */
    private CampaignDTO convertToDTO(Campain campaign) {
        return new CampaignDTO(
            campaign.getUuid(),
            campaign.getName(),
            campaign.getDescription(),
            campaign.getCreatedAt(),
            campaign.getUpdatedAt(),
            campaign.isActive()
        );
    }
}

