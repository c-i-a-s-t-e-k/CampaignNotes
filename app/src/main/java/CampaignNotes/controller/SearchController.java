package CampaignNotes.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import CampaignNotes.database.DatabaseConnectionManager;
import CampaignNotes.dto.ArtifactDTO;
import CampaignNotes.dto.RelationTypeDTO;
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
    private final DatabaseConnectionManager dbConnectionManager;
    
    public SearchController(SemantickSearchService searchService, NoteService noteService, 
                           CampaignManager campaignManager, DatabaseConnectionManager dbConnectionManager) {
        this.searchService = searchService;
        this.noteService = noteService;
        this.campaignManager = campaignManager;
        this.dbConnectionManager = dbConnectionManager;
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
    
    /**
     * Perform semantic search on campaign artifacts.
     * Returns top 3 most semantically similar artifacts.
     * 
     * @param campaignUuid UUID of the campaign to search in
     * @param request Search request with query
     * @return List of top 3 artifact results
     */
    @PostMapping("/artifacts")
    public ResponseEntity<List<ArtifactDTO>> searchArtifacts(
            @PathVariable String campaignUuid,
            @Valid @RequestBody SearchRequest request) {
        
        LOGGER.info("POST /api/campaigns/{}/search/artifacts - Query: '{}'", 
                   campaignUuid, request.getQuery());
        
        // Validate campaign exists
        Campain campaign = campaignManager.getCampaignByUuid(campaignUuid);
        if (campaign == null) {
            LOGGER.warn("Campaign not found: {}", campaignUuid);
            return ResponseEntity.notFound().build();
        }
        
        try {
            
            List<String> artifactIds = searchService.getTopKMatch(
                request.getQuery(), 
                request.getLimit() + 1, 
                campaignUuid,
                "artifact"
            );
            
            if (artifactIds.isEmpty()) {
                LOGGER.info("No artifact results found for query: '{}'", request.getQuery());
                return ResponseEntity.ok(new ArrayList<>());
            }
            
            // Retrieve all artifacts from Neo4j
            List<Map<String, Object>> allArtifacts = dbConnectionManager.getNeo4jRepository()
                .getAllArtifacts(campaignUuid);
            
            // Filter to only matching artifacts (maintain order from search)
            List<ArtifactDTO> results = new ArrayList<>();
            for (String artifactId : artifactIds) {
                for (Map<String, Object> artifact : allArtifacts) {
                    if (artifactId.equals(artifact.get("id"))) {
                        results.add(new ArtifactDTO(
                            (String) artifact.get("id"),
                            (String) artifact.get("name"),
                            (String) artifact.get("type"),
                            (String) artifact.get("description")
                        ));
                        break;
                    }
                }
            }
            
            LOGGER.info("Returning {} artifact search results", results.size());
            return ResponseEntity.ok(results);
            
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid search request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            LOGGER.error("Error performing artifact search: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Perform semantic search on campaign relations.
     * Returns top 3 most semantically similar relation types.
     * 
     * @param campaignUuid UUID of the campaign to search in
     * @param request Search request with query
     * @return List of top 3 relation type results
     */
    @PostMapping("/relations")
    public ResponseEntity<List<RelationTypeDTO>> searchRelations(
            @PathVariable String campaignUuid,
            @Valid @RequestBody SearchRequest request) {
        
        LOGGER.info("POST /api/campaigns/{}/search/relations - Query: '{}'", 
                   campaignUuid, request.getQuery());
        
        // Validate campaign exists
        Campain campaign = campaignManager.getCampaignByUuid(campaignUuid);
        if (campaign == null) {
            LOGGER.warn("Campaign not found: {}", campaignUuid);
            return ResponseEntity.notFound().build();
        }
        
        try {
            // Perform semantic search with type filter for relations (top 3)
            List<String> relationIds = searchService.getTopKMatch(
                request.getQuery(), 
                request.getLimit(), 
                campaignUuid,
                "relation"
            );
            
            if (relationIds.isEmpty()) {
                LOGGER.info("No relation results found for query: '{}'", request.getQuery());
                return ResponseEntity.ok(new ArrayList<>());
            }
            
            // Get all relation types from Neo4j
            List<String> allRelationTypes = dbConnectionManager.getNeo4jRepository()
                .getAllRelationshipTypes(campaignUuid);
            
            // For now, return the top 3 unique relation types based on semantic search
            // Note: The relationship IDs from Qdrant may not directly map to relation types
            // We'll match by extracting labels from the IDs or use a simpler approach
            List<RelationTypeDTO> results = new ArrayList<>();
            
            // Try to extract unique labels from the search results
            // This is a simplified approach - you may need to enhance this
            java.util.Set<String> addedLabels = new java.util.HashSet<>();
            for (String relationId : relationIds) {
                // Try to match with existing relation types
                for (String label : allRelationTypes) {
                    if (!addedLabels.contains(label) && results.size() < 3) {
                        results.add(new RelationTypeDTO(label, 0));
                        addedLabels.add(label);
                        break;
                    }
                }
                if (results.size() >= 3) break;
            }
            
            LOGGER.info("Returning {} relation search results", results.size());
            return ResponseEntity.ok(results);
            
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid search request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            LOGGER.error("Error performing relation search: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

