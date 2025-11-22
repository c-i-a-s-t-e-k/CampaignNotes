package CampaignNotes.assistant;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import CampaignNotes.ArtifactCategoryService;
import CampaignNotes.dto.assistant.ActionType;
import CampaignNotes.dto.assistant.PlanningResult;
import CampaignNotes.llm.OpenAILLMService;
import CampaignNotes.tracking.LangfuseClient;
import CampaignNotes.tracking.otel.OTelGenerationObservation;
import CampaignNotes.tracking.otel.OTelTraceManager.OTelTrace;
import model.Campain;
import model.LLMResponse;
import model.PromptContent;

/**
 * Service for analyzing user queries and deciding on the best action to take.
 * Uses LLM to determine whether to search notes, artifacts, or handle special cases.
 */
@Service
public class PlanningService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(PlanningService.class);
    
    private static final String MODEL = "o3-mini";
    private static final String PROMPT_NAME = "assistant-planning-v1";
    private static final int MAX_RETRIES = 2;
    
    private final OpenAILLMService llmService;
    private final LangfuseClient langfuseClient;
    private final ArtifactCategoryService categoryService;
    private final Gson gson;
    
    public PlanningService(OpenAILLMService llmService, 
                          LangfuseClient langfuseClient,
                          ArtifactCategoryService categoryService) {
        this.llmService = llmService;
        this.langfuseClient = langfuseClient;
        this.categoryService = categoryService;
        this.gson = new Gson();
    }
    
    /**
     * Decides on the best action to take for a user query.
     * 
     * @param campaign the campaign context
     * @param query the user's query
     * @param trace the OpenTelemetry trace for tracking
     * @return PlanningResult with action and parameters
     */
    public PlanningResult decideAction(Campain campaign, String query, OTelTrace trace) {
        try (OTelGenerationObservation observation = 
            new OTelGenerationObservation("planning-decision", trace.getContext())) {
            
            observation.withComponent("assistant-planning")
                       .withStage("action-decision");
            
            trace.addEvent("planning_started");
            
            // Build prompt variables
            Map<String, Object> promptVariables = buildPromptVariables(campaign, query);
            
            // Get prompt from Langfuse
            PromptContent promptContent = langfuseClient.getPromptContentWithVariables(
                PROMPT_NAME, promptVariables);
            
            if (promptContent == null || !promptContent.isChat()) {
                LOGGER.error("Failed to retrieve planning prompt from Langfuse");
                return buildFallbackPlanningResult(query);
            }
            
            var messages = promptContent.asChatMessages();
            String systemPrompt = messages.get(0).getContent();
            String inputPrompt = messages.size() > 1 ? messages.get(1).getContent() : "";
            
            observation.withModel(MODEL)
                       .withPrompt(promptContent.asText());
            
            // Call LLM
            LOGGER.info("Calling LLM for planning decision with model: {}", MODEL);
            LLMResponse response = llmService.generateWithRetry(MODEL, systemPrompt, inputPrompt, MAX_RETRIES);
            
            if (!response.isSuccessful()) {
                LOGGER.error("LLM planning call failed: {}", response.getErrorMessage());
                observation.setError("LLM call failed: " + response.getErrorMessage());
                return buildFallbackPlanningResult(query);
            }
            
            observation.withResponse(response.getContent())
                       .withTokenUsage(response.getInputTokens(), response.getOutputTokens(), response.getTokensUsed());
            
            // Parse the response
            PlanningResult result = parsePlanningResponse(response.getContent());
            
            observation.setSuccess();
            trace.addEvent("planning_completed");
            trace.setAttribute("planning.action", result.getAction().getValue());
            trace.setAttribute("planning.reasoning", result.getReasoning());
            
            LOGGER.info("Planning decision: action={}, reasoning={}", result.getAction(), result.getReasoning());
            
            return result;
            
        } catch (Exception e) {
            LOGGER.error("Error in planning service: {}", e.getMessage(), e);
            trace.recordException(e);
            return buildFallbackPlanningResult(query);
        }
    }
    
    /**
     * Builds prompt variables for the planning prompt.
     */
    private Map<String, Object> buildPromptVariables(Campain campaign, String query) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("query", query);
        variables.put("campaignName", campaign.getName());
        variables.put("campaignDescription", campaign.getDescription() != null ? campaign.getDescription() : "");
        
        // Get categories and format as JSON
        Map<String, String> categories = categoryService.getCategoriesForCampaign(campaign.getUuid());
        variables.put("categories", gson.toJson(categories));
        
        return variables;
    }
    
    /**
     * Parses the LLM response into a PlanningResult.
     */
    private PlanningResult parsePlanningResponse(String response) {
        try {
            // Extract JSON from response
            String jsonContent = extractJsonFromResponse(response);
            JsonObject jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();
            
            PlanningResult result = new PlanningResult();
            
            // Extract action
            if (jsonObject.has("action")) {
                String actionString = jsonObject.get("action").getAsString();
                result.setAction(actionString); // Uses setAction(String) which converts to enum
            } else {
                LOGGER.warn("Planning response missing 'action' field, defaulting to search_notes");
                result.setAction(ActionType.SEARCH_NOTES);
            }
            
            // Extract reasoning
            if (jsonObject.has("reasoning")) {
                result.setReasoning(jsonObject.get("reasoning").getAsString());
            } else {
                result.setReasoning("No reasoning provided");
            }
            
            // Extract parameters
            if (jsonObject.has("parameters")) {
                JsonObject paramsJson = jsonObject.getAsJsonObject("parameters");
                Map<String, Object> parameters = gson.fromJson(paramsJson, Map.class);
                result.setParameters(parameters);
            } else {
                result.setParameters(new HashMap<>());
            }
            
            return result;
            
        } catch (Exception e) {
            LOGGER.error("Error parsing planning response: {}", e.getMessage(), e);
            LOGGER.debug("Response content: {}", response);
            return buildFallbackPlanningResult("Error parsing response");
        }
    }
    
    /**
     * Extracts JSON from response text.
     */
    private String extractJsonFromResponse(String response) {
        int jsonStart = response.indexOf('{');
        int jsonEnd = response.lastIndexOf('}') + 1;
        
        if (jsonStart != -1 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd);
        }
        
        throw new RuntimeException("No valid JSON found in response");
    }
    
    /**
     * Builds a fallback planning result when LLM call fails.
     */
    private PlanningResult buildFallbackPlanningResult(String query) {
        PlanningResult result = new PlanningResult();
        result.setAction(ActionType.SEARCH_NOTES);
        result.setReasoning("Fallback: defaulting to search_notes due to planning error");
        result.setParameters(new HashMap<>());
        
        LOGGER.warn("Using fallback planning result for query: {}", query);
        
        return result;
    }
}

