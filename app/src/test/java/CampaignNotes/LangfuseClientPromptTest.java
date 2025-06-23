package CampaignNotes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.gson.JsonObject;

/**
 * Comprehensive test suite for Enhanced Prompt Management functionality in LangfuseClient.
 * Tests the improved getPromptWithVariables method with caching, retry mechanism, 
 * versioning support, and various edge cases.
 * 
 * Uses real TestPrompt from Langfuse service for integration testing.
 */
@DisplayName("Enhanced Prompt Management Tests")
class LangfuseClientEnhancedPromptTest {
    
    private LangfuseClient client;
    private static final String TEST_PROMPT_NAME = "TestPrompt";
    private Map<String, Object> testVariables;

    @BeforeEach
    void setUp() {
        client = new LangfuseClient();
        
        // Clear cache before each test to ensure clean state
        client.clearPromptCache();
        
        // Prepare test variables for the TestPrompt
        testVariables = new HashMap<>();
        testVariables.put("VARIABLE", "Hello World from Test!");
    }

    @Nested
    @DisplayName("Basic Prompt Retrieval Tests")
    class BasicPromptRetrievalTests {
        
        @Test
        @DisplayName("Should retrieve real TestPrompt with variable interpolation")
        @Timeout(30)
        void testRealPromptWithVariableInterpolation() {
            // Test with the actual TestPrompt from Langfuse service
            String result = client.getPromptWithVariables(TEST_PROMPT_NAME, testVariables);
            
            assertAll("TestPrompt retrieval and interpolation",
                () -> assertNotNull(result, "Should retrieve TestPrompt successfully"),
                () -> assertFalse(result.trim().isEmpty(), "Prompt content should not be empty"),
                () -> assertTrue(result.contains("Hello World from Test!"), 
                    "Should contain interpolated VARIABLE value"),
                () -> assertFalse(result.contains("{{VARIABLE}}"), 
                    "Should not contain unresolved variable placeholders")
            );
            
            System.out.println("Retrieved and interpolated prompt: " + result);
        }
        
        @Test
        @DisplayName("Should use production label by default")
        @Timeout(30)
        void testDefaultProductionLabel() {
            String productionPrompt = client.getPromptWithVariables(TEST_PROMPT_NAME, testVariables);
            String explicitProduction = client.getPromptWithLabel(TEST_PROMPT_NAME, "production", testVariables);
            
            assertAll("Production label behavior",
                () -> assertNotNull(productionPrompt, "Default call should return prompt"),
                () -> assertNotNull(explicitProduction, "Explicit production call should return prompt"),
                () -> assertEquals(productionPrompt, explicitProduction, 
                    "Default and explicit production should return same content")
            );
        }
        
        @Test
        @DisplayName("Should retrieve latest version using latest label")
        @Timeout(30)
        void testLatestLabelRetrieval() {
            String latestPrompt = client.getLatestPromptWithVariables(TEST_PROMPT_NAME, testVariables);
            
            assertAll("Latest label retrieval",
                () -> assertNotNull(latestPrompt, "Should retrieve latest version successfully"),
                () -> assertFalse(latestPrompt.trim().isEmpty(), "Latest prompt should not be empty"),
                () -> assertTrue(latestPrompt.contains("Hello World from Test!"), 
                    "Latest prompt should have interpolated variables")
            );
            
            System.out.println("Retrieved latest prompt: " + latestPrompt);
        }
        
        @Test
        @DisplayName("Should retrieve specific version by number")
        @Timeout(30)
        void testVersionNumberRetrieval() {
            // Test with version 1 (assuming this exists based on screenshot)
            String versionPrompt = client.getPromptVersionWithVariables(TEST_PROMPT_NAME, 1, testVariables);
            
            assertAll("Version number retrieval",
                () -> assertNotNull(versionPrompt, "Should retrieve version 1 successfully"),
                () -> assertFalse(versionPrompt.trim().isEmpty(), "Version prompt should not be empty"),
                () -> assertTrue(versionPrompt.contains("Hello World from Test!"), 
                    "Version prompt should have interpolated variables")
            );
            
            System.out.println("Retrieved version 1 prompt: " + versionPrompt);
        }
    }

    @Nested
    @DisplayName("Cache Management Tests")
    class CacheManagementTests {
        
        @Test
        @DisplayName("Should handle cache functionality correctly")
        @Timeout(30)
        void testPromptCaching() {
            // Ensure cache is empty
            assertEquals(0, client.getPromptCacheSize(), "Cache should start empty");
            
            // First call - should fetch from API and cache
            String firstCall = client.getPromptWithVariables(TEST_PROMPT_NAME, testVariables);
            assertNotNull(firstCall, "First call should succeed");
            
            // Cache should now contain the prompt
            assertTrue(client.getPromptCacheSize() > 0, "Cache should contain prompt after first call");
            
            // Second call - should use cache (faster)
            long startTime = System.currentTimeMillis();
            String secondCall = client.getPromptWithVariables(TEST_PROMPT_NAME, testVariables);
            long duration = System.currentTimeMillis() - startTime;
            
            assertAll("Cache functionality",
                () -> assertNotNull(secondCall, "Second call should succeed"),
                () -> assertEquals(firstCall, secondCall, "Cached result should be identical"),
                () -> assertTrue(duration < 100, "Cached call should be fast (< 100ms)")
            );
        }
        
        @Test
        @DisplayName("Should bypass cache when requested")
        @Timeout(30)
        void testNoCacheRetrieval() {
            // First call with cache
            client.getPromptWithVariables(TEST_PROMPT_NAME, testVariables);
            int cacheSize = client.getPromptCacheSize();
            
            // No-cache call should not affect cache
            String noCacheResult = client.getPromptWithVariablesNoCache(TEST_PROMPT_NAME, testVariables);
            
            assertAll("No-cache functionality",
                () -> assertNotNull(noCacheResult, "No-cache call should succeed"),
                () -> assertTrue(noCacheResult.contains("Hello World from Test!"), 
                    "No-cache result should have interpolated variables"),
                () -> assertEquals(cacheSize, client.getPromptCacheSize(), 
                    "Cache size should not change with no-cache call")
            );
        }
        
        @Test
        @DisplayName("Should handle extended cache TTL")
        @Timeout(30)
        void testExtendedCacheRetrieval() {
            String extendedCachePrompt = client.getPromptWithVariablesExtendedCache(TEST_PROMPT_NAME, testVariables);
            
            assertAll("Extended cache functionality",
                () -> assertNotNull(extendedCachePrompt, "Extended cache call should succeed"),
                () -> assertTrue(extendedCachePrompt.contains("Hello World from Test!"), 
                    "Extended cache result should have interpolated variables"),
                () -> assertTrue(client.getPromptCacheSize() > 0, 
                    "Extended cache should populate cache")
            );
        }
        
        @Test
        @DisplayName("Should clear cache correctly")
        void testCacheClearFunctionality() {
            // Add something to cache
            client.getPromptWithVariables(TEST_PROMPT_NAME, testVariables);
            assertTrue(client.getPromptCacheSize() > 0, "Cache should have content");
            
            // Clear cache
            client.clearPromptCache();
            
            assertEquals(0, client.getPromptCacheSize(), "Cache should be empty after clearing");
        }
        
        @Test
        @DisplayName("Should preload prompts successfully")
        @Timeout(30)
        void testPromptPreloading() {
            // Clear cache first
            client.clearPromptCache();
            assertEquals(0, client.getPromptCacheSize(), "Cache should be empty initially");
            
            // Preload TestPrompt
            List<String> promptsToPreload = Arrays.asList(TEST_PROMPT_NAME);
            client.preloadPrompts(promptsToPreload);
            
            // Cache should now contain the preloaded prompt
            assertTrue(client.getPromptCacheSize() > 0, "Cache should contain preloaded prompt");
            
            // Retrieving should be fast now (from cache)
            long startTime = System.currentTimeMillis();
            String cachedResult = client.getPromptWithVariables(TEST_PROMPT_NAME, testVariables);
            long duration = System.currentTimeMillis() - startTime;
            
            assertAll("Preloading functionality",
                () -> assertNotNull(cachedResult, "Preloaded prompt should be retrievable"),
                () -> assertTrue(cachedResult.contains("Hello World from Test!"), 
                    "Preloaded prompt should interpolate correctly"),
                () -> assertTrue(duration < 100, "Preloaded prompt should be retrieved quickly (< 100ms)")
            );
        }
    }

    @Nested
    @DisplayName("Variable Handling Tests")
    class VariableHandlingTests {
        
        @ParameterizedTest
        @DisplayName("Should handle various variable types correctly")
        @ValueSource(strings = {"String Value", "123", "true", "null"})
        @Timeout(30)
        void testVariableTypesInterpolation(String variableValue) {
            Map<String, Object> variables = new HashMap<>();
            
            // Convert string to appropriate type for testing
            Object actualValue;
            switch (variableValue) {
                case "123":
                    actualValue = 123;
                    break;
                case "true":
                    actualValue = true;
                    break;
                case "null":
                    actualValue = null;
                    break;
                default:
                    actualValue = variableValue;
            }
            
            variables.put("VARIABLE", actualValue);
            
            String result = client.getPromptWithVariables(TEST_PROMPT_NAME, variables);
            
            assertAll("Variable type handling",
                () -> assertNotNull(result, "Should handle " + variableValue + " type"),
                () -> assertFalse(result.trim().isEmpty(), "Result should not be empty"),
                () -> assertFalse(result.contains("{{VARIABLE}}"), 
                    "Should not contain unresolved placeholders for " + variableValue)
            );
            
            System.out.println("Variable type " + variableValue + " handled correctly: " + result);
        }
        
        @Test
        @DisplayName("Should handle multiple variables in prompt")
        @Timeout(30)
        void testMultipleVariablesHandling() {
            // Test with multiple variables (even if TestPrompt only has one)
            Map<String, Object> multipleVariables = new HashMap<>();
            multipleVariables.put("VARIABLE", "Primary Value");
            multipleVariables.put("UNUSED_VAR", "This should not cause issues");
            multipleVariables.put("ANOTHER_VAR", 42);
            
            String result = client.getPromptWithVariables(TEST_PROMPT_NAME, multipleVariables);
            
            assertAll("Multiple variables handling",
                () -> assertNotNull(result, "Should handle multiple variables"),
                () -> assertTrue(result.contains("Primary Value"), 
                    "Should interpolate the used variable"),
                () -> assertFalse(result.contains("{{VARIABLE}}"), 
                    "Should not contain the used variable placeholder")
            );
        }
        
        @Test
        @DisplayName("Should handle empty variables map gracefully")
        @Timeout(30)
        void testEmptyVariablesHandling() {
            Map<String, Object> emptyVariables = new HashMap<>();
            
            String result = client.getPromptWithVariables(TEST_PROMPT_NAME, emptyVariables);
            
            assertAll("Empty variables handling",
                () -> assertNotNull(result, "Should handle empty variables map"),
                () -> assertTrue(result.contains("{{VARIABLE}}"), 
                    "Should contain unresolved variable when no variables provided")
            );
        }
    }

    @Nested
    @DisplayName("Advanced Functionality Tests")
    class AdvancedFunctionalityTests {
        
        @Test
        @DisplayName("Should validate prompt existence correctly")
        @Timeout(30)
        void testPromptExistence() {
            assertAll("Prompt existence validation",
                () -> assertTrue(client.promptExists(TEST_PROMPT_NAME), 
                    "TestPrompt should exist"),
                () -> assertTrue(client.promptExists(TEST_PROMPT_NAME, 1, null), 
                    "TestPrompt version 1 should exist"),
                () -> assertTrue(client.promptExists(TEST_PROMPT_NAME, null, "production"), 
                    "TestPrompt with production label should exist"),
                () -> assertFalse(client.promptExists("NonExistentPrompt123"), 
                    "Non-existent prompt should return false"),
                () -> assertFalse(client.promptExists(TEST_PROMPT_NAME, 999, null), 
                    "Non-existent version should return false")
            );
        }
        
        @Test
        @DisplayName("Should retrieve raw prompt data without interpolation")
        @Timeout(30)
        void testRawPromptDataRetrieval() {
            JsonObject rawData = client.getRawPromptData(TEST_PROMPT_NAME, null, "production");
            
            assertAll("Raw prompt data retrieval",
                () -> assertNotNull(rawData, "Should retrieve raw prompt data"),
                () -> assertTrue(rawData.has("prompt"), "Raw data should have prompt field"),
                () -> assertTrue(rawData.has("type"), "Raw data should have type field"),
                () -> assertEquals("text", rawData.get("type").getAsString(), 
                    "TestPrompt should be text type"),
                () -> assertTrue(rawData.get("prompt").getAsString().contains("{{VARIABLE}}"), 
                    "Raw data should contain uninterpolated variables")
            );
            
            System.out.println("Raw prompt data retrieved: " + rawData.toString());
        }
        
        @Test
        @DisplayName("Should handle network errors gracefully with retry mechanism")
        @Timeout(45)
        void testRetryMechanism() {
            // Test with non-existent prompt to trigger potential retry scenarios
            String result = client.getPromptWithVariables("NonExistentPromptForRetryTest", testVariables);
            
            // Should handle gracefully without throwing exceptions
            assertNull(result, "Non-existent prompt should return null after retries");
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {
        
        @Test
        @DisplayName("Full workflow with real TestPrompt")
        @Timeout(60)
        void testFullPromptWorkflowIntegration() {
            // Simulate a complete workflow using the enhanced prompt functionality
            
            // Step 1: Clear cache and verify
            client.clearPromptCache();
            assertEquals(0, client.getPromptCacheSize());
            
            // Step 2: Check if TestPrompt exists
            assertTrue(client.promptExists(TEST_PROMPT_NAME), "TestPrompt must exist for integration test");
            
            // Step 3: Preload the prompt
            client.preloadPrompts(Arrays.asList(TEST_PROMPT_NAME));
            assertTrue(client.getPromptCacheSize() > 0, "Preloading should populate cache");
            
            // Step 4: Use the prompt with variables (should be fast due to cache)
            Map<String, Object> workflowVariables = new HashMap<>();
            workflowVariables.put("VARIABLE", "Integration Test Value");
            
            long startTime = System.currentTimeMillis();
            String prompt = client.getPromptWithVariables(TEST_PROMPT_NAME, workflowVariables);
            long duration = System.currentTimeMillis() - startTime;
            
            // Step 5: Verify results
            assertAll("Integration workflow validation",
                () -> assertNotNull(prompt, "Should retrieve prompt successfully"),
                () -> assertTrue(prompt.contains("Integration Test Value"), 
                    "Should contain interpolated value"),
                () -> assertTrue(duration < 100, "Should be fast due to caching"),
                () -> assertFalse(prompt.contains("{{VARIABLE}}"), 
                    "Should not contain unresolved variables")
            );
            
            // Step 6: Test different versions/labels in the same workflow
            String latestVersion = client.getLatestPromptWithVariables(TEST_PROMPT_NAME, workflowVariables);
            String productionVersion = client.getPromptWithLabel(TEST_PROMPT_NAME, "production", workflowVariables);
            
            assertAll("Version consistency in workflow",
                () -> assertNotNull(latestVersion, "Latest version should be available"),
                () -> assertNotNull(productionVersion, "Production version should be available"),
                () -> assertEquals(latestVersion, productionVersion, 
                    "Latest and production should be same for TestPrompt")
            );
            
            System.out.println("Integration test completed successfully");
            System.out.println("Final prompt: " + prompt);
            System.out.println("Cache size after workflow: " + client.getPromptCacheSize());
        }
        
        @Test
        @DisplayName("Performance test with multiple concurrent requests")
        @Timeout(60)
        void testConcurrentPromptRequests() {
            // Test concurrent access to cached prompts
            client.clearPromptCache();
            
            // Pre-load the prompt
            client.preloadPrompts(Arrays.asList(TEST_PROMPT_NAME));
            
            // Simulate multiple concurrent requests
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < 10; i++) {
                Map<String, Object> variables = new HashMap<>();
                variables.put("VARIABLE", "Concurrent Request " + i);
                
                String result = client.getPromptWithVariables(TEST_PROMPT_NAME, variables);
                assertNotNull(result, "Concurrent request " + i + " should succeed");
                assertTrue(result.contains("Concurrent Request " + i), 
                    "Should contain correct interpolated value for request " + i);
            }
            
            long totalDuration = System.currentTimeMillis() - startTime;
            
            // All requests should be fast due to caching
            assertTrue(totalDuration < 500, "10 concurrent requests should complete in < 500ms with caching");
            
            System.out.println("10 concurrent requests completed in " + totalDuration + "ms");
        }
    }
} 