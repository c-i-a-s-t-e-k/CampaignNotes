package CampaignNotes.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import CampaignNotes.NoteService;
import CampaignNotes.SemantickSearchService;
import CampaignNotes.database.DatabaseConnectionManager;
import CampaignNotes.dto.assistant.DataCollectionResult;
import CampaignNotes.dto.assistant.PlanningResult;
import CampaignNotes.tracking.otel.OTelTraceManager.OTelTrace;
import model.Artifact;
import model.Campain;
import model.Note;
import model.Relationship;

/**
 * Service for collecting data from multiple sources based on planning decision.
 * Orchestrates vector search and data retrieval.
 */
@Service
public class DataCollectorService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DataCollectorService.class);
    
    private static final int DEFAULT_NOTES_LIMIT = 3;
    private static final int DEFAULT_ARTIFACTS_LIMIT = 3;
    
    private final SemantickSearchService semantickSearchService;
    private final NoteService noteService;
    private final DatabaseConnectionManager dbConnectionManager;
    
    public DataCollectorService(SemantickSearchService semantickSearchService,
                               NoteService noteService,
                               DatabaseConnectionManager dbConnectionManager) {
        this.semantickSearchService = semantickSearchService;
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
                case SEARCH_NOTES:
                    collectNotes(campaign, query, result, trace);
                    break;
                    
                case SEARCH_ARTIFACTS_THEN_GRAPH:
                    collectArtifacts(campaign, query, plan, result, trace);
                    break;
                    
                case SEARCH_RELATIONS_THEN_GRAPH:
                    collectRelations(campaign, query, plan, result, trace);
                    break;
                    
                case COMBINED_SEARCH:
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
        
        List<String> noteIds = semantickSearchService.searchNotes(query, DEFAULT_NOTES_LIMIT, campaign.getUuid());
        result.setFoundNoteIds(noteIds);
        
        // Get full note objects
        List<Note> notes = buildNotes(noteIds, campaign);
        result.setNotes(notes);
        result.setSourcesCount(notes.size());
        
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
        
        List<Map<String, Object>> searchResults = semantickSearchService.searchArtifacts(
            searchQuery, DEFAULT_ARTIFACTS_LIMIT, campaign.getUuid());
        
        if (!searchResults.isEmpty()) {
            // Take the top result
            Map<String, Object> topResult = searchResults.get(0);
            Artifact artifact = buildArtifactFromPayload(topResult, campaign);
            
            result.setFoundArtifactId(artifact.getId());
            result.setFoundArtifactName(artifact.getName());
            result.setFoundArtifactType(artifact.getType());
            result.setArtifacts(List.of(artifact));
            result.setSourcesCount(1);
            
            LOGGER.info("Found artifact: {} (ID: {})", 
                artifact.getName(), artifact.getId());
        } else {
            LOGGER.warn("No artifacts found for query: {}", searchQuery);
            result.setSourcesCount(0);
            result.setArtifacts(new ArrayList<>());
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
        
        List<Map<String, Object>> searchResults = semantickSearchService.searchRelations(
            searchQuery, DEFAULT_ARTIFACTS_LIMIT, campaign.getUuid());
        
        if (!searchResults.isEmpty()) {
            Map<String, Object> topResult = searchResults.get(0);
            Relationship relationship = buildRelationshipFromPayload(topResult, campaign);
            
            result.setFoundRelationshipId(relationship.getId());
            result.setRelationships(List.of(relationship));
            result.setSourcesCount(1);
            
            LOGGER.info("Found relation: {}", relationship.getId());
        } else {
            LOGGER.warn("No relations found for query: {}", searchQuery);
            result.setSourcesCount(0);
            result.setRelationships(new ArrayList<>());
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
        
        List<Note> allNotes = new ArrayList<>();
        List<Artifact> allArtifacts = new ArrayList<>();
        List<Relationship> allRelationships = new ArrayList<>();
        int totalSourcesCount = 0;
        
        if (searchNotes) {
            List<String> noteIds = semantickSearchService.searchNotes(query, DEFAULT_NOTES_LIMIT, campaign.getUuid());
            result.setFoundNoteIds(noteIds);
            List<Note> notes = buildNotes(noteIds, campaign);
            allNotes.addAll(notes);
            totalSourcesCount += notes.size();
        }
        
        if (searchArtifacts) {
            List<Map<String, Object>> searchResults = semantickSearchService.searchArtifacts(
                query, DEFAULT_ARTIFACTS_LIMIT, campaign.getUuid());
            if (!searchResults.isEmpty()) {
                Map<String, Object> topResult = searchResults.get(0);
                Artifact artifact = buildArtifactFromPayload(topResult, campaign);
                result.setFoundArtifactId(artifact.getId());
                result.setFoundArtifactName(artifact.getName());
                result.setFoundArtifactType(artifact.getType());
                allArtifacts.add(artifact);
                totalSourcesCount++;
            }
        }
        
        if (searchRelations) {
            List<Map<String, Object>> searchResults = semantickSearchService.searchRelations(
                query, DEFAULT_ARTIFACTS_LIMIT, campaign.getUuid());
            if (!searchResults.isEmpty()) {
                Map<String, Object> topResult = searchResults.get(0);
                Relationship relationship = buildRelationshipFromPayload(topResult, campaign);
                result.setFoundRelationshipId(relationship.getId());
                allRelationships.add(relationship);
                totalSourcesCount++;
            }
        }
        
        result.setNotes(allNotes);
        result.setArtifacts(allArtifacts);
        result.setRelationships(allRelationships);
        result.setSourcesCount(totalSourcesCount);
        
        trace.addEvent("combined_search_completed");
        LOGGER.info("Combined search found {} sources", totalSourcesCount);
    }
    
    /**
     * Builds source references from note IDs.
     */
    private List<Note> buildNotes(List<String> noteIds, Campain campaign) {
        String collectionName = campaign.getQuadrantCollectionName();
        return noteService.getNotesByIds(noteIds, collectionName);
    }
    
    /**
     * Builds a full Artifact object from Qdrant search payload.
     */
    private Artifact buildArtifactFromPayload(Map<String, Object> payload, Campain campaign) {
        String artifactId = (String) payload.get("artifact_id");
        String name = (String) payload.get("name");
        String artifactType = (String) payload.get("artifact_type");
        String description = (String) payload.getOrDefault("description", "");
        
        Artifact artifact = new Artifact(name, artifactType, campaign.getUuid(), (String) null, description);
        if (artifactId != null) {
            artifact.setId(artifactId);
        }
        
        return artifact;
    }
    
    /**
     * Builds a full Relationship object from Qdrant search payload.
     */
    private Relationship buildRelationshipFromPayload(Map<String, Object> payload, Campain campaign) {
        String relationshipId = (String) payload.get("relationship_id");
        String sourceArtifactName = (String) payload.get("source_artifact_name");
        String targetArtifactName = (String) payload.get("target_artifact_name");
        String label = (String) payload.getOrDefault("label", "");
        String description = (String) payload.getOrDefault("description", "");
        String reasoning = (String) payload.getOrDefault("reasoning", "");
        
        Relationship relationship = new Relationship(sourceArtifactName, targetArtifactName, label, 
                                                     description, reasoning, (String) null, campaign.getUuid());
        if (relationshipId != null) {
            relationship.setId(relationshipId);
        }
        
        return relationship;
    }
}

