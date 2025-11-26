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

import CampaignNotes.config.DeduplicationConfig;
import CampaignNotes.config.LLMConfig;
import CampaignNotes.database.DatabaseConnectionManager;
import CampaignNotes.database.Neo4jRepository;
import CampaignNotes.llm.LLMService;
import CampaignNotes.llm.OpenAILLMService;
import CampaignNotes.tracking.LangfuseClient;
import CampaignNotes.tracking.otel.OTelGenerationObservation;
import CampaignNotes.tracking.otel.OTelTraceManager;
import CampaignNotes.tracking.otel.OTelTraceManager.OTelTrace;
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
    
    private final LLMService llmService;
    private final LangfuseClient langfuseClient;
    private final OTelTraceManager traceManager;
    private final ArtifactCategoryService categoryService;
    private final DatabaseConnectionManager dbConnectionManager;
    private final GraphEmbeddingService graphEmbeddingService;
    private final DeduplicationConfig deduplicationConfig;
    private final LLMConfig llmConfig;
    private final Gson gson;
    
    // Timeout for the entire workflow (10 minute as per PRD)
    private static final long WORKFLOW_TIMEOUT_MS = 600000;
    
    /**
     * Constructor with OpenTelemetry tracking.
     * 
     * @deprecated Use {@link #ArtifactGraphService(LLMService, ArtifactCategoryService, DatabaseConnectionManager, GraphEmbeddingService, DeduplicationConfig, LLMConfig)} instead
     */
    @Deprecated
    public ArtifactGraphService() {
        this.llmService = new OpenAILLMService();
        this.langfuseClient = LangfuseClient.getInstance();
        this.traceManager = OTelTraceManager.getInstance();
        this.categoryService = new ArtifactCategoryService();
        this.dbConnectionManager = new DatabaseConnectionManager();
        this.graphEmbeddingService = new GraphEmbeddingService(
            new CampaignNotes.llm.OpenAIEmbeddingService(), 
            dbConnectionManager);
        io.github.cdimascio.dotenv.Dotenv dotenv = io.github.cdimascio.dotenv.Dotenv.configure().ignoreIfMissing().load();
        this.deduplicationConfig = new CampaignNotes.config.DeduplicationConfig(dotenv);
        this.llmConfig = new CampaignNotes.config.LLMConfig();
        this.gson = new Gson();
    }
    
    /**
     * Constructor for dependency injection.
     * 
     * @param llmService the LLM service to use
     * @param categoryService the artifact category service to use
     * @param dbConnectionManager the database connection manager to use
     * @param graphEmbeddingService the graph embedding service for deduplication
     * @param deduplicationConfig the deduplication configuration
     * @param llmConfig the LLM configuration containing model names
     */
    public ArtifactGraphService(LLMService llmService, 
                               ArtifactCategoryService categoryService, 
                               DatabaseConnectionManager dbConnectionManager,
                               GraphEmbeddingService graphEmbeddingService,
                               DeduplicationConfig deduplicationConfig,
                               LLMConfig llmConfig) {
        this.llmService = llmService;
        this.langfuseClient = LangfuseClient.getInstance();
        this.traceManager = OTelTraceManager.getInstance();
        this.categoryService = categoryService;
        this.dbConnectionManager = dbConnectionManager;
        this.graphEmbeddingService = graphEmbeddingService;
        this.deduplicationConfig = deduplicationConfig;
        this.llmConfig = llmConfig;
        this.gson = new Gson();
    }
    
    /**
     * Main method to process a note and extract artifacts and relationships.
     * This is the entry point for the entire artifact extraction workflow.
     * 
     * Uses OpenTelemetry for tracking:
     * - Creates a trace for the entire workflow
     * - Creates observations for NAE and ARE operations
     * - Automatically reports success/failure status
     * 
     * @param note the note to process
     * @param campaign the campaign the note belongs to
     * @return ArtifactProcessingResult with extracted artifacts and relationships
     * @deprecated Use extractArtifactsAndRelationships() and saveToNeo4j() separately for deduplication workflow
     */
    @Deprecated
    public ArtifactProcessingResult processNoteArtifacts(Note note, Campain campaign) {
        long startTime = System.currentTimeMillis();
        
        // Create trace for the entire artifact extraction workflow
        try (OTelTrace trace = traceManager.createTrace(
            "artifact-extraction",
            campaign.getUuid(),
            note.getId(),
            null, // userId
            note.toString() // input
        )) {
            trace.addEvent("artifact_processing_started");
            
            // Get categories for this campaign
            Map<String, String> categories = categoryService.getCategoriesForCampaign(campaign.getUuid());
            if (categories.isEmpty()) {
                categoryService.assignDefaultCategoriesToCampaign(campaign.getUuid());
                categories = categoryService.getCategoriesForCampaign(campaign.getUuid());
            }
            
            trace.setAttribute("workflow.type", "ai-powered-extraction");
            trace.setAttribute("categories.count", categories.size());
            
            // Check timeout
            if (System.currentTimeMillis() - startTime > WORKFLOW_TIMEOUT_MS) {
                trace.setStatus(false, "Workflow timeout before artifact extraction");
                return new ArtifactProcessingResult("Workflow timeout before artifact extraction", 
                                                   note.getId(), campaign.getUuid());
            }
            
            // Step 1: Extract artifacts using o3-mini
            List<Artifact> artifacts = extractArtifacts(note.getFullTextForEmbedding(), categories, 
                                                       note, campaign, trace);
            
            // Check timeout
            if (System.currentTimeMillis() - startTime > WORKFLOW_TIMEOUT_MS) {
                trace.setStatus(false, "Workflow timeout after artifact extraction");
                return new ArtifactProcessingResult("Workflow timeout after artifact extraction", 
                                                   note.getId(), campaign.getUuid());
            }
            
            // Step 2: Extract relationships using o3-mini (only if we have artifacts)
            List<Relationship> relationships = new ArrayList<>();
            if (!artifacts.isEmpty()) {
                relationships = extractRelationships(note.getFullTextForEmbedding(), artifacts, 
                                                    note, campaign, trace);
            }
            
            // Check timeout
            if (System.currentTimeMillis() - startTime > WORKFLOW_TIMEOUT_MS) {
                trace.setStatus(false, "Workflow timeout after relationship extraction");
                return new ArtifactProcessingResult("Workflow timeout after relationship extraction", 
                                                   note.getId(), campaign.getUuid());
            }
            
            // Step 3: Save to Neo4j
            boolean saveSuccess = saveToNeo4j(artifacts, relationships, campaign);
            
            long totalDuration = System.currentTimeMillis() - startTime;
            
            if (saveSuccess) {
                // Add metadata to trace
                trace.setAttribute("artifacts.count", artifacts.size());
                trace.setAttribute("relationships.count", relationships.size());
                trace.setAttribute("total.duration_ms", totalDuration);
                trace.setAttribute("save.status", "success");
                trace.setStatus(true, "Artifact extraction completed successfully");
                trace.close(gson.toJson(artifacts) + "\n" + gson.toJson(relationships));
                
                // Calculate total tokens (rough estimation)
                int totalTokens = artifacts.size() * 100 + relationships.size() * 150;
                
                return new ArtifactProcessingResult(artifacts, relationships, totalTokens, 
                                                   totalDuration, note.getId(), campaign.getUuid());
            } else {
                trace.setAttribute("save.status", "failed");
                trace.setStatus(false, "Failed to save artifacts to Neo4j");
                trace.close();
                
                return new ArtifactProcessingResult("Failed to save artifacts to Neo4j", 
                                                   note.getId(), campaign.getUuid());
            }
            
        } catch (Exception e) {
            String errorMessage = "Error processing artifacts: " + e.getMessage();
            System.err.println(errorMessage);
            e.printStackTrace();
            
            return new ArtifactProcessingResult(errorMessage, note.getId(), campaign.getUuid());
        }
    }
    
    /**
     * Extracts artifacts and relationships from a note without saving to Neo4j.
     * This method allows deduplication to occur before saving.
     * 
     * Uses OpenTelemetry for tracking:
     * - Creates a trace for the entire workflow
     * - Creates observations for NAE and ARE operations
     * - Automatically reports success/failure status
     * 
     * @param note the note to process
     * @param campaign the campaign the note belongs to
     * @param trace OpenTelemetry trace for tracking (optional, will create new if null)
     * @return ArtifactProcessingResult with extracted artifacts and relationships (not saved to Neo4j)
     */
    public ArtifactProcessingResult extractArtifactsAndRelationships(Note note, Campain campaign, OTelTrace trace) {
        long startTime = System.currentTimeMillis();
        boolean shouldCloseTrace = false;
        
        // Create trace if not provided
        if (trace == null) {
            trace = traceManager.createTrace(
                "artifact-extraction",
                campaign.getUuid(),
                note.getId(),
                null,
                note.toString()
            );
            shouldCloseTrace = true;
        }
        
        try {
            trace.addEvent("artifact_extraction_started");
            
            // Get categories for this campaign
            Map<String, String> categories = categoryService.getCategoriesForCampaign(campaign.getUuid());
            if (categories.isEmpty()) {
                categoryService.assignDefaultCategoriesToCampaign(campaign.getUuid());
                categories = categoryService.getCategoriesForCampaign(campaign.getUuid());
            }
            
            trace.setAttribute("workflow.type", "ai-powered-extraction");
            trace.setAttribute("categories.count", categories.size());
            
            // Check timeout
            if (System.currentTimeMillis() - startTime > WORKFLOW_TIMEOUT_MS) {
                trace.setStatus(false, "Workflow timeout before artifact extraction");
                return new ArtifactProcessingResult("Workflow timeout before artifact extraction", 
                                                   note.getId(), campaign.getUuid());
            }
            
            // Step 1: Extract artifacts using o3-mini
            List<Artifact> artifacts = extractArtifacts(note.getFullTextForEmbedding(), categories, 
                                                       note, campaign, trace);
            
            // Check timeout
            if (System.currentTimeMillis() - startTime > WORKFLOW_TIMEOUT_MS) {
                trace.setStatus(false, "Workflow timeout after artifact extraction");
                return new ArtifactProcessingResult("Workflow timeout after artifact extraction", 
                                                   note.getId(), campaign.getUuid());
            }
            
            // Step 2: Extract relationships using o3-mini (only if we have artifacts)
            List<Relationship> relationships = new ArrayList<>();
            if (!artifacts.isEmpty()) {
                relationships = extractRelationships(note.getFullTextForEmbedding(), artifacts, 
                                                    note, campaign, trace);
            }
            
            // Check timeout
            if (System.currentTimeMillis() - startTime > WORKFLOW_TIMEOUT_MS) {
                trace.setStatus(false, "Workflow timeout after relationship extraction");
                return new ArtifactProcessingResult("Workflow timeout after relationship extraction", 
                                                   note.getId(), campaign.getUuid());
            }
            
            long totalDuration = System.currentTimeMillis() - startTime;
            
            // Add metadata to trace
            trace.setAttribute("artifacts.count", artifacts.size());
            trace.setAttribute("relationships.count", relationships.size());
            trace.setAttribute("extraction.duration_ms", totalDuration);
            trace.setStatus(true, "Artifact extraction completed successfully");
            
            // Calculate total tokens (rough estimation)
            int totalTokens = artifacts.size() * 100 + relationships.size() * 150;
            
            ArtifactProcessingResult result = new ArtifactProcessingResult(artifacts, relationships, totalTokens, 
                                               totalDuration, note.getId(), campaign.getUuid());
            
            if (shouldCloseTrace) {
                trace.close(gson.toJson(artifacts) + "\n" + gson.toJson(relationships));
            }
            
            return result;
            
        } catch (Exception e) {
            String errorMessage = "Error extracting artifacts: " + e.getMessage();
            System.err.println(errorMessage);
            e.printStackTrace();
            
            if (shouldCloseTrace && trace != null) {
                trace.recordException(e);
                trace.setStatus(false, errorMessage);
                trace.close();
            }
            
            return new ArtifactProcessingResult(errorMessage, note.getId(), campaign.getUuid());
        }
    }
    
    /**
     * Extracts artifacts from note content using NAE prompt and o3-mini model.
     * 
     * @param noteContent the content to analyze
     * @param categories available categories for this campaign
     * @param note the original note
     * @param campaign the campaign
     * @param trace OpenTelemetry trace for tracking
     * @return List of extracted artifacts
     */
    private List<Artifact> extractArtifacts(String noteContent, Map<String, String> categories, 
                                           Note note, Campain campaign, OTelTrace trace) {
        String modelUsed = llmConfig.getArtifactExtractionModel();
        List<Artifact> artifacts = new ArrayList<>();
        
        // Create observation for NAE (Note Artifact Extraction)
        try (OTelGenerationObservation observation = 
            new OTelGenerationObservation("nae-generation", trace.getContext())) {
            
            observation.withComponent("nae")
                       .withStage("artifact-extraction");
            
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
            } else {
                // Fallback to a built-in prompt rather than failing the whole workflow
                systemPrompt = createFallbackNAEPrompt(categories);
                inputPrompt = createNAEInputPrompt(noteContent);
            }
            
            observation.withModel(modelUsed)
                       .withPrompt(inputPrompt);
            
            // Generate with retry
            LLMResponse response = llmService.generateWithRetry(modelUsed, systemPrompt, inputPrompt, 1);
            
            if (response.isSuccessful()) {
                observation.withResponse(response.getContent())
                           .withTokenUsage(response.getInputTokens(), response.getOutputTokens(), 
                               response.getTokensUsed());
                
                artifacts = parseArtifactsFromResponse(response.getContent(), note, campaign);
                
                observation.setSuccess();
                trace.addEvent("nae_completed");
            } else {
                System.err.println("Failed to extract artifacts: " + response.getErrorMessage());
                observation.setError("Failed to extract artifacts: " + response.getErrorMessage());
            }
            
        } catch (Exception e) {
            System.err.println("Error in artifact extraction: " + e.getMessage());
            trace.recordException(e);
        }
        
        return artifacts;
    }
    
    /**
     * Extracts relationships between artifacts using ARE prompt and o3-mini model.
     * 
     * @param noteContent the content to analyze
     * @param artifacts the previously extracted artifacts
     * @param note the original note
     * @param campaign the campaign
     * @param trace OpenTelemetry trace for tracking
     * @return List of extracted relationships
     */
    private List<Relationship> extractRelationships(String noteContent, List<Artifact> artifacts, 
                                                   Note note, Campain campaign, OTelTrace trace) {
        List<Relationship> relationships = new ArrayList<>();
        String modelUsed = llmConfig.getRelationshipExtractionModel();
        
        // Create observation for ARE (Artifact Relationship Extraction)
        try (OTelGenerationObservation observation = 
            new OTelGenerationObservation("are-generation", trace.getContext())) {
            
            observation.withComponent("are")
                       .withStage("relationship-extraction");
            
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
                // Fallback to a built-in prompt rather than failing the whole workflow
                systemPrompt = createFallbackAREPrompt(artifacts);
                inputPrompt = createAREInputPrompt(noteContent, artifacts);
            }

            observation.withModel(modelUsed)
                       .withPrompt(inputPrompt);

            // Generate with retry
            LLMResponse response = llmService.generateWithRetry(modelUsed, systemPrompt, inputPrompt, 1);
            
            if (response.isSuccessful()) {
                observation.withResponse(response.getContent())
                           .withTokenUsage(response.getInputTokens(), response.getOutputTokens(), 
                               response.getTokensUsed());
                
                relationships = parseRelationshipsFromResponse(response.getContent(), note, campaign);
                
                observation.setSuccess();
                trace.addEvent("are_completed");
            } else {
                System.err.println("Failed to extract relationships: " + response.getErrorMessage());
                observation.setError("Failed to extract relationships: " + response.getErrorMessage());
            }
            
        } catch (Exception e) {
            System.err.println("Error in relationship extraction: " + e.getMessage());
            trace.recordException(e);
        }
        
        return relationships;
    }
    
    /**
     * Saves artifacts and relationships to Neo4j database.
     * This method is public to allow saving after deduplication workflow.
     * 
     * @param artifacts list of artifacts to save
     * @param relationships list of relationships to save
     * @param campaign the campaign these belong to
     * @return true if saved successfully, false otherwise
     */
    public boolean saveToNeo4j(List<Artifact> artifacts, List<Relationship> relationships, Campain campaign) {
        try {
            var driver = dbConnectionManager.getNeo4jRepository().getDriver();
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
                            List<String> noteIds = artifact.getNoteIds();
                            if (noteIds.isEmpty()) {
                                continue; // Skip artifacts without note IDs
                            }
                            
                            String cypher = String.format(
                                "MERGE (a:%s {name: $name, campaign_uuid: $campaign_uuid}) " +
                                "SET a.type = $type, a.description = $description, " +
                                "a.note_ids = CASE " +
                                "  WHEN a.note_ids IS NULL THEN $note_ids " +
                                "  ELSE [n IN (a.note_ids + $note_ids) WHERE NOT n IN a.note_ids | n] + a.note_ids " +
                                "END, " +
                                "a.created_at = COALESCE(a.created_at, datetime()), a.id = $id",
                                Neo4jRepository.sanitizeNeo4jLabel(campaign.getNeo4jLabel()) + "_Artifact"
                            );
                            
                            Map<String, Object> params = Map.of(
                                "name", artifact.getName(),
                                "campaign_uuid", artifact.getCampaignUuid(),
                                "type", artifact.getType(),
                                "description", artifact.getDescription() != null ? artifact.getDescription() : "",
                                "note_ids", noteIds,
                                "id", artifact.getId()
                            );
                            
                            tx.run(cypher, params);
                        }
                        
                        // Create relationships
                        for (Relationship relationship : relationships) {
                            List<String> noteIds = relationship.getNoteIds();
                            if (noteIds.isEmpty()) {
                                continue; // Skip relationships without note IDs
                            }
                            
                            String cypher = String.format(
                                "MATCH (a1:%s_Artifact {name: $source_name, campaign_uuid: $campaign_uuid}) " +
                                "MATCH (a2:%s_Artifact {name: $target_name, campaign_uuid: $campaign_uuid}) " +
                                "MERGE (a1)-[r:%s {label: $label}]->(a2) " +
                                "SET r.description = $description, r.reasoning = $reasoning, " +
                                "r.note_ids = CASE " +
                                "  WHEN r.note_ids IS NULL THEN $note_ids " +
                                "  ELSE [n IN (r.note_ids + $note_ids) WHERE NOT n IN r.note_ids | n] + r.note_ids " +
                                "END, " +
                                "r.created_at = COALESCE(r.created_at, datetime()), r.id = $id",
                                Neo4jRepository.sanitizeNeo4jLabel(campaign.getNeo4jLabel()), Neo4jRepository.sanitizeNeo4jLabel(campaign.getNeo4jLabel()), 
                                Neo4jRepository.sanitizeRelationshipType(relationship.getLabel())
                            );
                            
                            Map<String, Object> params = Map.of(
                                "source_name", relationship.getSourceArtifactName(),
                                "target_name", relationship.getTargetArtifactName(),
                                "campaign_uuid", relationship.getCampaignUuid(),
                                "label", relationship.getLabel(),
                                "description", relationship.getDescription() != null ? relationship.getDescription() : "",
                                "reasoning", relationship.getReasoning() != null ? relationship.getReasoning() : "",
                                "note_ids", noteIds,
                                "id", relationship.getId()
                            );
                            
                            tx.run(cypher, params);
                        }
                        
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
    
    /**
     * Saves artifacts and relationships to Neo4j database and their embeddings to Qdrant.
     * Overloaded version that also handles embedding storage for complete synchronization.
     * 
     * @param artifacts list of artifacts to save
     * @param relationships list of relationships to save
     * @param campaign the campaign these belong to
     * @param collectionName the Qdrant collection name for storing embeddings
     * @return true if saved successfully to Neo4j (embeddings are best-effort), false otherwise
     */
    public boolean saveToNeo4j(List<Artifact> artifacts, List<Relationship> relationships, 
                              Campain campaign, String collectionName) {
        // First, save to Neo4j
        boolean neo4jSaved = saveToNeo4j(artifacts, relationships, campaign);
        
        if (!neo4jSaved) {
            return false;
        }
        
        // Then, store embeddings to Qdrant (best-effort, don't fail if this fails)
        storeGraphEmbeddings(artifacts, relationships, collectionName);
        
        return true;
    }
    
    /**
     * Private helper method to store embeddings for artifacts and relationships in Qdrant.
     * This is a best-effort operation - failures are logged but don't fail the entire operation.
     * 
     * @param artifacts list of artifacts to store embeddings for
     * @param relationships list of relationships to store embeddings for
     * @param collectionName the Qdrant collection name
     */
    private void storeGraphEmbeddings(List<Artifact> artifacts, List<Relationship> relationships, 
                                     String collectionName) {
        try {
            if (collectionName == null || collectionName.trim().isEmpty()) {
                System.err.println("Cannot store embeddings: collection name is null or empty");
                return;
            }
            
            // Store artifact embeddings
            if (artifacts != null && !artifacts.isEmpty()) {
                int artifactCount = graphEmbeddingService.storeArtifactEmbeddingsBatch(artifacts, collectionName);
                System.out.println("Stored embeddings for " + artifactCount + " artifacts in Qdrant");
            }
            
            // Store relationship embeddings
            if (relationships != null && !relationships.isEmpty()) {
                int relationshipCount = graphEmbeddingService.storeRelationshipEmbeddingsBatch(relationships, collectionName);
                System.out.println("Stored embeddings for " + relationshipCount + " relationships in Qdrant");
            }
            
        } catch (Exception e) {
            System.err.println("Error storing graph embeddings to Qdrant: " + e.getMessage());
            // Don't throw - this is a best-effort operation
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
    
    /**
     * Merges a new artifact into an existing artifact in Neo4j and updates embeddings in Qdrant.
     * This method:
     * 1. Merges note_ids and descriptions in Neo4j
     * 2. Deletes the new artifact's embedding from Qdrant (since it's merged)
     * 3. Updates the existing artifact's embedding in Qdrant with new data
     * 
     * @param targetArtifactName the name of the existing artifact to merge into
     * @param newArtifact the new artifact to merge
     * @param campaignLabel the campaign label for Neo4j
     * @param collectionName the Qdrant collection name
     * @return true if merge was successful, false otherwise
     */
    public boolean mergeArtifacts(String targetArtifactName, Artifact newArtifact, 
                                 String campaignLabel, String collectionName) {
        try {
            var driver = dbConnectionManager.getNeo4jRepository().getDriver();
            if (driver == null) {
                System.err.println("Neo4j driver not available");
                return false;
            }
            
            try (var session = driver.session()) {
                // Perform merge in Neo4j
                String mergedArtifactId = session.executeWrite(tx -> {
                    try {
                        String sanitizedLabel = Neo4jRepository.sanitizeNeo4jLabel(campaignLabel) + "_Artifact";
                        
                        // Get existing artifact to merge note_ids
                        String getCypher = String.format(
                            "MATCH (a:%s {name: $target_name}) " +
                            "RETURN a.note_ids AS note_ids, a.id AS id",
                            sanitizedLabel
                        );
                        
                        var getCursor = tx.run(getCypher, 
                            Map.of("target_name", targetArtifactName));
                        
                        List<String> existingNoteIds = new ArrayList<>();
                        String existingId = null;
                        if (getCursor.hasNext()) {
                            var record = getCursor.next();
                            var noteIdsValue = record.get("note_ids");
                            if (!noteIdsValue.isNull()) {
                                existingNoteIds = noteIdsValue.asList(v -> v.asString());
                            }
                            existingId = record.get("id").asString();
                        }
                        
                        // Merge new note IDs
                        List<String> mergedNoteIds = new ArrayList<>(existingNoteIds);
                        for (String noteId : newArtifact.getNoteIds()) {
                            if (!mergedNoteIds.contains(noteId)) {
                                mergedNoteIds.add(noteId);
                            }
                        }
                        
                        // Update target artifact with merged note_ids and updated description
                        String updateCypher = String.format(
                            "MATCH (a:%s {name: $target_name}) " +
                            "SET a.note_ids = $note_ids, " +
                            "    a.description = CASE " +
                            "      WHEN a.description IS NULL OR a.description = '' " +
                            "      THEN $new_description " +
                            "      WHEN $new_description IS NULL OR $new_description = '' " +
                            "      THEN a.description " +
                            "      ELSE a.description + ' | ' + $new_description " +
                            "    END, " +
                            "    a.updated_at = datetime() " +
                            "RETURN a.id AS artifact_id",
                            sanitizedLabel
                        );
                        
                        var updateCursor = tx.run(updateCypher,
                            Map.of("target_name", targetArtifactName,
                                   "note_ids", mergedNoteIds,
                                   "new_description", newArtifact.getDescription() != null ? 
                                       newArtifact.getDescription() : ""));
                        
                        if (updateCursor.hasNext()) {
                            String artifactId = updateCursor.next().get("artifact_id").asString();
                            System.out.println("Successfully merged artifact " + newArtifact.getName() + 
                                             " into " + targetArtifactName + " (ID: " + artifactId + ")");
                            return artifactId;
                        }
                        
                        return null;
                        
                    } catch (Exception e) {
                        System.err.println("Error in merge transaction: " + e.getMessage());
                        return null;
                    }
                });
                
                if (mergedArtifactId == null) {
                    return false;
                }
                
                // Delete new artifact's embedding from Qdrant
                deleteArtifactEmbedding(newArtifact.getId(), collectionName);
                
                // Update existing artifact's embedding in Qdrant
                updateArtifactEmbeddingAfterMerge(mergedArtifactId, targetArtifactName, 
                                                 campaignLabel, collectionName);
                
                return true;
            }
            
        } catch (Exception e) {
            System.err.println("Error merging artifacts: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Merges a new relationship into an existing relationship in Neo4j and updates embeddings in Qdrant.
     * This method:
     * 1. Merges note_ids and descriptions in Neo4j
     * 2. Deletes the new relationship's embedding from Qdrant (since it's merged)
     * 3. Updates the existing relationship's embedding in Qdrant with new data
     * 
     * @param sourceArtifactName source artifact name for target relationship
     * @param targetArtifactName target artifact name for target relationship
     * @param relationshipLabel label of the relationship to merge into
     * @param newRelationship the new relationship to merge
     * @param campaignLabel the campaign label for Neo4j
     * @param collectionName the Qdrant collection name
     * @return true if merge was successful, false otherwise
     */
    public boolean mergeRelationships(String sourceArtifactName, String targetArtifactName,
                                     String relationshipLabel, Relationship newRelationship,
                                     String campaignLabel, String collectionName) {
        try {
            var driver = dbConnectionManager.getNeo4jRepository().getDriver();
            if (driver == null) {
                System.err.println("Neo4j driver not available");
                return false;
            }
            
            try (var session = driver.session()) {
                // Perform merge in Neo4j
                String mergedRelationshipId = session.executeWrite(tx -> {
                    try {
                        String sanitizedLabel = Neo4jRepository.sanitizeNeo4jLabel(campaignLabel) + "_Artifact";
                        String sanitizedRelType = Neo4jRepository.sanitizeRelationshipType(relationshipLabel);
                        
                        // Get existing relationship to merge note_ids
                        String getCypher = String.format(
                            "MATCH (a:%s {name: $source_name})-[r:%s]->(b:%s {name: $target_name}) " +
                            "RETURN r.note_ids AS note_ids, r.id AS id",
                            sanitizedLabel, sanitizedRelType, sanitizedLabel
                        );
                        
                        var getCursor = tx.run(getCypher,
                            Map.of("source_name", sourceArtifactName,
                                   "target_name", targetArtifactName));
                        
                        List<String> existingNoteIds = new ArrayList<>();
                        String existingId = null;
                        if (getCursor.hasNext()) {
                            var record = getCursor.next();
                            var noteIdsValue = record.get("note_ids");
                            if (!noteIdsValue.isNull()) {
                                existingNoteIds = noteIdsValue.asList(v -> v.asString());
                            }
                            existingId = record.get("id").asString();
                        }
                        
                        // Merge note IDs
                        List<String> mergedNoteIds = new ArrayList<>(existingNoteIds);
                        for (String noteId : newRelationship.getNoteIds()) {
                            if (!mergedNoteIds.contains(noteId)) {
                                mergedNoteIds.add(noteId);
                            }
                        }
                        
                        // Update relationship with merged note_ids
                        String updateCypher = String.format(
                            "MATCH (a:%s {name: $source_name})-[r:%s]->(b:%s {name: $target_name}) " +
                            "SET r.note_ids = $note_ids, " +
                            "    r.description = CASE " +
                            "      WHEN r.description IS NULL OR r.description = '' " +
                            "      THEN $new_description " +
                            "      WHEN $new_description IS NULL OR $new_description = '' " +
                            "      THEN r.description " +
                            "      ELSE r.description + ' | ' + $new_description " +
                            "    END, " +
                            "    r.updated_at = datetime() " +
                            "RETURN r.id AS relationship_id",
                            sanitizedLabel, sanitizedRelType, sanitizedLabel
                        );
                        
                        var updateCursor = tx.run(updateCypher,
                            Map.of("source_name", sourceArtifactName,
                                   "target_name", targetArtifactName,
                                   "note_ids", mergedNoteIds,
                                   "new_description", newRelationship.getDescription() != null ? 
                                       newRelationship.getDescription() : ""));
                        
                        if (updateCursor.hasNext()) {
                            String relationshipId = updateCursor.next().get("relationship_id").asString();
                            System.out.println("Successfully merged relationship " + newRelationship.getLabel() + 
                                             " (ID: " + relationshipId + ")");
                            return relationshipId;
                        }
                        
                        return null;
                        
                    } catch (Exception e) {
                        System.err.println("Error in relationship merge transaction: " + e.getMessage());
                        return null;
                    }
                });
                
                if (mergedRelationshipId == null) {
                    return false;
                }
                
                // Delete new relationship's embedding from Qdrant
                deleteRelationshipEmbedding(newRelationship.getId(), collectionName);
                
                // Update existing relationship's embedding in Qdrant
                updateRelationshipEmbeddingAfterMerge(mergedRelationshipId, sourceArtifactName, 
                                                     targetArtifactName, relationshipLabel, 
                                                     campaignLabel, collectionName);
                
                return true;
            }
            
        } catch (Exception e) {
            System.err.println("Error merging relationships: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Updates an artifact's embedding in Qdrant after merge operation.
     * Fetches the updated artifact from Neo4j and generates a new embedding.
     * Delegates to GraphEmbeddingService for update operation.
     */
    private void updateArtifactEmbeddingAfterMerge(String artifactId, String artifactName, 
                                                   String campaignLabel, String collectionName) {
        try {
            var driver = dbConnectionManager.getNeo4jRepository().getDriver();
            if (driver == null) {
                System.err.println("Neo4j driver not available for embedding update");
                return;
            }
            
            try (var session = driver.session()) {
                String sanitizedLabel = Neo4jRepository.sanitizeNeo4jLabel(campaignLabel) + "_Artifact";
                
                // Fetch updated artifact from Neo4j
                String cypher = String.format(
                    "MATCH (a:%s {id: $id}) " +
                    "RETURN a.name AS name, a.type AS type, a.description AS description, " +
                    "a.campaign_uuid AS campaign_uuid, a.note_ids AS note_ids",
                    sanitizedLabel
                );
                
                var result = session.run(cypher, Map.of("id", artifactId));
                
                if (result.hasNext()) {
                    var record = result.next();
                    
                    // Reconstruct artifact for embedding generation
                    Artifact updatedArtifact = new Artifact(
                        record.get("name").asString(),
                        record.get("type").asString(),
                        record.get("campaign_uuid").asString(),
                        null // noteId not needed for embedding
                    );
                    updatedArtifact.setId(artifactId);
                    updatedArtifact.setDescription(record.get("description").asString(""));
                    
                    // Update embedding using GraphEmbeddingService
                    graphEmbeddingService.updateArtifactEmbedding(updatedArtifact, collectionName);
                    
                    System.out.println("Updated embedding for merged artifact: " + artifactName);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error updating artifact embedding after merge: " + e.getMessage());
            // Best-effort - don't fail the merge operation
        }
    }
    
    /**
     * Updates a relationship's embedding in Qdrant after merge operation.
     * Fetches the updated relationship from Neo4j and generates a new embedding.
     * Delegates to GraphEmbeddingService for update operation.
     */
    private void updateRelationshipEmbeddingAfterMerge(String relationshipId, String sourceArtifactName,
                                                      String targetArtifactName, String relationshipLabel,
                                                      String campaignLabel, String collectionName) {
        try {
            var driver = dbConnectionManager.getNeo4jRepository().getDriver();
            if (driver == null) {
                System.err.println("Neo4j driver not available for embedding update");
                return;
            }
            
            try (var session = driver.session()) {
                String sanitizedLabel = Neo4jRepository.sanitizeNeo4jLabel(campaignLabel) + "_Artifact";
                String sanitizedRelType = Neo4jRepository.sanitizeRelationshipType(relationshipLabel);
                
                // Fetch updated relationship from Neo4j
                String cypher = String.format(
                    "MATCH (a:%s {name: $source_name})-[r:%s]->(b:%s {name: $target_name}) " +
                    "WHERE r.id = $id " +
                    "RETURN r.label AS label, r.description AS description, r.reasoning AS reasoning, " +
                    "r.campaign_uuid AS campaign_uuid, r.note_ids AS note_ids",
                    sanitizedLabel, sanitizedRelType, sanitizedLabel
                );
                
                var result = session.run(cypher, 
                    Map.of("source_name", sourceArtifactName,
                           "target_name", targetArtifactName,
                           "id", relationshipId));
                
                if (result.hasNext()) {
                    var record = result.next();
                    
                    // Reconstruct relationship for embedding generation
                    Relationship updatedRelationship = new Relationship(
                        sourceArtifactName,
                        targetArtifactName,
                        record.get("label").asString(),
                        record.get("description").asString(""),
                        record.get("reasoning").asString(""),
                        new ArrayList<>(), // empty list for noteIds
                        record.get("campaign_uuid").asString()
                    );
                    updatedRelationship.setId(relationshipId);
                    
                    // Update embedding using GraphEmbeddingService
                    graphEmbeddingService.updateRelationshipEmbedding(updatedRelationship, collectionName);
                    
                    System.out.println("Updated embedding for merged relationship: " + relationshipLabel);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error updating relationship embedding after merge: " + e.getMessage());
            // Best-effort - don't fail the merge operation
        }
    }
    
    /**
     * Deletes an artifact's embedding from Qdrant.
     * Delegates to GraphEmbeddingService for consistent ID handling.
     * Used when an artifact is merged into another one.
     */
    private void deleteArtifactEmbedding(String artifactId, String collectionName) {
        try {
            graphEmbeddingService.deleteEmbedding(artifactId, collectionName);
        } catch (Exception e) {
            System.err.println("Error deleting artifact embedding: " + e.getMessage());
            // Best-effort - don't fail the merge operation
        }
    }
    
    /**
     * Deletes a relationship's embedding from Qdrant.
     * Delegates to GraphEmbeddingService for consistent ID handling.
     * Used when a relationship is merged into another one.
     */
    private void deleteRelationshipEmbedding(String relationshipId, String collectionName) {
        try {
            graphEmbeddingService.deleteEmbedding(relationshipId, collectionName);
        } catch (Exception e) {
            System.err.println("Error deleting relationship embedding: " + e.getMessage());
            // Best-effort - don't fail the merge operation
        }
    }
} 