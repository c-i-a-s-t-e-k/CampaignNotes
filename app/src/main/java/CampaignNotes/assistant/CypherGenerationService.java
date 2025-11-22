package CampaignNotes.assistant;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
 * Service for generating Cypher queries using LLM.
 * Creates read-only Neo4j queries based on user intent and found artifacts.
 */
@Service
public class CypherGenerationService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CypherGenerationService.class);
    
    private static final String MODEL = "gpt-3.5-turbo";
    private static final String PROMPT_NAME = "assistant-cypher-generation";
    private static final int MAX_RETRIES = 2;
    
    private final OpenAILLMService llmService;
    private final LangfuseClient langfuseClient;
    private final CypherValidator validator;
    
    public CypherGenerationService(OpenAILLMService llmService,
                                   LangfuseClient langfuseClient,
                                   CypherValidator validator) {
        this.llmService = llmService;
        this.langfuseClient = langfuseClient;
        this.validator = validator;
    }
    
    /**
     * Generates a Cypher query for retrieving graph data.
     * 
     * @param campaign the campaign context
     * @param originalQuery the original user query
     * @param collectedData the data collected from vector search
     * @param plan the planning result with parameters
     * @param trace the OpenTelemetry trace for tracking
     * @return the generated Cypher query
     */
    public String generateCypher(Campain campaign, String originalQuery,
                                 DataCollectionResult collectedData,
                                 PlanningResult plan, OTelTrace trace) {
        try (OTelGenerationObservation observation = 
            new OTelGenerationObservation("cypher-generation", trace.getContext())) {
            
            observation.withComponent("assistant-cypher")
                       .withStage("query-generation");
            
            trace.addEvent("cypher_generation_started");
            
            // Build prompt variables
            Map<String, Object> promptVariables = buildPromptVariables(
                campaign, originalQuery, collectedData, plan);
            
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
                LOGGER.warn("Failed to retrieve Cypher generation prompt, using fallback");
                systemPrompt = buildFallbackSystemPrompt(campaign);
                inputPrompt = buildFallbackInputPrompt(originalQuery, collectedData);
            }
            
            observation.withModel(MODEL)
                       .withPrompt(promptContent != null ? promptContent.asText() : inputPrompt);
            
            // Call LLM
            LOGGER.info("Calling LLM for Cypher generation with model: {}", MODEL);
            LLMResponse response = llmService.generateWithRetry(MODEL, systemPrompt, inputPrompt, MAX_RETRIES);
            
            if (!response.isSuccessful()) {
                LOGGER.error("LLM Cypher generation failed: {}", response.getErrorMessage());
                observation.setError("LLM call failed: " + response.getErrorMessage());
                throw new RuntimeException("Failed to generate Cypher query: " + response.getErrorMessage());
            }
            
            observation.withResponse(response.getContent())
                       .withTokenUsage(response.getInputTokens(), response.getOutputTokens(), response.getTokensUsed());
            
            // Parse the response
            String cypherQuery = parseCypherResponse(response.getContent());
            
            // Validate
            CypherValidator.ValidationResult validation = validator.validate(cypherQuery);
            if (!validation.isValid()) {
                LOGGER.error("Generated Cypher query failed validation: {}", validation.getError());
                trace.addEvent("cypher_validation_failed");
                throw new RuntimeException("Generated Cypher query is invalid: " + validation.getError());
            }
            
            observation.setSuccess();
            trace.addEvent("cypher_generation_completed");
            trace.setAttribute("cypher.query", cypherQuery);
            
            LOGGER.info("Successfully generated and validated Cypher query");
            LOGGER.debug("Generated Cypher: {}", cypherQuery);
            
            return cypherQuery;
            
        } catch (Exception e) {
            LOGGER.error("Error in Cypher generation: {}", e.getMessage(), e);
            trace.recordException(e);
            throw new RuntimeException("Cypher generation failed", e);
        }
    }
    
    /**
     * Builds prompt variables for Cypher generation.
     */
    private Map<String, Object> buildPromptVariables(Campain campaign, String originalQuery,
                                                     DataCollectionResult collectedData,
                                                     PlanningResult plan) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("originalQuery", originalQuery);
        variables.put("campaignUuid", campaign.getUuid());
        variables.put("campaignLabel", campaign.getNeo4jLabel());
        
        // Artifact data
        if (collectedData.getFoundArtifactId() != null) {
            variables.put("artifactId", collectedData.getFoundArtifactId());
            variables.put("artifactName", collectedData.getFoundArtifactName() != null ? 
                collectedData.getFoundArtifactName() : "Unknown");
            variables.put("artifactType", collectedData.getFoundArtifactType() != null ?
                collectedData.getFoundArtifactType() : "unknown");
        } else {
            variables.put("artifactId", "");
            variables.put("artifactName", "");
            variables.put("artifactType", "");
        }
        
        // Scope from planning parameters
        String scope = "relationships"; // default
        if (plan.getParameters() != null && plan.getParameters().containsKey("expected_cypher_scope")) {
            scope = plan.getParameters().get("expected_cypher_scope").toString();
        }
        variables.put("scope", scope);
        
        // Additional instructions
        String additionalInstructions = plan.getParameters().get("additionalInstructions").toString();
        variables.put("additionalInstructions", additionalInstructions);
        
        return variables;
    }
    
    /**
     * Parses the Cypher response from LLM.
     */
    private String parseCypherResponse(String response) {
        try {
            String jsonContent = extractJsonFromResponse(response);
            JsonObject jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();
            
            if (jsonObject.has("cypher_query")) {
                return jsonObject.get("cypher_query").getAsString();
            } else {
                LOGGER.warn("Response missing 'cypher_query' field");
                throw new RuntimeException("Invalid Cypher response format");
            }
            
        } catch (Exception e) {
            LOGGER.error("Error parsing Cypher response: {}", e.getMessage());
            LOGGER.debug("Response content: {}", response);
            throw new RuntimeException("Failed to parse Cypher response", e);
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
     * Builds a fallback system prompt when Langfuse prompt is unavailable.
     */
    private String buildFallbackSystemPrompt(Campain campaign) {
        return String.format(
            "You are an expert in generating Neo4j Cypher queries. " +
            "Generate a read-only query for campaign '%s' (label: %s). " +
            "ONLY use MATCH and RETURN clauses. NEVER use CREATE, DELETE, SET, MERGE. " +
            "Return JSON format: {\"reasoning\": \"...\", \"cypher_query\": \"...\"}",
            campaign.getName(), campaign.getNeo4jLabel()
        );
    }
    
    /**
     * Builds a fallback input prompt.
     */
    private String buildFallbackInputPrompt(String originalQuery, DataCollectionResult collectedData) {
        return String.format(
            "Original query: %s\nArtifact ID: %s\nGenerate a Cypher query to retrieve this artifact and its relationships.",
            originalQuery,
            collectedData.getFoundArtifactId() != null ? collectedData.getFoundArtifactId() : "unknown"
        );
    }
}

