package CampaignNotes.controller;

import java.util.ArrayList;
import java.util.List;

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
import CampaignNotes.NoteService;
import CampaignNotes.SemantickSearchService;
import CampaignNotes.dto.SearchRequest;
import CampaignNotes.dto.SearchResultDTO;
import jakarta.validation.Valid;
import model.Campain;
import model.Note;

/**
 * REST Controller for semantic search.
 * Provides endpoints for searching notes within campaigns.
 */
@RestController
@RequestMapping("/api/campaigns/{campaignUuid}/search")
@Validated
public class SearchController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchController.class);
    
    private final SemantickSearchService searchService;
    private final NoteService noteService;
    private final CampaignManager campaignManager;
    
    public SearchController(SemantickSearchService searchService, NoteService noteService, 
                           CampaignManager campaignManager) {
        this.searchService = searchService;
        this.noteService = noteService;
        this.campaignManager = campaignManager;
    }
    
    /**
     * Perform semantic search on campaign notes.
     * 
     * @param campaignUuid UUID of the campaign to search in
     * @param request Search request with query and optional limit
     * @return List of search results
     */
    @PostMapping
    public ResponseEntity<List<SearchResultDTO>> search(
            @PathVariable String campaignUuid,
            @Valid @RequestBody SearchRequest request) {
        
        LOGGER.info("POST /api/campaigns/{}/search - Query: '{}', Limit: {}", 
                   campaignUuid, request.getQuery(), request.getLimit());
        
        // Validate campaign exists
        Campain campaign = campaignManager.getCampaignByUuid(campaignUuid);
        if (campaign == null) {
            LOGGER.warn("Campaign not found: {}", campaignUuid);
            return ResponseEntity.notFound().build();
        }
        
        try {
            // Perform semantic search
            List<String> noteIds = searchService.searchSemanticklyNotes(
                request.getQuery(), 
                request.getLimit(), 
                campaignUuid
            );
            
            if (noteIds.isEmpty()) {
                LOGGER.info("No results found for query: '{}'", request.getQuery());
                return ResponseEntity.ok(new ArrayList<>());
            }
            
            // Retrieve full note details
            String collectionName = campaign.getQuadrantCollectionName();
            List<Note> notes = noteService.getNotesByIds(noteIds, collectionName);
            
            // Convert to search result DTOs
            List<SearchResultDTO> results = new ArrayList<>();
            for (Note note : notes) {
                String contentPreview = note.getContent().length() > 200 
                    ? note.getContent().substring(0, 200) + "..." 
                    : note.getContent();
                
                SearchResultDTO result = new SearchResultDTO(
                    note.getId(),
                    note.getTitle(),
                    contentPreview,
                    1.0 // Placeholder score - Qdrant returns similarity scores but we're not capturing them here
                );
                
                results.add(result);
            }
            
            LOGGER.info("Returning {} search results", results.size());
            return ResponseEntity.ok(results);
            
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid search request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            LOGGER.error("Error performing search: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

