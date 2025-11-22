package CampaignNotes.assistant;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import CampaignNotes.CampaignManager;
import CampaignNotes.dto.AssistantResponse;
import CampaignNotes.dto.GraphDTO;
import CampaignNotes.dto.assistant.DataCollectionResult;
import CampaignNotes.dto.assistant.PlanningResult;
import CampaignNotes.tracking.otel.OTelTraceManager;
import CampaignNotes.tracking.otel.OTelTraceManager.OTelTrace;
import model.Campain;

/**
 * Main orchestrator for the Assistant Agent workflow.
 * Coordinates planning, data collection, graph queries, and response synthesis.
 */
@Service
public class AssistantOrchestrator {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AssistantOrchestrator.class);
    
    private final PlanningService planningService;
    private final DataCollectorService dataCollector;
    private final CypherGenerationService cypherGenerator;
    private final GraphQueryService graphQueryService;
    private final SynthesisService synthesisService;
    private final OTelTraceManager traceManager;
    private final CampaignManager campaignManager;
    
    public AssistantOrchestrator(PlanningService planningService,
                                DataCollectorService dataCollector,
                                CypherGenerationService cypherGenerator,
                                GraphQueryService graphQueryService,
                                SynthesisService synthesisService,
                                OTelTraceManager traceManager,
                                CampaignManager campaignManager) {
        this.planningService = planningService;
        this.dataCollector = dataCollector;
        this.cypherGenerator = cypherGenerator;
        this.graphQueryService = graphQueryService;
        this.synthesisService = synthesisService;
        this.traceManager = traceManager;
        this.campaignManager = campaignManager;
    }
    
    /**
     * Processes an assistant query through the full workflow.
     * 
     * @param campaignUuid the UUID of the campaign
     * @param query the user's query
     * @return AssistantResponse with the result
     */
    @Cacheable(value = "assistantQueryCache", 
               key = "#campaignUuid + ':' + #query",
               unless = "#result.responseType == 'error'")
    public AssistantResponse processQuery(String campaignUuid, String query) {
        LOGGER.info("Processing assistant query for campaign: {}", campaignUuid);
        LOGGER.debug("Query: {}", query);
        
        // Validate campaign
        Campain campaign = campaignManager.getCampaignByUuid(campaignUuid);
        if (campaign == null) {
            LOGGER.error("Campaign not found: {}", campaignUuid);
            return AssistantResponse.error(
                "campaign_not_found",
                "Kampania nie została znaleziona.",
                null
            );
        }
        
        try (OTelTrace trace = traceManager.createTrace(
            "assistant-query", campaignUuid, null, null, query)) {
            
            trace.setAttribute("query.length", query.length());
            trace.addEvent("query_received");
            
            // PHASE 1: Planning
            LOGGER.info("[PHASE 1] Starting planning phase");
            trace.addEvent("planning_started");
            
            PlanningResult plan = planningService.decideAction(campaign, query, trace);
            
            trace.setAttribute("action.decided", plan.getAction());
            trace.setAttribute("action.reasoning", plan.getReasoning());
            trace.addEvent("planning_completed");
            
            LOGGER.info("[PHASE 1] Planning completed. Action: {}", plan.getAction());
            
            // PHASE 2: Handle special cases
            if ("clarification_needed".equals(plan.getAction())) {
                LOGGER.info("[PHASE 2] Clarification needed");
                trace.setStatus(true, "Clarification requested");
                
                String message = "Twoje pytanie wymaga doprecyzowania. Czy możesz podać więcej szczegółów?";
                if (plan.getParameters() != null && plan.getParameters().containsKey("clarification_message")) {
                    message = plan.getParameters().get("clarification_message").toString();
                }
                
                return AssistantResponse.clarificationNeeded(message);
            }
            
            if ("out_of_scope".equals(plan.getAction())) {
                LOGGER.info("[PHASE 2] Query out of scope");
                trace.setStatus(true, "Out of scope");
                return AssistantResponse.outOfScope();
            }
            
            // PHASE 3: Data collection
            LOGGER.info("[PHASE 3] Starting data collection");
            trace.addEvent("data_collection_started");
            
            DataCollectionResult collectedData = dataCollector.collectData(
                campaign, query, plan, trace);
            
            trace.setAttribute("data.sources_count", collectedData.getSourcesCount());
            trace.addEvent("data_collection_completed");
            
            LOGGER.info("[PHASE 3] Data collection completed. Sources: {}", collectedData.getSourcesCount());
            
            // PHASE 4: Graph query (if needed)
            if (requiresGraphQuery(plan.getAction())) {
                LOGGER.info("[PHASE 4] Starting graph query phase");
                trace.addEvent("graph_query_phase_started");
                
                try {
                    GraphDTO graphData = executeGraphQuery(campaign, query, collectedData, plan, trace);
                    collectedData.setGraphData(graphData);
                    
                    trace.addEvent("graph_query_phase_completed");
                    LOGGER.info("[PHASE 4] Graph query completed. Nodes: {}, Edges: {}",
                            graphData.getNodes().size(), graphData.getEdges().size());
                    
                } catch (Exception e) {
                    LOGGER.error("[PHASE 4] Graph query failed: {}", e.getMessage(), e);
                    trace.addEvent("graph_query_failed");
                    trace.setAttribute("error.graph_query", e.getMessage());
                    
                    // Return error response for invalid Cypher
                    if (e instanceof GraphQueryService.InvalidCypherException) {
                        return AssistantResponse.error(
                            "invalid_cypher",
                            "Przepraszam, wystąpił błąd podczas generowania zapytania do grafu. " +
                            "Szczegóły: " + e.getMessage(),
                            Map.of("error", e.getMessage())
                        );
                    }
                    
                    // For other errors, continue without graph data
                    LOGGER.warn("[PHASE 4] Continuing without graph data");
                }
            } else {
                LOGGER.debug("[PHASE 4] Graph query not required for action: {}", plan.getAction());
            }
            
            // PHASE 5: Synthesis
            LOGGER.info("[PHASE 5] Starting response synthesis");
            trace.addEvent("synthesis_started");
            
            AssistantResponse response = synthesisService.synthesizeResponse(
                campaign, query, plan, collectedData, trace);
            
            trace.addEvent("synthesis_completed");
            trace.setAttribute("response.type", response.getResponseType());
            trace.setAttribute("response.has_graph", String.valueOf(response.getGraphData() != null));
            trace.setStatus(true, "Query processed successfully");
            
            LOGGER.info("[PHASE 5] Synthesis completed. Response type: {}", response.getResponseType());
            LOGGER.info("Query processing completed successfully");
            
            return response;
            
        } catch (Exception e) {
            LOGGER.error("Error processing assistant query: {}", e.getMessage(), e);
            
            return AssistantResponse.error(
                "internal_error",
                "Przepraszam, wystąpił nieoczekiwany błąd podczas przetwarzania zapytania.",
                Map.of("error", e.getMessage())
            );
        }
    }
    
    /**
     * Checks if the action requires a graph query.
     */
    private boolean requiresGraphQuery(String action) {
        return "search_artifacts_then_graph".equals(action) ||
               "search_relations_then_graph".equals(action);
    }
    
    /**
     * Executes graph query phase with Cypher generation.
     */
    private GraphDTO executeGraphQuery(Campain campaign, String originalQuery,
                                      DataCollectionResult collectedData,
                                      PlanningResult plan, OTelTrace trace) {
        // Check if we have data to query
        if ("search_artifacts_then_graph".equals(plan.getAction())) {
            if (collectedData.getFoundArtifactId() == null) {
                LOGGER.warn("No artifact found, skipping graph query");
                return new GraphDTO(); // Empty graph
            }
        } else if ("search_relations_then_graph".equals(plan.getAction())) {
            if (collectedData.getFoundRelationshipId() == null) {
                LOGGER.warn("No relationship found, skipping graph query");
                return new GraphDTO(); // Empty graph
            }
        }
        
        // Generate Cypher query
        trace.addEvent("cypher_generation_started");
        String cypherQuery = cypherGenerator.generateCypher(
            campaign, originalQuery, collectedData, plan, trace);
        trace.addEvent("cypher_generation_completed");
        trace.setAttribute("cypher.query", cypherQuery);
        
        // Build parameters for query
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("campaignUuid", campaign.getUuid());
        
        if (collectedData.getFoundArtifactId() != null) {
            parameters.put("artifactId", collectedData.getFoundArtifactId());
        }
        if (collectedData.getFoundRelationshipId() != null) {
            parameters.put("relationshipId", collectedData.getFoundRelationshipId());
        }
        
        // Execute query
        trace.addEvent("neo4j_query_execution_started");
        GraphDTO graphData = graphQueryService.executeReadOnlyQuery(cypherQuery, parameters);
        trace.addEvent("neo4j_query_execution_completed");
        trace.setAttribute("graph.nodes_count", graphData.getNodes().size());
        trace.setAttribute("graph.edges_count", graphData.getEdges().size());
        
        return graphData;
    }
}

