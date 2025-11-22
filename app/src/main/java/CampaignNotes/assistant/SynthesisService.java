package CampaignNotes.assistant;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
                    List.of(plan.getAction().getValue())
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
                       .withPrompt(promptContent != null ? promptContent.asText() : inputPrompt);
            
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
                    List.of(plan.getAction().getValue())
                );
            }
            
            // Parse the JSON response to extract the "response" field
            String parsedResponse = parseSynthesisResponse(response.getContent());
            
            observation.withResponse(response.getContent())
                       .withTokenUsage(response.getInputTokens(), response.getOutputTokens(), response.getTokensUsed());
            
            observation.setSuccess();
            trace.addEvent("synthesis_completed");
            
            LOGGER.info("Response synthesis completed successfully");
            
            // Build final response
            if (collectedData.getGraphData() != null) {
                return AssistantResponse.textAndGraph(
                    parsedResponse,
                    collectedData.getGraphData(),
                    collectedData.getSources(),
                    List.of(plan.getAction().getValue(), "synthesis")
                );
            } else {
                return AssistantResponse.text(
                    parsedResponse,
                    collectedData.getSources(),
                    List.of(plan.getAction().getValue(), "synthesis")
                );
            }
            
        } catch (Exception e) {
            LOGGER.error("Error in response synthesis: {}", e.getMessage(), e);
            trace.recordException(e);
            
            // Return error response
            return AssistantResponse.text(
                "Przepraszam, wystąpił błąd podczas przetwarzania Twojego zapytania.",
                collectedData.getSources(),
                List.of(plan.getAction().getValue())
            );
        }
    }
    
    /**
     * Builds prompt variables for synthesis with full content data.
     */
    private Map<String, Object> buildPromptVariables(Campain campaign, String originalQuery,
                                                     PlanningResult plan, DataCollectionResult collectedData) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("originalQuery", originalQuery);
        variables.put("campaignName", campaign.getName());
        variables.put("action", plan.getAction().getValue());
        
        // Format full note data with title and content
        List<Map<String, String>> notesData = collectedData.getNotes().stream()
            .map(note -> Map.of(
                "title", note.getTitle(),
                "content", note.getContent()
            ))
            .toList();
        variables.put("vectorResults", gson.toJson(notesData).toString());
        
        // Format full artifact data
        List<Map<String, String>> artifactsData = collectedData.getArtifacts().stream()
            .map(artifact -> Map.of(
                "name", artifact.getName(),
                "type", artifact.getType(),
                "description", artifact.getDescription() != null ? artifact.getDescription() : ""
            ))
            .toList();
        variables.put("artifacts", gson.toJson(artifactsData).toString());
        
        // Format full relationship data
        List<Map<String, String>> relationsData = collectedData.getRelationships().stream()
            .map(rel -> Map.of(
                "source", rel.getSourceArtifactName(),
                "target", rel.getTargetArtifactName(),
                "label", rel.getLabel(),
                "description", rel.getDescription() != null ? rel.getDescription() : ""
            ))
            .toList();
        variables.put("relationships", gson.toJson(relationsData).toString());
        
        // Format graph results
        if (collectedData.getGraphData() != null) {
            Map<String, Object> graphResults = new HashMap<>();
            graphResults.put("nodes_count", collectedData.getGraphData().getNodes().size());
            graphResults.put("edges_count", collectedData.getGraphData().getEdges().size());
            
            // Include brief description of nodes
            if (!collectedData.getGraphData().getNodes().isEmpty()) {
                graphResults.put("has_graph", true);
            }
            
            variables.put("graphResults", gson.toJson(graphResults).toString());
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
    
    /**
     * Parses the LLM response into the synthesis response string.
     * Extracts the "response" field from JSON format.
     * 
     * @param response the LLM response content
     * @return the extracted response string
     */
    private String parseSynthesisResponse(String response) {
        try {
            // Extract JSON from response
            String jsonContent = extractJsonFromResponse(response);
            JsonObject jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();
            
            // Extract response field
            if (jsonObject.has("response")) {
                return jsonObject.get("response").getAsString();
            } else {
                LOGGER.warn("Synthesis response missing 'response' field");
                return response;
            }
            
        } catch (Exception e) {
            LOGGER.warn("Failed to parse synthesis response as JSON: {}. Using raw response.", e.getMessage());
            return response;
        }
    }
    
    /**
     * Extracts JSON from response text.
     * Finds the first '{' and last '}' to extract JSON content.
     * 
     * @param response the response text that may contain JSON
     * @return the extracted JSON string
     * @throws RuntimeException if no valid JSON is found
     */
    private String extractJsonFromResponse(String response) {
        int jsonStart = response.indexOf('{');
        int jsonEnd = response.lastIndexOf('}') + 1;
        
        if (jsonStart != -1 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd);
        }
        
        throw new RuntimeException("No valid JSON found in response");
    }
}

