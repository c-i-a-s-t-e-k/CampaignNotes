package CampaignNotes.deduplication;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import CampaignNotes.dto.deduplication.ArtifactCandidate;
import CampaignNotes.dto.deduplication.RelationshipCandidate;
import CampaignNotes.llm.OpenAILLMService;
import CampaignNotes.tracking.LangfuseClient;
import CampaignNotes.tracking.otel.OTelGenerationObservation;
import CampaignNotes.tracking.otel.OTelTraceManager.OTelTrace;
import model.Artifact;
import model.Campain;
import model.DeduplicationDecision;
import model.LLMResponse;
import model.Note;
import model.PromptContent;
import model.Relationship;

/**
 * Service for Phase 2 (LLM Reasoning) of the hybrid deduplication system.
 * Uses an LLM to analyze whether potential duplicates found in Phase 1 should be merged.
 */
public class DeduplicationLLMService {
    
    private final OpenAILLMService llmService;
    private final LangfuseClient langfuseClient;
    
    private static final String DEDUP_MODEL = "gpt-3.5-turbo";
    private static final String ARTIFACT_DEDUP_PROMPT_NAME = "ArtifactDeduplicationReasoning";
    private static final String RELATIONSHIP_DEDUP_PROMPT_NAME = "RelationshipDeduplicationReasoning";
    
    /**
     * Constructor with dependency injection.
     * 
     * @param llmService the OpenAI LLM service
     * @param langfuseClient the Langfuse client for prompt management
     */
    public DeduplicationLLMService(OpenAILLMService llmService, LangfuseClient langfuseClient) {
        this.llmService = llmService;
        this.langfuseClient = langfuseClient;
    }
    
    /**
     * Analyzes whether a new artifact and a candidate are the same entity.
     * Uses LLM to reason about semantic similarity in narrative context.
     * 
     * @param newArtifact the newly extracted artifact
     * @param candidate the candidate artifact from Phase 1
     * @param sourceNote the note from which the new artifact was extracted
     * @param candidateNotes the notes associated with the candidate artifact
     * @param campaign the campaign context
     * @param trace OpenTelemetry trace for tracking
     * @return DeduplicationDecision with verdict and confidence
     */
    public DeduplicationDecision analyzeArtifactSimilarity(Artifact newArtifact, ArtifactCandidate candidate,
                                                          Note sourceNote, List<Note> candidateNotes,
                                                          Campain campaign, OTelTrace trace) {
        try (OTelGenerationObservation observation = 
            new OTelGenerationObservation("artifact-dedup-reasoning", trace.getContext())) {
            
            observation.withComponent("deduplication-phase2")
                       .withStage("artifact-analysis");
            
            // Build prompt variables
            Map<String, Object> promptVariables = new HashMap<>();
            promptVariables.put("NEW_ARTIFACT", formatArtifactForPrompt(newArtifact));
            promptVariables.put("CANDIDATE_ARTIFACT", formatArtifactForPrompt(candidate));
            promptVariables.put("NEW_ARTIFACT_NOTE_CONTEXT", sourceNote.getContent());
            promptVariables.put("CANDIDATE_ARTIFACT_NOTES_CONTEXT", formatNotesContext(candidateNotes));
            
            // Get prompt from Langfuse
            PromptContent promptContent = langfuseClient.getPromptContentWithVariables(
                ARTIFACT_DEDUP_PROMPT_NAME, promptVariables);
            
            String systemPrompt;
            String inputPrompt;
            
            if (promptContent != null && promptContent.isChat()) {
                var messages = promptContent.asChatMessages();
                systemPrompt = messages.get(0).getContent();
                inputPrompt = messages.size() > 1 ? messages.get(1).getContent() : "";
            } else {
                // Fallback to a generic prompt if Langfuse prompt is not available
                systemPrompt = createFallbackArtifactDedupPrompt();
                inputPrompt = buildArtifactDeduplicationInput(newArtifact, candidate, sourceNote);
            }
            
            observation.withModel(DEDUP_MODEL)
                       .withPrompt(promptContent != null ? promptContent.asText() : inputPrompt);
            
            // Call LLM
            LLMResponse response = llmService.generateWithRetry(DEDUP_MODEL, systemPrompt, inputPrompt, 1);
            
            if (!response.isSuccessful()) {
                System.err.println("LLM deduplication analysis failed: " + response.getErrorMessage());
                observation.setError("LLM call failed: " + response.getErrorMessage());
                return new DeduplicationDecision(false, 0, "LLM error", candidate.getArtifactId(), candidate.getName());
            }
            
            observation.withResponse(response.getContent())
                       .withTokenUsage(response.getInputTokens(), response.getOutputTokens(), response.getTokensUsed());
            
            // Parse the response
            DeduplicationDecision decision = parseDeduplicationResponse(response.getContent(), 
                                                                      candidate.getArtifactId(),
                                                                      candidate.getName());
            
            observation.setSuccess();
            trace.addEvent("artifact_dedup_analysis_completed");
            
            return decision;
            
        } catch (Exception e) {
            System.err.println("Error in artifact deduplication analysis: " + e.getMessage());
            trace.recordException(e);
            return new DeduplicationDecision(false, 0, "Exception: " + e.getMessage(), 
                                           candidate.getArtifactId(), candidate.getName());
        }
    }
    
    /**
     * Analyzes whether a new relationship and a candidate are the same.
     * Uses LLM to reason about semantic similarity in narrative context.
     * 
     * @param newRelationship the newly extracted relationship
     * @param candidate the candidate relationship from Phase 1
     * @param sourceNote the note from which the new relationship was extracted
     * @param candidateNotes the notes associated with the candidate relationship
     * @param campaign the campaign context
     * @param trace OpenTelemetry trace for tracking
     * @return DeduplicationDecision with verdict and confidence
     */
    public DeduplicationDecision analyzeRelationshipSimilarity(Relationship newRelationship, RelationshipCandidate candidate,
                                                              Note sourceNote, List<Note> candidateNotes,
                                                              Campain campaign, OTelTrace trace) {
        try (OTelGenerationObservation observation = 
            new OTelGenerationObservation("relationship-dedup-reasoning", trace.getContext())) {
            
            observation.withComponent("deduplication-phase2")
                       .withStage("relationship-analysis");
            
            // Build prompt variables
            Map<String, Object> promptVariables = new HashMap<>();
            promptVariables.put("NEW_RELATIONSHIP", formatRelationshipForPrompt(newRelationship));
            promptVariables.put("CANDIDATE_RELATIONSHIP", formatRelationshipForPrompt(candidate));
            promptVariables.put("NEW_RELATIONSHIP_NOTE_CONTEXT", sourceNote.getContent());
            promptVariables.put("CANDIDATE_RELATIONSHIP_NOTES_CONTEXT", formatNotesContext(candidateNotes));
            
            // Get prompt from Langfuse
            PromptContent promptContent = langfuseClient.getPromptContentWithVariables(
                RELATIONSHIP_DEDUP_PROMPT_NAME, promptVariables);
            
            String systemPrompt;
            String inputPrompt;
            
            if (promptContent != null && promptContent.isChat()) {
                var messages = promptContent.asChatMessages();
                systemPrompt = messages.get(0).getContent();
                inputPrompt = messages.size() > 1 ? messages.get(1).getContent() : "";
            } else {
                // Fallback to a generic prompt
                systemPrompt = createFallbackRelationshipDedupPrompt();
                inputPrompt = buildRelationshipDeduplicationInput(newRelationship, candidate, sourceNote);
            }
            
            observation.withModel(DEDUP_MODEL)
                       .withPrompt(promptContent != null ? promptContent.asText() : inputPrompt);
            
            // Call LLM
            LLMResponse response = llmService.generateWithRetry(DEDUP_MODEL, systemPrompt, inputPrompt, 1);
            
            if (!response.isSuccessful()) {
                System.err.println("LLM relationship dedup analysis failed: " + response.getErrorMessage());
                observation.setError("LLM call failed: " + response.getErrorMessage());
                return new DeduplicationDecision(false, 0, "LLM error", candidate.getRelationshipId(), 
                                               candidate.getLabel());
            }
            
            observation.withResponse(response.getContent())
                       .withTokenUsage(response.getInputTokens(), response.getOutputTokens(), response.getTokensUsed());
            
            // Parse the response
            DeduplicationDecision decision = parseDeduplicationResponse(response.getContent(),
                                                                      candidate.getRelationshipId(),
                                                                      candidate.getLabel());
            
            observation.setSuccess();
            trace.addEvent("relationship_dedup_analysis_completed");
            
            return decision;
            
        } catch (Exception e) {
            System.err.println("Error in relationship deduplication analysis: " + e.getMessage());
            trace.recordException(e);
            return new DeduplicationDecision(false, 0, "Exception: " + e.getMessage(),
                                           candidate.getRelationshipId(), candidate.getLabel());
        }
    }
    
    /**
     * Formats an artifact for inclusion in the prompt.
     */
    private String formatArtifactForPrompt(Artifact artifact) {
        return String.format("Name: %s\nType: %s\nDescription: %s",
                artifact.getName(), artifact.getType(), 
                artifact.getDescription() != null ? artifact.getDescription() : "");
    }
    
    /**
     * Formats an artifact candidate for inclusion in the prompt.
     */
    private String formatArtifactForPrompt(ArtifactCandidate candidate) {
        return String.format("Name: %s\nType: %s\nDescription: %s",
                candidate.getName(), candidate.getType(), candidate.getDescription());
    }
    
    /**
     * Formats a relationship for inclusion in the prompt.
     */
    private String formatRelationshipForPrompt(Relationship relationship) {
        return String.format("Source: %s\nTarget: %s\nLabel: %s\nDescription: %s",
                relationship.getSourceArtifactName(), relationship.getTargetArtifactName(),
                relationship.getLabel(), relationship.getDescription() != null ? relationship.getDescription() : "");
    }
    
    /**
     * Formats a relationship candidate for inclusion in the prompt.
     */
    private String formatRelationshipForPrompt(RelationshipCandidate candidate) {
        return String.format("Source: %s\nTarget: %s\nLabel: %s\nDescription: %s",
                candidate.getSourceArtifactName(), candidate.getTargetArtifactName(),
                candidate.getLabel(), candidate.getDescription());
    }
    
    /**
     * Builds the input prompt for artifact deduplication.
     */
    private String buildArtifactDeduplicationInput(Artifact newArtifact, ArtifactCandidate candidate, Note sourceNote) {
        return String.format("New Artifact:\n%s\n\nCandidate Artifact:\n%s\n\nSource Note:\n%s",
                formatArtifactForPrompt(newArtifact), formatArtifactForPrompt(candidate), sourceNote.getContent());
    }
    
    /**
     * Builds the input prompt for relationship deduplication.
     */
    private String buildRelationshipDeduplicationInput(Relationship newRelationship, RelationshipCandidate candidate, Note sourceNote) {
        return String.format("New Relationship:\n%s\n\nCandidate Relationship:\n%s\n\nSource Note:\n%s",
                formatRelationshipForPrompt(newRelationship), formatRelationshipForPrompt(candidate), sourceNote.getContent());
    }
    
    /**
     * Formats notes context for inclusion in the prompt.
     * Takes the 3 most recent notes (sorted by createdAt in descending order).
     * If no notes are provided, returns a message indicating no historical notes are available.
     * 
     * @param candidateNotes the notes to format
     * @return formatted notes context as a string
     */
    private String formatNotesContext(List<Note> candidateNotes) {
        if (candidateNotes == null || candidateNotes.isEmpty()) {
            return "No historical notes available";
        }
        
        // Sort notes by createdAt in descending order (newest first)
        List<Note> sortedNotes = candidateNotes.stream()
            .sorted((n1, n2) -> {
                LocalDateTime date1 = n1.getCreatedAt() != null ? n1.getCreatedAt() : LocalDateTime.MIN;
                LocalDateTime date2 = n2.getCreatedAt() != null ? n2.getCreatedAt() : LocalDateTime.MIN;
                return date2.compareTo(date1);
            })
            .limit(3)
            .collect(Collectors.toList());
        
        // Format notes
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sortedNotes.size(); i++) {
            Note note = sortedNotes.get(i);
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append(String.format("Note %d (created: %s):\n%s", 
                i + 1, 
                note.getCreatedAt() != null ? note.getCreatedAt().toString() : "Unknown",
                note.getContent() != null ? note.getContent() : ""));
        }
        
        return sb.toString();
    }
    
    /**
     * Creates a fallback prompt for artifact deduplication if Langfuse prompt is not available.
     */
    private String createFallbackArtifactDedupPrompt() {
        return "You are an expert at identifying whether two descriptions refer to the same entity in a narrative context. " +
               "Analyze if the new artifact and candidate artifact represent the same being/object. " +
               "Consider variations in naming, descriptions, narrative context, and historical notes about the candidate artifact. " +
               "Respond in JSON format: {\"is_same\": boolean, \"confidence\": 0-100, \"reasoning\": \"explanation\"}";
    }
    
    /**
     * Creates a fallback prompt for relationship deduplication if Langfuse prompt is not available.
     */
    private String createFallbackRelationshipDedupPrompt() {
        return "You are an expert at identifying whether two relationship descriptions refer to the same connection in a narrative context. " +
               "Analyze if the new relationship and candidate relationship represent the same connection between entities. " +
               "Consider variations in description, narrative context, and historical notes about the candidate relationship. " +
               "Respond in JSON format: {\"is_same\": boolean, \"confidence\": 0-100, \"reasoning\": \"explanation\"}";
    }
    
    /**
     * Parses the LLM response into a DeduplicationDecision.
     * Expects JSON format: {\"is_same\": boolean, \"confidence\": number, \"reasoning\": string}
     */
    private DeduplicationDecision parseDeduplicationResponse(String response, String candidateId, String candidateName) {
        try {
            // Try to extract JSON from response
            String jsonContent = extractJsonFromResponse(response);
            JsonObject jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();
            
            boolean isSame = jsonObject.has("is_same") ? jsonObject.get("is_same").getAsBoolean() : false;
            int confidence = jsonObject.has("confidence") ? jsonObject.get("confidence").getAsInt() : 0;
            String reasoning = jsonObject.has("reasoning") ? jsonObject.get("reasoning").getAsString() : "No reasoning provided";
            
            confidence = Math.max(0, Math.min(100, confidence));  // Clamp to 0-100
            
            return new DeduplicationDecision(isSame, confidence, reasoning, candidateId, candidateName);
            
        } catch (Exception e) {
            System.err.println("Error parsing deduplication response: " + e.getMessage() + "\nResponse: " + response);
            return new DeduplicationDecision(false, 0, "Failed to parse response", candidateId, candidateName);
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
}

