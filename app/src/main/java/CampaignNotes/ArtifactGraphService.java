package CampaignNotes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import CampaignNotes.llm.OpenAILLMService;
import CampaignNotes.tracking.LangfuseClient;
import model.Artifact;
import model.ArtifactProcessingResult;
import model.Campain;
import model.LLMResponse;
import model.Note;
import model.PromptContent;
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
    
    // LLM models used for different stages of artifact processing
    private static final String ARTIFACT_EXTRACTION_MODEL = "o3-mini";
    private static final String RELATIONSHIP_EXTRACTION_MODEL = "o3-mini";
    
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
        String modelUsed = ARTIFACT_EXTRACTION_MODEL;
        List<Artifact> artifacts = new ArrayList<>();
        
        try {
            // Get NAE prompt from Langfuse
            Map<String, Object> promptVariables = new HashMap<>();
            promptVariables.put("CATEGORIES", formatCategoriesForPrompt(categories));
            promptVariables.put("TEXT", createNAEInputPrompt(noteContent));
            
            PromptContent promptContent = langfuseClient.getPromptContentWithVariables("NarrativeArtefactExtractorV2", promptVariables);
            String systemPrompt;
            String inputPrompt;
            if (promptContent != null) {
                if (promptContent.isText()) {
                    systemPrompt = promptContent.asText();
                    inputPrompt = createNAEInputPrompt(noteContent);
                } else if (promptContent.isChat()) {
                    systemPrompt = promptContent.asChatMessages().get(0).getContent();
                    inputPrompt = promptContent.asChatMessages().get(1).getContent();
                } else {
                    throw new RuntimeException("Unsupported prompt type: " + promptContent.getType());
                }
            }else {
                throw new RuntimeException("Failed to get NAE prompt from Langfuse");
            }
            
            // Generate with retry
            LLMResponse response = llmService.generateWithRetry(modelUsed, systemPrompt, inputPrompt, 1);
            
            // Track in Langfuse
            if (traceId != null && response.isSuccessful()) {
                langfuseClient.trackLLMGeneration(traceId, response.getModel(), 
                                                inputPrompt, response.getContent(), 
                                                response.getTokensUsed(), response.getDurationMs());
            }
            
            if (response.isSuccessful()) {
//                System.out.println("\n\nFor DEBUG::" + response.getContent() + "\n\n");
                artifacts = parseArtifactsFromResponse(response.getContent(), note, campaign);
                System.out.println("Extracted " + artifacts.size() + " artifacts using " + modelUsed);
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
        String modelUsed = RELATIONSHIP_EXTRACTION_MODEL;
        
        try {
            // Get ARE prompt from Langfuse
            Map<String, Object> promptVariables = new HashMap<>();
            promptVariables.put("TEXT", createAREInputPrompt(noteContent, artifacts));
            
            PromptContent promptContent = langfuseClient.getPromptContentWithVariables("ArtefactRelationshipExtractor", promptVariables);
            String systemPrompt;
            String inputPrompt;
            if (promptContent != null) {
                if (promptContent.isText()) {
                    systemPrompt = promptContent.asText();
                    inputPrompt = createAREInputPrompt(noteContent, artifacts);
                } else if (promptContent.isChat()) {
                    systemPrompt = promptContent.asChatMessages().get(0).getContent();
                    inputPrompt = promptContent.asChatMessages().get(1).getContent();
                } else {
                    throw new RuntimeException("Unsupported prompt type: " + promptContent.getType());
                }
            } else {
                throw new RuntimeException("Failed to get ARE prompt from Langfuse");
            }

            // Generate with retry using o1 (more powerful for relationship detection)
            LLMResponse response = llmService.generateWithRetry(modelUsed, systemPrompt, inputPrompt, 1);
            
            // Track in Langfuse
            if (traceId != null && response.isSuccessful()) {
                langfuseClient.trackLLMGeneration(traceId, response.getModel(), 
                                                inputPrompt, response.getContent(), 
                                                response.getTokensUsed(), response.getDurationMs());
            }
            
            if (response.isSuccessful()) {
                relationships = parseRelationshipsFromResponse(response.getContent(), note, campaign);
                System.out.println("Extracted " + relationships.size() + " relationships using " + modelUsed);
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
                return session.executeWrite(tx -> {
                    try {
                        // Create artifacts as nodes
                        for (Artifact artifact : artifacts) {
                            String cypher = String.format(
                                "MERGE (a:%s {name: $name, campaign_uuid: $campaign_uuid}) " +
                                "SET a.type = $type, a.description = $description, a.note_id = $note_id, " +
                                "a.created_at = datetime(), a.id = $id",
                                sanitizeNeo4jLabel(campaign.getNeo4jLabel()) + "_Artifact"
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
                                sanitizeNeo4jLabel(campaign.getNeo4jLabel()), sanitizeNeo4jLabel(campaign.getNeo4jLabel()), 
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
    
    /**
     * Creates JSON input prompt for NarrativeArtefactExtractor with only 'note' field.
     * 
     * @param noteContent the content of the note to analyze
     * @return JSON string containing the note content
     */
    private String createNAEInputPrompt(String noteContent) {
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("note", noteContent);
        return gson.toJson(inputData);
    }
    
    /**
     * Creates JSON input prompt for ArtefactRelationshipExtractor with 'note' and 'artefacts' fields.
     * 
     * @param noteContent the content of the note to analyze
     * @param artifacts the list of previously extracted artifacts
     * @return JSON string containing the note content and artifacts
     */
    private String createAREInputPrompt(String noteContent, List<Artifact> artifacts) {
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("note", noteContent);
        
        // Create list of artifact objects with name and type
        List<Map<String, Object>> artifactsList = new ArrayList<>();
        for (Artifact artifact : artifacts) {
            Map<String, Object> artifactData = new HashMap<>();
            artifactData.put("name", artifact.getName());
            artifactData.put("type", artifact.getType());
            artifactsList.add(artifactData);
        }
        inputData.put("artefacts", artifactsList);
        
        return gson.toJson(inputData);
    }
    
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
    
    private String extractJsonFromResponse(String response) {
        String cleanedResponse = response;
        // Find JSON object content (handle cases where AI adds explanatory text)
        int jsonStart = cleanedResponse.indexOf('{');
        int jsonEnd = cleanedResponse.lastIndexOf('}') + 1;
        
        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            return cleanedResponse.substring(jsonStart, jsonEnd);
        }
        
        throw new RuntimeException("No valid JSON found in response");
    }
    
    private List<Artifact> parseArtifactsFromResponse(String response, Note note, Campain campaign) {
        List<Artifact> artifacts = new ArrayList<>();
        
        try {
            // Extract JSON from the response
            String jsonContent = extractJsonFromResponse(response);
            JsonObject jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();
            
            // Check if it has "artefacts" field (note the British spelling)
            JsonArray artifactsArray;
            if (jsonObject.has("artefacts")) {
                artifactsArray = jsonObject.getAsJsonArray("artefacts");
            } else {
                // Fallback: assume the entire response is an array
                artifactsArray = JsonParser.parseString(jsonContent).getAsJsonArray();
            }
            
            for (JsonElement element : artifactsArray) {
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
            // Extract JSON from the response
            String jsonContent = extractJsonFromResponse(response);
            JsonObject jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();
            
            // Check if it has "relations" field
            JsonArray relationsArray;
            if (jsonObject.has("relations")) {
                relationsArray = jsonObject.getAsJsonArray("relations");
            } else if (jsonObject.has("relationships")) {
                relationsArray = jsonObject.getAsJsonArray("relationships");
            } else {
                // Fallback: assume the entire response is an array
                relationsArray = JsonParser.parseString(jsonContent).getAsJsonArray();
            }
            
            for (JsonElement element : relationsArray) {
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
    
    /**
     * Sanitizes a string to be used as a Neo4j label.
     * Neo4j labels cannot contain spaces, hyphens, or special characters.
     * Only letters, numbers, and underscores are allowed.
     * 
     * @param input the input string to sanitize
     * @return sanitized label suitable for Neo4j
     */
    private String sanitizeNeo4jLabel(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "DefaultLabel";
        }
        
        return input.replaceAll("[^a-zA-Z0-9_]", "_")  // Replace invalid characters with underscore
                   .replaceAll("_{2,}", "_")           // Replace multiple underscores with single
                   .replaceAll("^_+|_+$", "")         // Remove leading/trailing underscores
                   .substring(0, Math.min(input.length(), 100)); // Limit length to prevent very long labels
    }
} 