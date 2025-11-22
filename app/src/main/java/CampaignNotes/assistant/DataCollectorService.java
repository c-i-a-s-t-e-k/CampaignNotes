package CampaignNotes.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import CampaignNotes.NoteService;
import CampaignNotes.database.DatabaseConnectionManager;
import CampaignNotes.dto.SourceReference;
import CampaignNotes.dto.assistant.DataCollectionResult;
import CampaignNotes.dto.assistant.PlanningResult;
import CampaignNotes.tracking.otel.OTelTraceManager.OTelTrace;
import model.Campain;
import model.Note;

/**
 * Service for collecting data from multiple sources based on planning decision.
 * Orchestrates vector search and data retrieval.
 */
@Service
public class DataCollectorService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DataCollectorService.class);
    
    private static final int DEFAULT_NOTES_LIMIT = 5;
    private static final int DEFAULT_ARTIFACTS_LIMIT = 3;
    
    private final VectorSearchService vectorSearchService;
    private final NoteService noteService;
    private final DatabaseConnectionManager dbConnectionManager;
    
    public DataCollectorService(VectorSearchService vectorSearchService,
                               NoteService noteService,
                               DatabaseConnectionManager dbConnectionManager) {
        this.vectorSearchService = vectorSearchService;
        this.noteService = noteService;
        this.dbConnectionManager = dbConnectionManager;
    }
    
    /**
     * Collects data from appropriate sources based on planning decision.
     * 
     * @param campaign the campaign context
     * @param query the user's query
     * @param plan the planning result
     * @param trace the OpenTelemetry trace
     * @return DataCollectionResult with collected data
     */
    public DataCollectionResult collectData(Campain campaign, String query, 
                                           PlanningResult plan, OTelTrace trace) {
        LOGGER.info("Collecting data for action: {}", plan.getAction());
        trace.addEvent("data_collection_started");
        
        DataCollectionResult result = new DataCollectionResult();
        
        try {
            switch (plan.getAction()) {
                case "search_notes":
                    collectNotes(campaign, query, result, trace);
                    break;
                    
                case "search_artifacts_then_graph":
                    collectArtifacts(campaign, query, plan, result, trace);
                    break;
                    
                case "search_relations_then_graph":
                    collectRelations(campaign, query, plan, result, trace);
                    break;
                    
                case "combined_search":
                    collectCombined(campaign, query, plan, result, trace);
                    break;
                    
                default:
                    LOGGER.warn("Unknown action type: {}, skipping data collection", plan.getAction());
                    result.setSourcesCount(0);
                    result.setSources(new ArrayList<>());
            }
            
            trace.addEvent("data_collection_completed");
            trace.setAttribute("data.sources_count", result.getSourcesCount());
            
            LOGGER.info("Data collection completed. Sources: {}", result.getSourcesCount());
            
            return result;
            
        } catch (Exception e) {
            LOGGER.error("Error collecting data: {}", e.getMessage(), e);
            trace.recordException(e);
            
            // Return empty result on error
            result.setSourcesCount(0);
            result.setSources(new ArrayList<>());
            return result;
        }
    }
    
    /**
     * Collects notes via semantic search.
     */
    private void collectNotes(Campain campaign, String query, 
                             DataCollectionResult result, OTelTrace trace) {
        trace.addEvent("vector_search_notes_started");
        
        List<String> noteIds = vectorSearchService.searchNotes(query, DEFAULT_NOTES_LIMIT, campaign.getUuid());
        result.setFoundNoteIds(noteIds);
        
        // Get full note objects to extract titles
        List<SourceReference> sources = buildSourceReferences(noteIds, campaign);
        result.setSources(sources);
        result.setSourcesCount(sources.size());
        
        trace.addEvent("vector_search_notes_completed");
        LOGGER.debug("Found {} notes", noteIds.size());
    }
    
    /**
     * Collects artifacts via semantic search.
     */
    private void collectArtifacts(Campain campaign, String query, PlanningResult plan,
                                 DataCollectionResult result, OTelTrace trace) {
        trace.addEvent("vector_search_artifacts_started");
        
        // Use custom search query if provided
        String searchQuery = query;
        if (plan.getParameters() != null && plan.getParameters().containsKey("artifact_search_query")) {
            searchQuery = plan.getParameters().get("artifact_search_query").toString();
            LOGGER.debug("Using custom artifact search query: {}", searchQuery);
        }
        
        List<Map<String, Object>> artifacts = vectorSearchService.searchArtifacts(
            searchQuery, DEFAULT_ARTIFACTS_LIMIT, campaign.getUuid());
        
        if (!artifacts.isEmpty()) {
            // Take the top result
            Map<String, Object> topArtifact = artifacts.get(0);
            result.setFoundArtifactId((String) topArtifact.get("artifact_id"));
            result.setFoundArtifactName((String) topArtifact.get("name"));
            result.setFoundArtifactType((String) topArtifact.get("artifact_type"));
            
            // Extract note IDs from artifact (if available)
            if (topArtifact.containsKey("note_ids")) {
                // Note: This would need proper parsing from Qdrant payload
                LOGGER.debug("Artifact has associated note_ids");
            }
            
            List<SourceReference> sources = new ArrayList<>();
            // We'll build sources later after graph query
            result.setSources(sources);
            result.setSourcesCount(1);
            
            LOGGER.info("Found artifact: {} (ID: {})", 
                result.getFoundArtifactName(), result.getFoundArtifactId());
        } else {
            LOGGER.warn("No artifacts found for query: {}", searchQuery);
            result.setSourcesCount(0);
            result.setSources(new ArrayList<>());
        }
        
        trace.addEvent("vector_search_artifacts_completed");
    }
    
    /**
     * Collects relations via semantic search.
     */
    private void collectRelations(Campain campaign, String query, PlanningResult plan,
                                  DataCollectionResult result, OTelTrace trace) {
        trace.addEvent("vector_search_relations_started");
        
        // Use custom search query if provided
        String searchQuery = query;
        if (plan.getParameters() != null && plan.getParameters().containsKey("relation_search_query")) {
            searchQuery = plan.getParameters().get("relation_search_query").toString();
        }
        
        List<Map<String, Object>> relations = vectorSearchService.searchRelations(
            searchQuery, DEFAULT_ARTIFACTS_LIMIT, campaign.getUuid());
        
        if (!relations.isEmpty()) {
            Map<String, Object> topRelation = relations.get(0);
            result.setFoundRelationshipId((String) topRelation.get("relationship_id"));
            
            List<SourceReference> sources = new ArrayList<>();
            result.setSources(sources);
            result.setSourcesCount(1);
            
            LOGGER.info("Found relation: {}", result.getFoundRelationshipId());
        } else {
            LOGGER.warn("No relations found for query: {}", searchQuery);
            result.setSourcesCount(0);
            result.setSources(new ArrayList<>());
        }
        
        trace.addEvent("vector_search_relations_completed");
    }
    
    /**
     * Collects data from multiple sources.
     */
    private void collectCombined(Campain campaign, String query, PlanningResult plan,
                                DataCollectionResult result, OTelTrace trace) {
        trace.addEvent("combined_search_started");
        
        Map<String, Object> params = plan.getParameters();
        boolean searchNotes = params != null && Boolean.TRUE.equals(params.get("search_notes"));
        boolean searchArtifacts = params != null && Boolean.TRUE.equals(params.get("search_artifacts"));
        boolean searchRelations = params != null && Boolean.TRUE.equals(params.get("search_relations"));
        
        List<SourceReference> allSources = new ArrayList<>();
        
        if (searchNotes) {
            List<String> noteIds = vectorSearchService.searchNotes(query, DEFAULT_NOTES_LIMIT, campaign.getUuid());
            result.setFoundNoteIds(noteIds);
            allSources.addAll(buildSourceReferences(noteIds, campaign));
        }
        
        if (searchArtifacts) {
            List<Map<String, Object>> artifacts = vectorSearchService.searchArtifacts(
                query, DEFAULT_ARTIFACTS_LIMIT, campaign.getUuid());
            if (!artifacts.isEmpty()) {
                Map<String, Object> topArtifact = artifacts.get(0);
                result.setFoundArtifactId((String) topArtifact.get("artifact_id"));
                result.setFoundArtifactName((String) topArtifact.get("name"));
                result.setFoundArtifactType((String) topArtifact.get("artifact_type"));
            }
        }
        
        if (searchRelations) {
            List<Map<String, Object>> relations = vectorSearchService.searchRelations(
                query, DEFAULT_ARTIFACTS_LIMIT, campaign.getUuid());
            if (!relations.isEmpty()) {
                Map<String, Object> topRelation = relations.get(0);
                result.setFoundRelationshipId((String) topRelation.get("relationship_id"));
            }
        }
        
        result.setSources(allSources);
        result.setSourcesCount(allSources.size());
        
        trace.addEvent("combined_search_completed");
        LOGGER.info("Combined search found {} sources", allSources.size());
    }
    
    /**
     * Builds source references from note IDs.
     */
    private List<SourceReference> buildSourceReferences(List<String> noteIds, Campain campaign) {
        List<SourceReference> sources = new ArrayList<>();
        
        try {
            String collectionName = campaign.getQuadrantCollectionName();
            List<Note> notes = noteService.getNotesByIds(noteIds, collectionName);
            
            for (Note note : notes) {
                sources.add(new SourceReference(note.getId(), note.getTitle()));
            }
            
        } catch (Exception e) {
            LOGGER.error("Error building source references: {}", e.getMessage(), e);
        }
        
        return sources;
    }
}

