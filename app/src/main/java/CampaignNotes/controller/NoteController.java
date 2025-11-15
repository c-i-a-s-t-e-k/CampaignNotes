package CampaignNotes.controller;

import java.util.ArrayList;
import java.util.List;

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

import CampaignNotes.ArtifactGraphService;
import CampaignNotes.ArtifactMergeService;
import CampaignNotes.CampaignManager;
import CampaignNotes.DeduplicationSessionManager;
import CampaignNotes.GraphEmbeddingService;
import CampaignNotes.NoteService;
import CampaignNotes.dto.NoteConfirmationRequest;
import CampaignNotes.dto.NoteCreateRequest;
import CampaignNotes.dto.NoteCreateResponse;
import CampaignNotes.dto.NoteDTO;
import CampaignNotes.dto.deduplication.MergeProposal;
import jakarta.validation.Valid;
import model.Artifact;
import model.Campain;
import model.Note;
import model.Relationship;

/**
 * REST Controller for note management.
 * Provides endpoints for creating and retrieving campaign notes.
 */
@RestController
@RequestMapping("/api/campaigns/{campaignUuid}/notes")
@Validated
public class NoteController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NoteController.class);
    
    private final NoteService noteService;
    private final CampaignManager campaignManager;
    private final DeduplicationSessionManager sessionManager;
    private final ArtifactMergeService mergeService;
    private final ArtifactGraphService artifactService;
    private final GraphEmbeddingService graphEmbeddingService;
    
    public NoteController(NoteService noteService, CampaignManager campaignManager,
                         DeduplicationSessionManager sessionManager, 
                         ArtifactMergeService mergeService,
                         ArtifactGraphService artifactService,
                         GraphEmbeddingService graphEmbeddingService) {
        this.noteService = noteService;
        this.campaignManager = campaignManager;
        this.sessionManager = sessionManager;
        this.mergeService = mergeService;
        this.artifactService = artifactService;
        this.graphEmbeddingService = graphEmbeddingService;
    }
    
    /**
     * Create a new note in a campaign.
     * 
     * @param campaignUuid UUID of the campaign
     * @param request Note creation request
     * @return Response with note details and extracted artifacts
     */
    @PostMapping
    public ResponseEntity<NoteCreateResponse> createNote(
            @PathVariable String campaignUuid,
            @Valid @RequestBody NoteCreateRequest request) {
        
        LOGGER.info("POST /api/campaigns/{}/notes - Creating note: {}", campaignUuid, request.getTitle());
        
        // Validate campaign exists
        Campain campaign = campaignManager.getCampaignByUuid(campaignUuid);
        if (campaign == null) {
            LOGGER.warn("Campaign not found: {}", campaignUuid);
            NoteCreateResponse errorResponse = new NoteCreateResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Campaign not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
        
        // Validate note content (500 words limit)
        if (!request.isValid()) {
            String error = request.getValidationError();
            LOGGER.warn("Note validation failed: {}", error);
            NoteCreateResponse errorResponse = new NoteCreateResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage(error);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
        
        try {
            // Create note object
            Note note = new Note(campaignUuid, request.getTitle(), request.getContent());
            
            // Add note to campaign (this triggers embedding generation and artifact extraction)
            NoteCreateResponse response = noteService.addNoteWithResponse(note, campaign);
            
            if (!response.isSuccess()) {
                LOGGER.error("Failed to add note to campaign: {}", response.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
            LOGGER.info("Note created successfully: {} with {} artifacts and {} relationships", 
                       response.getNoteId(), 
                       response.getArtifactCount(), 
                       response.getRelationshipCount());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            LOGGER.error("Error creating note: {}", e.getMessage(), e);
            NoteCreateResponse errorResponse = new NoteCreateResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Get note details by ID.
     * 
     * @param campaignUuid UUID of the campaign
     * @param noteId ID of the note
     * @return Note details
     */
    @GetMapping("/{noteId}")
    public ResponseEntity<NoteDTO> getNote(
            @PathVariable String campaignUuid,
            @PathVariable String noteId) {
        
        LOGGER.info("GET /api/campaigns/{}/notes/{} - Fetching note", campaignUuid, noteId);
        
        // Validate campaign exists
        Campain campaign = campaignManager.getCampaignByUuid(campaignUuid);
        if (campaign == null) {
            LOGGER.warn("Campaign not found: {}", campaignUuid);
            return ResponseEntity.notFound().build();
        }
        
        try {
            // Retrieve note from Qdrant
            String collectionName = campaign.getQuadrantCollectionName();
            List<Note> notes = noteService.getNotesByIds(List.of(noteId), collectionName);
            
            if (notes.isEmpty()) {
                LOGGER.warn("Note not found: {}", noteId);
                return ResponseEntity.notFound().build();
            }
            
            Note note = notes.get(0);
            NoteDTO dto = convertToDTO(note);
            
            return ResponseEntity.ok(dto);
            
        } catch (Exception e) {
            LOGGER.error("Error fetching note: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Confirm deduplication decisions after note creation.
     * This endpoint processes user confirmation of merge proposals.
     * 
     * @param campaignUuid UUID of the campaign
     * @param noteId ID of the note
     * @param request Confirmation request with approved merge proposals
     * @return Final response with merged counts
     */
    @PostMapping("/{noteId}/confirm-deduplication")
    public ResponseEntity<NoteCreateResponse> confirmDeduplication(
            @PathVariable String campaignUuid,
            @PathVariable String noteId,
            @Valid @RequestBody NoteConfirmationRequest request) {
        
        LOGGER.info("POST /api/campaigns/{}/notes/{}/confirm-deduplication - Processing deduplication confirmation", 
                   campaignUuid, noteId);
        
        // 1. Validate campaign exists
        Campain campaign = campaignManager.getCampaignByUuid(campaignUuid);
        if (campaign == null) {
            LOGGER.warn("Campaign not found: {}", campaignUuid);
            NoteCreateResponse errorResponse = new NoteCreateResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Campaign not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
        
        // 2. Retrieve session from DeduplicationSessionManager
        DeduplicationSessionManager.DeduplicationSession session = sessionManager.getSession(noteId);
        
        if (session == null) {
            LOGGER.warn("Deduplication session not found or expired for note: {}", noteId);
            NoteCreateResponse errorResponse = new NoteCreateResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Deduplication session not found or expired. Please try creating the note again.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
        
        // 3. Validate campaign UUID matches
        if (!campaign.getUuid().equals(session.getCampaignUuid())) {
            LOGGER.warn("Campaign UUID mismatch. Expected: {}, Got: {}", 
                       session.getCampaignUuid(), campaignUuid);
            NoteCreateResponse errorResponse = new NoteCreateResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Campaign UUID mismatch");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
        
        try {
            List<Artifact> pendingArtifacts = session.getPendingArtifacts();
            List<Relationship> pendingRelationships = session.getPendingRelationships();
            List<MergeProposal> approvedProposals = request.getApprovedMergeProposals();
            
            LOGGER.info("Processing {} approved proposals for note {}", approvedProposals.size(), noteId);
            
            int mergedArtifactCount = 0;
            int mergedRelationshipCount = 0;
            int newArtifactCount = 0;
            int newRelationshipCount = 0;
            
            // Track which items were merged to avoid saving them again
            List<String> mergedArtifactIds = new ArrayList<>();
            List<String> mergedRelationshipIds = new ArrayList<>();
            
            // 4. For each approved proposal, execute merge
            for (MergeProposal proposal : approvedProposals) {
                if (!proposal.isApproved()) {
                    continue; // Skip non-approved proposals
                }
                
                if ("artifact".equals(proposal.getItemType())) {
                    // Find the artifact
                    Artifact artifact = pendingArtifacts.stream()
                        .filter(a -> a.getId().equals(proposal.getNewItemId()))
                        .findFirst()
                        .orElse(null);
                    
                    if (artifact != null) {
                        boolean merged = mergeService.mergeArtifacts(
                            proposal.getExistingItemName(), 
                            artifact, 
                            campaign.getNeo4jLabel()
                        );
                        
                        if (merged) {
                            mergedArtifactCount++;
                            mergedArtifactIds.add(artifact.getId());
                            LOGGER.debug("Merged artifact: {} -> {}", 
                                       artifact.getName(), proposal.getExistingItemName());
                        } else {
                            LOGGER.warn("Failed to merge artifact: {}", artifact.getName());
                        }
                    }
                    
                } else if ("relationship".equals(proposal.getItemType())) {
                    // Find the relationship
                    Relationship relationship = pendingRelationships.stream()
                        .filter(r -> r.getId().equals(proposal.getNewItemId()))
                        .findFirst()
                        .orElse(null);
                    
                    if (relationship != null) {
                        // For relationships, we need source and target artifact names
                        // The candidate name contains the relationship label
                        boolean merged = mergeService.mergeRelationships(
                            relationship.getSourceArtifactName(),
                            relationship.getTargetArtifactName(),
                            proposal.getExistingItemName(), // existing relationship label
                            relationship,
                            campaign.getNeo4jLabel()
                        );
                        
                        if (merged) {
                            mergedRelationshipCount++;
                            mergedRelationshipIds.add(relationship.getId());
                            LOGGER.debug("Merged relationship: {}", relationship.getLabel());
                        } else {
                            LOGGER.warn("Failed to merge relationship: {}", relationship.getLabel());
                        }
                    }
                }
            }
            
            // 5. Save new artifacts/relationships (not merged) to Neo4j
            List<Artifact> newArtifacts = pendingArtifacts.stream()
                .filter(a -> !mergedArtifactIds.contains(a.getId()))
                .collect(java.util.stream.Collectors.toList());
                
            List<Relationship> newRelationships = pendingRelationships.stream()
                .filter(r -> !mergedRelationshipIds.contains(r.getId()))
                .collect(java.util.stream.Collectors.toList());
            
            boolean saved = artifactService.saveToNeo4j(newArtifacts, newRelationships, campaign);
            
            if (!saved) {
                LOGGER.error("Failed to save new artifacts/relationships to Neo4j");
                NoteCreateResponse errorResponse = new NoteCreateResponse();
                errorResponse.setSuccess(false);
                errorResponse.setMessage("Failed to save artifacts to knowledge graph");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
            
            newArtifactCount = newArtifacts.size();
            newRelationshipCount = newRelationships.size();
            
            // 6. Store embeddings in Qdrant for new artifacts/relationships
            try {
                String collectionName = campaign.getQuadrantCollectionName();
                
                for (Artifact artifact : newArtifacts) {
                    var embeddingResult = graphEmbeddingService.generateArtifactEmbedding(artifact);
                    graphEmbeddingService.storeArtifactEmbedding(artifact, embeddingResult.getEmbedding(), collectionName);
                }
                
                for (Relationship relationship : newRelationships) {
                    var embeddingResult = graphEmbeddingService.generateRelationshipEmbedding(relationship);
                    graphEmbeddingService.storeRelationshipEmbedding(relationship, embeddingResult.getEmbedding(), collectionName);
                }
                
                LOGGER.info("Stored embeddings for {} artifacts and {} relationships", 
                           newArtifacts.size(), newRelationships.size());
                
            } catch (Exception e) {
                LOGGER.error("Error storing embeddings: {}", e.getMessage(), e);
                // Don't fail the entire request if embedding storage fails
            }
            
            // 7. Clear session
            sessionManager.removeSession(noteId);
            LOGGER.info("Deduplication session cleared for note: {}", noteId);
            
            // 8. Return success response with final counts
            NoteCreateResponse response = new NoteCreateResponse();
            response.setNoteId(noteId);
            response.setSuccess(true);
            response.setMessage("Deduplication completed successfully");
            response.setArtifactCount(newArtifactCount + mergedArtifactCount);
            response.setRelationshipCount(newRelationshipCount + mergedRelationshipCount);
            response.setMergedArtifactCount(mergedArtifactCount);
            response.setMergedRelationshipCount(mergedRelationshipCount);
            response.setRequiresUserConfirmation(false);
            
            LOGGER.info("Deduplication completed: {} merged artifacts, {} new artifacts, " +
                       "{} merged relationships, {} new relationships",
                       mergedArtifactCount, newArtifactCount, 
                       mergedRelationshipCount, newRelationshipCount);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            LOGGER.error("Error processing deduplication confirmation: {}", e.getMessage(), e);
            NoteCreateResponse errorResponse = new NoteCreateResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Converts Note model to NoteDTO.
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

