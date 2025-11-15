package CampaignNotes;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import CampaignNotes.tracking.LangfuseClient;
import model.PromptContent;

/**
 * Integration tests for Langfuse prompt availability.
 * Verifies that all critical prompts (NAR, ARE, ADR, RDR) are available in Langfuse
 * and can be retrieved with the exact variables used in production.
 * 
 * This test uses @Tag("integration") and should be run as part of integration test suite.
 * Tests include retry logic (2 attempts with 1 second delay between retries).
 */
@Tag("integration")
@DisplayName("Langfuse Prompt Availability Tests")
class LangfusePromptAvailabilityTest {
    
    private LangfuseClient langfuseClient;
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 1000;
    
    // Prompt names
    private static final String NAR_PROMPT_NAME = "NarrativeArtefactExtractorV2";
    private static final String ARE_PROMPT_NAME = "ArtefactRelationshipExtractor";
    private static final String ADR_PROMPT_NAME = "ArtifactDeduplicationReasoning";
    private static final String RDR_PROMPT_NAME = "RelationshipDeduplicationReasoning";
    
    @BeforeEach
    void setUp() {
        langfuseClient = new LangfuseClient();
    }
    
    /**
     * Test NAR (NarrativeArtefactExtractorV2) prompt availability
     */
    @Test
    @DisplayName("Should successfully retrieve NAR prompt (NarrativeArtefactExtractorV2) with correct variables")
    @Timeout(30)
    void testNARPromptAvailability() {
        Map<String, Object> promptVariables = new HashMap<>();
        promptVariables.put("CATEGORIES", "Test Category 1, Test Category 2");
        promptVariables.put("TEXT", "Test narrative text for extraction");
        
        PromptContent promptContent = fetchPromptWithRetry(NAR_PROMPT_NAME, promptVariables);
        
        assertNotNull(promptContent, "NAR prompt should be available in Langfuse");
        assertTrue(promptContent.isChat(), "NAR prompt should be of type CHAT");
    }
    
    /**
     * Test ARE (ArtefactRelationshipExtractor) prompt availability
     */
    @Test
    @DisplayName("Should successfully retrieve ARE prompt (ArtefactRelationshipExtractor) with correct variables")
    @Timeout(30)
    void testAREPromptAvailability() {
        Map<String, Object> promptVariables = new HashMap<>();
        promptVariables.put("TEXT", "Test text with artifacts for relationship extraction");
        
        PromptContent promptContent = fetchPromptWithRetry(ARE_PROMPT_NAME, promptVariables);
        
        assertNotNull(promptContent, "ARE prompt should be available in Langfuse");
        assertTrue(promptContent.isChat(), "ARE prompt should be of type CHAT");
    }
    
    /**
     * Test ADR (ArtifactDeduplicationReasoning) prompt availability
     */
    @Test
    @DisplayName("Should successfully retrieve ADR prompt (ArtifactDeduplicationReasoning) with correct variables")
    @Timeout(30)
    void testADRPromptAvailability() {
        Map<String, Object> promptVariables = new HashMap<>();
        promptVariables.put("NEW_ARTIFACT", "Test New Artifact");
        promptVariables.put("CANDIDATE_ARTIFACT", "Test Candidate Artifact");
        promptVariables.put("NEW_ARTIFACT_NOTE_CONTEXT", "Test new artifact context");
        promptVariables.put("CANDIDATE_ARTIFACT_NOTES_CONTEXT", "Test candidate context");
        
        PromptContent promptContent = fetchPromptWithRetry(ADR_PROMPT_NAME, promptVariables);
        
        assertNotNull(promptContent, "ADR prompt should be available in Langfuse");
        assertTrue(promptContent.isChat(), "ADR prompt should be of type CHAT");
    }
    
    /**
     * Test RDR (RelationshipDeduplicationReasoning) prompt availability
     */
    @Test
    @DisplayName("Should successfully retrieve RDR prompt (RelationshipDeduplicationReasoning) with correct variables")
    @Timeout(30)
    void testRDRPromptAvailability() {
        Map<String, Object> promptVariables = new HashMap<>();
        promptVariables.put("NEW_RELATIONSHIP", "Test New Relationship");
        promptVariables.put("CANDIDATE_RELATIONSHIP", "Test Candidate Relationship");
        promptVariables.put("NEW_RELATIONSHIP_NOTE_CONTEXT", "Test new relationship context");
        promptVariables.put("CANDIDATE_RELATIONSHIP_NOTES_CONTEXT", "Test candidate relationship context");
        
        PromptContent promptContent = fetchPromptWithRetry(RDR_PROMPT_NAME, promptVariables);
        
        assertNotNull(promptContent, "RDR prompt should be available in Langfuse");
        assertTrue(promptContent.isChat(), "RDR prompt should be of type CHAT");
    }
    
    /**
     * Comprehensive test for all prompts availability
     */
    @Test
    @DisplayName("Should have all prompts available in Langfuse")
    @Timeout(60)
    void shouldHaveAllPromptsAvailable() {
        Map<String, Boolean> promptsStatus = new HashMap<>();
        StringBuilder missingPrompts = new StringBuilder();
        
        // Test NAR
        PromptContent narPrompt = fetchPromptWithRetry(NAR_PROMPT_NAME, createNARVariables());
        promptsStatus.put("NAR (NarrativeArtefactExtractorV2)", narPrompt != null);
        if (narPrompt == null) missingPrompts.append("NAR, ");
        
        // Test ARE
        PromptContent arePrompt = fetchPromptWithRetry(ARE_PROMPT_NAME, createAREVariables());
        promptsStatus.put("ARE (ArtefactRelationshipExtractor)", arePrompt != null);
        if (arePrompt == null) missingPrompts.append("ARE, ");
        
        // Test ADR
        PromptContent adrPrompt = fetchPromptWithRetry(ADR_PROMPT_NAME, createADRVariables());
        promptsStatus.put("ADR (ArtifactDeduplicationReasoning)", adrPrompt != null);
        if (adrPrompt == null) missingPrompts.append("ADR, ");
        
        // Test RDR
        PromptContent rdrPrompt = fetchPromptWithRetry(RDR_PROMPT_NAME, createRDRVariables());
        promptsStatus.put("RDR (RelationshipDeduplicationReasoning)", rdrPrompt != null);
        if (rdrPrompt == null) missingPrompts.append("RDR, ");
        
        // Log status
        System.out.println("Prompt Availability Status:");
        promptsStatus.forEach((name, available) -> 
            System.out.println("  " + name + ": " + (available ? "AVAILABLE" : "MISSING"))
        );
        
        // Assert all prompts are available
        assertTrue(narPrompt != null && arePrompt != null && adrPrompt != null && rdrPrompt != null,
                   "All prompts must be available. Missing: " + 
                   (missingPrompts.length() > 0 ? missingPrompts.toString().trim().replaceAll(",$", "") : "none"));
    }
    
    /**
     * Helper method to fetch a prompt with retry logic.
     * Attempts to retrieve a prompt up to MAX_RETRIES times with RETRY_DELAY_MS between attempts.
     * 
     * @param promptName the name of the prompt to fetch
     * @param variables the variables to interpolate in the prompt
     * @return PromptContent if successful, or null if all retries failed
     * @throws AssertionError if all retry attempts fail
     */
    private PromptContent fetchPromptWithRetry(String promptName, Map<String, Object> variables) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                System.out.println("Attempting to fetch prompt '" + promptName + "' (attempt " + attempt + "/" + MAX_RETRIES + ")");
                
                PromptContent promptContent = langfuseClient.getPromptContentWithVariables(promptName, variables);
                
                if (promptContent != null) {
                    System.out.println("Successfully retrieved prompt '" + promptName + "' on attempt " + attempt);
                    return promptContent;
                } else {
                    System.out.println("Prompt '" + promptName + "' returned null on attempt " + attempt);
                    lastException = new Exception("Prompt content is null");
                }
                
            } catch (Exception e) {
                System.out.println("Error retrieving prompt '" + promptName + "' on attempt " + attempt + ": " + e.getMessage());
                lastException = e;
            }
            
            // Sleep before retry (except after the last attempt)
            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.err.println("Retry sleep interrupted: " + ie.getMessage());
                }
            }
        }
        
        // All retries failed
        String errorMessage = "Failed to retrieve prompt '" + promptName + "' after " + MAX_RETRIES + 
                            " attempts. Last error: " + (lastException != null ? lastException.getMessage() : "Unknown error");
        fail(errorMessage);
        return null;
    }
    
    // Helper methods to create prompt variables
    
    private Map<String, Object> createNARVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("CATEGORIES", "Test Category 1, Test Category 2");
        variables.put("TEXT", "Test narrative text for extraction");
        return variables;
    }
    
    private Map<String, Object> createAREVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("TEXT", "Test text with artifacts for relationship extraction");
        return variables;
    }
    
    private Map<String, Object> createADRVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("NEW_ARTIFACT", "Test New Artifact");
        variables.put("CANDIDATE_ARTIFACT", "Test Candidate Artifact");
        variables.put("NEW_ARTIFACT_NOTE_CONTEXT", "Test new artifact context");
        variables.put("CANDIDATE_ARTIFACT_NOTES_CONTEXT", "Test candidate context");
        return variables;
    }
    
    private Map<String, Object> createRDRVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("NEW_RELATIONSHIP", "Test New Relationship");
        variables.put("CANDIDATE_RELATIONSHIP", "Test Candidate Relationship");
        variables.put("NEW_RELATIONSHIP_NOTE_CONTEXT", "Test new relationship context");
        variables.put("CANDIDATE_RELATIONSHIP_NOTES_CONTEXT", "Test candidate relationship context");
        return variables;
    }
}

