package CampaignNotes.assistant;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;

import CampaignNotes.dto.AssistantResponse;
import CampaignNotes.dto.assistant.DataCollectionResult;
import CampaignNotes.dto.assistant.PlanningResult;
import CampaignNotes.llm.OpenAILLMService;
import CampaignNotes.tracking.LangfuseClient;
import CampaignNotes.tracking.otel.OTelGenerationObservation;
import CampaignNotes.tracking.otel.OTelTraceManager.OTelTrace;
import model.Campain;
import model.LLMResponse;
import model.PromptContent;

/**
 * Service for synthesizing final responses using LLM.
 * Generates natural language responses based on collected data.
 */
@Service
public class SynthesisService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SynthesisService.class);
    
    private static final String MODEL = "o3-mini";
    private static final String PROMPT_NAME = "assistant-synthesis";
    private static final int MAX_RETRIES = 2;
    
    private final OpenAILLMService llmService;
    private final LangfuseClient langfuseClient;
    private final Gson gson;
    
    public SynthesisService(OpenAILLMService llmService, LangfuseClient langfuseClient) {
        this.llmService = llmService;
        this.langfuseClient = langfuseClient;
        this.gson = new Gson();
    }
    
    /**
     * Synthesizes a final response based on collected data.
     * 
     * @param campaign the campaign context
     * @param originalQuery the original user query
     * @param plan the planning result
     * @param collectedData the data collected from various sources
     * @param trace the OpenTelemetry trace
     * @return AssistantResponse with synthesized text
     */
    public AssistantResponse synthesizeResponse(Campain campaign, String originalQuery,
                                               PlanningResult plan, DataCollectionResult collectedData,
                                               OTelTrace trace) {
        try (OTelGenerationObservation observation = 
            new OTelGenerationObservation("response-synthesis", trace.getContext())) {
            
            observation.withComponent("assistant-synthesis")
                       .withStage("response-generation");
            
            trace.addEvent("synthesis_started");
            
            // Check if we have any data to synthesize
            if (collectedData.getSourcesCount() == 0 && collectedData.getGraphData() == null) {
                LOGGER.warn("No data collected, returning empty response");
                return AssistantResponse.text(
                    "Przepraszam, nie znalazłem żadnych informacji na ten temat w kampanii.",
                    collectedData.getSources(),
                    List.of(plan.getAction())
                );
            }
            
            // Build prompt variables
            Map<String, Object> promptVariables = buildPromptVariables(
                campaign, originalQuery, plan, collectedData);
            
            // Get prompt from Langfuse
            PromptContent promptContent = langfuseClient.getPromptContentWithVariables(
                PROMPT_NAME, promptVariables);
            
            String systemPrompt;
            String inputPrompt;
            
            if (promptContent != null && promptContent.isChat()) {
                var messages = promptContent.asChatMessages();
                systemPrompt = messages.get(0).getContent();
                inputPrompt = messages.size() > 1 ? messages.get(1).getContent() : "";
            } else {
                // Fallback prompt
                LOGGER.warn("Failed to retrieve synthesis prompt, using fallback");
                systemPrompt = buildFallbackSystemPrompt(campaign);
                inputPrompt = buildFallbackInputPrompt(originalQuery);
            }
            
            observation.withModel(MODEL)
                       .withPrompt(inputPrompt);
            
            // Call LLM
            LOGGER.info("Calling LLM for response synthesis with model: {}", MODEL);
            LLMResponse response = llmService.generateWithRetry(MODEL, systemPrompt, inputPrompt, MAX_RETRIES);
            
            if (!response.isSuccessful()) {
                LOGGER.error("LLM synthesis call failed: {}", response.getErrorMessage());
                observation.setError("LLM call failed: " + response.getErrorMessage());
                
                // Return fallback response
                return AssistantResponse.text(
                    "Przepraszam, wystąpił błąd podczas generowania odpowiedzi.",
                    collectedData.getSources(),
                    List.of(plan.getAction())
                );
            }
            
            observation.withResponse(response.getContent())
                       .withTokenUsage(response.getInputTokens(), response.getOutputTokens(), response.getTokensUsed());
            
            observation.setSuccess();
            trace.addEvent("synthesis_completed");
            
            LOGGER.info("Response synthesis completed successfully");
            
            // Build final response
            if (collectedData.getGraphData() != null) {
                return AssistantResponse.textAndGraph(
                    response.getContent(),
                    collectedData.getGraphData(),
                    collectedData.getSources(),
                    List.of(plan.getAction(), "synthesis")
                );
            } else {
                return AssistantResponse.text(
                    response.getContent(),
                    collectedData.getSources(),
                    List.of(plan.getAction(), "synthesis")
                );
            }
            
        } catch (Exception e) {
            LOGGER.error("Error in response synthesis: {}", e.getMessage(), e);
            trace.recordException(e);
            
            // Return error response
            return AssistantResponse.text(
                "Przepraszam, wystąpił błąd podczas przetwarzania Twojego zapytania.",
                collectedData.getSources(),
                List.of(plan.getAction())
            );
        }
    }
    
    /**
     * Builds prompt variables for synthesis.
     */
    private Map<String, Object> buildPromptVariables(Campain campaign, String originalQuery,
                                                     PlanningResult plan, DataCollectionResult collectedData) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("originalQuery", originalQuery);
        variables.put("campaignName", campaign.getName());
        variables.put("action", plan.getAction());
        
        // Format vector results
        Map<String, Object> vectorResults = new HashMap<>();
        if (collectedData.getFoundNoteIds() != null && !collectedData.getFoundNoteIds().isEmpty()) {
            vectorResults.put("note_ids", collectedData.getFoundNoteIds());
            vectorResults.put("sources", collectedData.getSources());
        }
        if (collectedData.getFoundArtifactId() != null) {
            vectorResults.put("artifact_id", collectedData.getFoundArtifactId());
            vectorResults.put("artifact_name", collectedData.getFoundArtifactName());
        }
        variables.put("vectorResults", gson.toJson(vectorResults));
        
        // Format graph results
        if (collectedData.getGraphData() != null) {
            Map<String, Object> graphResults = new HashMap<>();
            graphResults.put("nodes_count", collectedData.getGraphData().getNodes().size());
            graphResults.put("edges_count", collectedData.getGraphData().getEdges().size());
            
            // Include brief description of nodes
            if (!collectedData.getGraphData().getNodes().isEmpty()) {
                graphResults.put("has_graph", true);
            }
            
            variables.put("graphResults", gson.toJson(graphResults));
        } else {
            variables.put("graphResults", "{}");
        }
        
        return variables;
    }
    
    /**
     * Builds a fallback system prompt.
     */
    private String buildFallbackSystemPrompt(Campain campaign) {
        return String.format(
            "Jesteś pomocnym asystentem dla kampanii RPG '%s'. " +
            "Twoim zadaniem jest stworzenie naturalnej odpowiedzi w języku polskim na podstawie zebranych danych. " +
            "Używaj inline citations w formacie [Notatka: Tytuł] dla każdej informacji. " +
            "Bądź zwięzły (max 300 słów) i pomocny.",
            campaign.getName()
        );
    }
    
    /**
     * Builds a fallback input prompt.
     */
    private String buildFallbackInputPrompt(String originalQuery) {
        return String.format(
            "Zapytanie użytkownika: %s\n\nWygeneruj pomocną odpowiedź.",
            originalQuery
        );
    }
}

