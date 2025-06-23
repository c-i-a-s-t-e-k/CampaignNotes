package CampaignNotes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import CampaignNotes.llm.OpenAILLMService;
import CampaignNotes.tracking.LangfuseClient;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import model.Artifact;
import model.ArtifactProcessingResult;
import model.Campain;
import model.LLMResponse;
import model.Note;
import model.Relationship;

/**
 * Service responsible for orchestrating the artifact extraction and graph creation workflow.
 * This is the main coordinator for AI-powered artifact identification from campaign notes.
 */
public class ArtifactGraphService {
    
    private final OpenAILLMService llmService;
    private final LangfuseClient langfuseClient;
    private final ArtifactCategoryService categoryService;
    private final DataBaseLoader dbLoader;
    private final Gson gson;
    
    // Timeout for the entire workflow (1 minute as per PRD)
    private static final long WORKFLOW_TIMEOUT_MS = 60000;
    
    /**
     * Constructor with default dependencies
     */
    public ArtifactGraphService() {
        this.llmService = new OpenAILLMService();
        this.langfuseClient = new LangfuseClient();
        this.categoryService = new ArtifactCategoryService();
        this.dbLoader = new DataBaseLoader();
        this.gson = new Gson();
    }
    
    /**
     * Constructor for dependency injection
     */
    public ArtifactGraphService(OpenAILLMService llmService, LangfuseClient langfuseClient, 
                               ArtifactCategoryService categoryService, DataBaseLoader dbLoader) {
        this.llmService = llmService;
        this.langfuseClient = langfuseClient;
        this.categoryService = categoryService;
        this.dbLoader = dbLoader;
        this.gson = new Gson();
    }
    
    /**
     * Main method to process a note and extract artifacts and relationships.
     * This is the entry point for the entire artifact extraction workflow.
     * 
     * @param note the note to process
     * @param campaign the campaign the note belongs to
     * @return ArtifactProcessingResult with extracted artifacts and relationships
     */
    public ArtifactProcessingResult processNoteArtifacts(Note note, Campain campaign) {
        long startTime = System.currentTimeMillis();
        String traceId = null;
        
        try {
            System.out.println("Starting artifact processing for note: " + note.getId());
            
            // Create Langfuse trace for the entire workflow
            traceId = langfuseClient.trackArtifactExtractionWorkflow(
                "artifact-extraction", campaign.getUuid(), note.getId());
            
            if (traceId == null) {
                System.err.println("Warning: Failed to create Langfuse trace, continuing without tracking");
            }
            
            // Get categories for this campaign
            Map<String, String> categories = categoryService.getCategoriesForCampaign(campaign.getUuid());
            if (categories.isEmpty()) {
                System.out.println("No categories configured for campaign, assigning defaults");
                categoryService.assignDefaultCategoriesToCampaign(campaign.getUuid());
                categories = categoryService.getCategoriesForCampaign(campaign.getUuid());
            }
            
            // Check timeout
            if (System.currentTimeMillis() - startTime > WORKFLOW_TIMEOUT_MS) {
                return new ArtifactProcessingResult("Workflow timeout before artifact extraction", 
                                                   note.getId(), campaign.getUuid());
            }
            
            // Step 1: Extract artifacts using o1-mini
            List<Artifact> artifacts = extractArtifacts(note.getFullTextForEmbedding(), categories, 
                                                       note, campaign, traceId);
            
            // Check timeout
            if (System.currentTimeMillis() - startTime > WORKFLOW_TIMEOUT_MS) {
                return new ArtifactProcessingResult("Workflow timeout after artifact extraction", 
                                                   note.getId(), campaign.getUuid());
            }
            
            // Step 2: Extract relationships using o1 (only if we have artifacts)
            List<Relationship> relationships = new ArrayList<>();
            if (!artifacts.isEmpty()) {
                relationships = extractRelationships(note.getFullTextForEmbedding(), artifacts, 
                                                    note, campaign, traceId);
            }
            
            // Check timeout
            if (System.currentTimeMillis() - startTime > WORKFLOW_TIMEOUT_MS) {
                return new ArtifactProcessingResult("Workflow timeout after relationship extraction", 
                                                   note.getId(), campaign.getUuid());
            }
            
            // Step 3: Save to Neo4j
            boolean saveSuccess = saveToNeo4j(artifacts, relationships, campaign);
            
            long totalDuration = System.currentTimeMillis() - startTime;
            
            if (saveSuccess) {
                System.out.println("Artifact processing completed successfully in " + totalDuration + "ms. " +
                                 "Found " + artifacts.size() + " artifacts and " + relationships.size() + " relationships.");
                
                // Calculate total tokens (rough estimation)
                int totalTokens = artifacts.size() * 100 + relationships.size() * 150; // Estimation
                
                return new ArtifactProcessingResult(artifacts, relationships, totalTokens, 
                                                   totalDuration, note.getId(), campaign.getUuid());
            } else {
                return new ArtifactProcessingResult("Failed to save artifacts to Neo4j", 
                                                   note.getId(), campaign.getUuid());
            }
            
        } catch (Exception e) {
            long totalDuration = System.currentTimeMillis() - startTime;
            String errorMessage = "Error processing artifacts: " + e.getMessage();
            System.err.println(errorMessage);
            e.printStackTrace();
            
            return new ArtifactProcessingResult(errorMessage, note.getId(), campaign.getUuid());
        }
    }
    
    /**
     * Extracts artifacts from note content using NAE prompt and o1-mini model.
     * 
     * @param noteContent the content to analyze
     * @param categories available categories for this campaign
     * @param note the original note
     * @param campaign the campaign
     * @param traceId Langfuse trace ID for tracking
     * @return List of extracted artifacts
     */
    private List<Artifact> extractArtifacts(String noteContent, Map<String, String> categories, 
                                           Note note, Campain campaign, String traceId) {
        List<Artifact> artifacts = new ArrayList<>();
        
        try {
            // Get NAE prompt from Langfuse
            Map<String, Object> promptVariables = new HashMap<>();
            promptVariables.put("note_content", noteContent);
            promptVariables.put("categories", formatCategoriesForPrompt(categories));
            
            String systemPrompt = langfuseClient.getPromptWithVariables("Narrative Artefact Extractor (NAE)", promptVariables);
            if (systemPrompt == null) {
                // Fallback prompt
                systemPrompt = createFallbackNAEPrompt(categories);
            }
            
            String inputPrompt = "Please analyze the following campaign note and extract narrative artifacts:\n\n" + noteContent;
            
            // Generate with retry
            LLMResponse response = llmService.generateWithRetry("o1-mini", systemPrompt, inputPrompt, 1);
            
            // Track in Langfuse
            if (traceId != null && response.isSuccessful()) {
                langfuseClient.trackLLMGeneration(traceId, response.getModel(), 
                                                inputPrompt, response.getContent(), 
                                                response.getTokensUsed(), response.getDurationMs());
            }
            
            if (response.isSuccessful()) {
                artifacts = parseArtifactsFromResponse(response.getContent(), note, campaign);
                System.out.println("Extracted " + artifacts.size() + " artifacts using o1-mini");
            } else {
                System.err.println("Failed to extract artifacts: " + response.getErrorMessage());
            }
            
        } catch (Exception e) {
            System.err.println("Error in artifact extraction: " + e.getMessage());
        }
        
        return artifacts;
    }
    
    /**
     * Extracts relationships between artifacts using ARE prompt and o1 model.
     * 
     * @param noteContent the content to analyze
     * @param artifacts the previously extracted artifacts
     * @param note the original note
     * @param campaign the campaign
     * @param traceId Langfuse trace ID for tracking
     * @return List of extracted relationships
     */
    private List<Relationship> extractRelationships(String noteContent, List<Artifact> artifacts, 
                                                   Note note, Campain campaign, String traceId) {
        List<Relationship> relationships = new ArrayList<>();
        
        try {
            // Get ARE prompt from Langfuse using enhanced method with production label
            Map<String, Object> promptVariables = new HashMap<>();
            promptVariables.put("note_content", noteContent);
            promptVariables.put("artifacts", formatArtifactsForPrompt(artifacts));
            
            String systemPrompt = langfuseClient.getPromptWithLabel(
                    "Artefact Relationship Extractor", "production", promptVariables);
            if (systemPrompt == null) {
                // Fallback prompt
                systemPrompt = createFallbackAREPrompt(artifacts);
                System.out.println("Using fallback ARE prompt due to API fetch failure");
            }
            
            String inputPrompt = "Please analyze relationships between the extracted artifacts in this campaign note:\n\n" + noteContent;
            
            // Generate with retry using o1 (more powerful for relationship detection)
            LLMResponse response = llmService.generateWithRetry("o1", systemPrompt, inputPrompt, 1);
            
            // Track in Langfuse
            if (traceId != null && response.isSuccessful()) {
                langfuseClient.trackLLMGeneration(traceId, response.getModel(), 
                                                inputPrompt, response.getContent(), 
                                                response.getTokensUsed(), response.getDurationMs());
            }
            
            if (response.isSuccessful()) {
                relationships = parseRelationshipsFromResponse(response.getContent(), note, campaign);
                System.out.println("Extracted " + relationships.size() + " relationships using o1");
            } else {
                System.err.println("Failed to extract relationships: " + response.getErrorMessage());
            }
            
        } catch (Exception e) {
            System.err.println("Error in relationship extraction: " + e.getMessage());
        }
        
        return relationships;
    }
    
    /**
     * Saves artifacts and relationships to Neo4j database.
     * 
     * @param artifacts list of artifacts to save
     * @param relationships list of relationships to save
     * @param campaign the campaign these belong to
     * @return true if saved successfully, false otherwise
     */
    private boolean saveToNeo4j(List<Artifact> artifacts, List<Relationship> relationships, Campain campaign) {
        try {
            var driver = dbLoader.getNeo4jDriver();
            if (driver == null) {
                System.err.println("Neo4j driver not available");
                return false;
            }
            
            try (var session = driver.session()) {
                // Start transaction
                return session.writeTransaction(tx -> {
                    try {
                        // Create artifacts as nodes
                        for (Artifact artifact : artifacts) {
                            String cypher = String.format(
                                "MERGE (a:%s {name: $name, campaign_uuid: $campaign_uuid}) " +
                                "SET a.type = $type, a.description = $description, a.note_id = $note_id, " +
                                "a.created_at = datetime(), a.id = $id",
                                campaign.getNeo4jLabel() + "_Artifact"
                            );
                            
                            Map<String, Object> params = Map.of(
                                "name", artifact.getName(),
                                "campaign_uuid", artifact.getCampaignUuid(),
                                "type", artifact.getType(),
                                "description", artifact.getDescription() != null ? artifact.getDescription() : "",
                                "note_id", artifact.getNoteId(),
                                "id", artifact.getId()
                            );
                            
                            tx.run(cypher, params);
                        }
                        
                        // Create relationships
                        for (Relationship relationship : relationships) {
                            String cypher = String.format(
                                "MATCH (a1:%s_Artifact {name: $source_name, campaign_uuid: $campaign_uuid}) " +
                                "MATCH (a2:%s_Artifact {name: $target_name, campaign_uuid: $campaign_uuid}) " +
                                "MERGE (a1)-[r:%s {label: $label}]->(a2) " +
                                "SET r.description = $description, r.reasoning = $reasoning, " +
                                "r.note_id = $note_id, r.created_at = datetime(), r.id = $id",
                                campaign.getNeo4jLabel(), campaign.getNeo4jLabel(), 
                                sanitizeRelationshipType(relationship.getLabel())
                            );
                            
                            Map<String, Object> params = Map.of(
                                "source_name", relationship.getSourceArtifactName(),
                                "target_name", relationship.getTargetArtifactName(),
                                "campaign_uuid", relationship.getCampaignUuid(),
                                "label", relationship.getLabel(),
                                "description", relationship.getDescription() != null ? relationship.getDescription() : "",
                                "reasoning", relationship.getReasoning() != null ? relationship.getReasoning() : "",
                                "note_id", relationship.getNoteId(),
                                "id", relationship.getId()
                            );
                            
                            tx.run(cypher, params);
                        }
                        
                        System.out.println("Successfully saved " + artifacts.size() + " artifacts and " + 
                                         relationships.size() + " relationships to Neo4j");
                        return true;
                        
                    } catch (Exception e) {
                        System.err.println("Error in Neo4j transaction: " + e.getMessage());
                        return false;
                    }
                });
            }
            
        } catch (Exception e) {
            System.err.println("Error saving to Neo4j: " + e.getMessage());
            return false;
        }
    }
    
    // Helper methods for prompt formatting and parsing
    
    private String formatCategoriesForPrompt(Map<String, String> categories) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : categories.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }
    
    private String formatArtifactsForPrompt(List<Artifact> artifacts) {
        StringBuilder sb = new StringBuilder();
        for (Artifact artifact : artifacts) {
            sb.append("- ").append(artifact.getName()).append(" (").append(artifact.getType()).append(")\n");
        }
        return sb.toString();
    }
    
    private String createFallbackNAEPrompt(Map<String, String> categories) {
        return "You are a narrative artifact extractor for RPG campaigns. " +
               "Extract artifacts from the note content using these categories:\n" + 
               formatCategoriesForPrompt(categories) + 
               "\nReturn results as JSON array with objects containing: name, type, description.";
    }
    
    private String createFallbackAREPrompt(List<Artifact> artifacts) {
        return "You are a relationship extractor for RPG campaign artifacts. " +
               "Find relationships between these artifacts:\n" + 
               formatArtifactsForPrompt(artifacts) + 
               "\nReturn results as JSON array with objects containing: source, target, label, description, reasoning.";
    }
    
    private List<Artifact> parseArtifactsFromResponse(String response, Note note, Campain campaign) {
        List<Artifact> artifacts = new ArrayList<>();
        
        try {
            // Try to extract JSON from the response
            String jsonContent = extractJsonFromResponse(response);
            JsonArray jsonArray = JsonParser.parseString(jsonContent).getAsJsonArray();
            
            for (JsonElement element : jsonArray) {
                JsonObject obj = element.getAsJsonObject();
                
                String name = obj.get("name").getAsString();
                String type = obj.get("type").getAsString();
                String description = obj.has("description") ? obj.get("description").getAsString() : "";
                
                Artifact artifact = new Artifact(name, type, campaign.getUuid(), note.getId(), description);
                artifacts.add(artifact);
            }
            
        } catch (Exception e) {
            System.err.println("Error parsing artifacts from response: " + e.getMessage());
            // Fallback: try to extract artifacts using simple text parsing
            artifacts = parseArtifactsWithFallback(response, note, campaign);
        }
        
        return artifacts;
    }
    
    private List<Relationship> parseRelationshipsFromResponse(String response, Note note, Campain campaign) {
        List<Relationship> relationships = new ArrayList<>();
        
        try {
            // Try to extract JSON from the response
            String jsonContent = extractJsonFromResponse(response);
            JsonArray jsonArray = JsonParser.parseString(jsonContent).getAsJsonArray();
            
            for (JsonElement element : jsonArray) {
                JsonObject obj = element.getAsJsonObject();
                
                String source = obj.get("source").getAsString();
                String target = obj.get("target").getAsString();
                String label = obj.get("label").getAsString();
                String description = obj.has("description") ? obj.get("description").getAsString() : "";
                String reasoning = obj.has("reasoning") ? obj.get("reasoning").getAsString() : "";
                
                Relationship relationship = new Relationship(source, target, label, description, 
                                                           reasoning, note.getId(), campaign.getUuid());
                relationships.add(relationship);
            }
            
        } catch (Exception e) {
            System.err.println("Error parsing relationships from response: " + e.getMessage());
        }
        
        return relationships;
    }
    
    private String extractJsonFromResponse(String response) {
        // Find JSON content in the response (handle cases where AI adds explanatory text)
        int jsonStart = response.indexOf('[');
        int jsonEnd = response.lastIndexOf(']') + 1;
        
        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd);
        }
        
        throw new RuntimeException("No valid JSON found in response");
    }
    
    private List<Artifact> parseArtifactsWithFallback(String response, Note note, Campain campaign) {
        // Simple fallback parsing - this would be enhanced based on actual AI response patterns
        List<Artifact> artifacts = new ArrayList<>();
        
        // This is a basic implementation - would need to be refined based on actual AI responses
        String[] lines = response.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith("-") || line.trim().startsWith("*")) {
                // Simple extraction logic
                String cleaned = line.replaceAll("^[-*]\\s*", "").trim();
                if (!cleaned.isEmpty() && cleaned.length() > 3) {
                    Artifact artifact = new Artifact(cleaned, "unknown", campaign.getUuid(), note.getId());
                    artifacts.add(artifact);
                }
            }
        }
        
        return artifacts;
    }
    
    private String sanitizeRelationshipType(String label) {
        // Neo4j relationship types must be valid identifiers
        return label.toUpperCase()
                   .replaceAll("[^A-Z0-9_]", "_")
                   .replaceAll("_{2,}", "_");
    }
} 