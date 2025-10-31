package CampaignNotes.controller;

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

import CampaignNotes.CampaignManager;
import CampaignNotes.NoteService;
import CampaignNotes.dto.NoteCreateRequest;
import CampaignNotes.dto.NoteCreateResponse;
import CampaignNotes.dto.NoteDTO;
import jakarta.validation.Valid;
import model.Campain;
import model.Note;

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
    
    public NoteController(NoteService noteService, CampaignManager campaignManager) {
        this.noteService = noteService;
        this.campaignManager = campaignManager;
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

