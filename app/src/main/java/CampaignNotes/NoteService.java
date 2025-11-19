package CampaignNotes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

import CampaignNotes.config.DeduplicationConfig;
import CampaignNotes.database.DatabaseConnectionManager;
import CampaignNotes.deduplication.DeduplicationCoordinator;
import CampaignNotes.dto.NoteCreateResponse;
import CampaignNotes.dto.NoteProcessingStatus;
import CampaignNotes.dto.deduplication.MergeProposal;
import CampaignNotes.llm.OpenAIEmbeddingService;
import CampaignNotes.tracking.otel.OTelEmbeddingObservation;
import CampaignNotes.tracking.otel.OTelTraceManager;
import CampaignNotes.tracking.otel.OTelTraceManager.OTelTrace;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.PointId;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import model.Artifact;
import model.ArtifactProcessingResult;
import model.Campain;
import model.DeduplicationDecision;
import model.DeduplicationResult;
import model.EmbeddingResult;
import model.Note;
import model.Relationship;

/**
 * Service for managing campaign notes.
 * Handles note validation, embedding generation, and delegates storage to CampaignManager.
 */
public class NoteService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NoteService.class);
    
    private final CampaignManager campaignManager; 
    private final OpenAIEmbeddingService embeddingService;
    private final OTelTraceManager traceManager;
    private final ArtifactGraphService artifactService;
    private final DatabaseConnectionManager dbConnectionManager;
    private final DeduplicationCoordinator deduplicationCoordinator;
    private final DeduplicationSessionManager sessionManager;
    private final DeduplicationConfig deduplicationConfig;
    
    /**
     * Constructor initializes all required services with OpenTelemetry tracking.
     * 
     * @deprecated Use {@link #NoteService(CampaignManager, OpenAIEmbeddingService, ArtifactGraphService, DatabaseConnectionManager)} instead
     */
    @Deprecated
    public NoteService() {
        this.dbConnectionManager = new DatabaseConnectionManager();
        this.campaignManager = new CampaignManager(dbConnectionManager);
        this.embeddingService = new OpenAIEmbeddingService();
        this.traceManager = OTelTraceManager.getInstance();
        this.artifactService = new ArtifactGraphService();
        this.sessionManager = DeduplicationSessionManager.getInstance();
        // Note: deduplicationCoordinator will be null in deprecated constructor
        // This is intentional - old code path doesn't use deduplication
        this.deduplicationCoordinator = null;
        io.github.cdimascio.dotenv.Dotenv dotenv = io.github.cdimascio.dotenv.Dotenv.configure().ignoreIfMissing().load();
        this.deduplicationConfig = new DeduplicationConfig(dotenv);
    }
    
    /**
     * Constructor with dependency injection.
     * 
     * @param campaignManager the campaign manager to use
     * @param embeddingService the embedding service to use
     * @param artifactService the artifact graph service to use
     * @param dbConnectionManager the database connection manager to use
     * @param deduplicationCoordinator the deduplication coordinator to use
     * @param sessionManager the session manager for deduplication
     * @param deduplicationConfig the deduplication configuration
     */
    public NoteService(CampaignManager campaignManager,
                      OpenAIEmbeddingService embeddingService,
                      ArtifactGraphService artifactService,
                      DatabaseConnectionManager dbConnectionManager,
                      DeduplicationCoordinator deduplicationCoordinator,
                      DeduplicationSessionManager sessionManager,
                      DeduplicationConfig deduplicationConfig) {
        this.campaignManager = campaignManager;
        this.embeddingService = embeddingService;
        this.traceManager = OTelTraceManager.getInstance();
        this.artifactService = artifactService;
        this.dbConnectionManager = dbConnectionManager;
        this.deduplicationCoordinator = deduplicationCoordinator;
        this.sessionManager = sessionManager;
        this.deduplicationConfig = deduplicationConfig;
    }
    
    /**
     * Adds a note to the specified campaign.
     * Validates the note, generates embedding, stores it, and processes artifacts.
     * For override notes, ensures that there are existing notes to override.
     * 
     * Uses OpenTelemetry for tracking:
     * - Creates a trace for the entire workflow
     * - Creates an observation for the embedding generation
     * - Automatically reports success/failure status
     * 
     * @param note the note to add
     * @param campaign the campaign to add the note to
     * @return true if the note was successfully added, false otherwise
     */
    @Deprecated
    public boolean addNote(Note note, Campain campaign) {
        NoteCreateResponse response = addNoteWithResponse(note, campaign);
        return response.isSuccess();
    }
    
    /**
     * Adds a note to the specified campaign and returns detailed response with progress tracking.
     * Validates the note, generates embedding, stores it, and processes artifacts.
     * For override notes, ensures that there are existing notes to override.
     * 
     * Uses OpenTelemetry for tracking:
     * - Creates a trace for the entire workflow
     * - Creates an observation for the embedding generation
     * - Automatically reports success/failure status
     * 
     * Updates processing status at key stages:
     * - embedding (0-20%)
     * - nae (20-50%)
     * - are (50-80%)
     * - deduplication (80-95%)
     * - saving (95-100%)
     * 
     * @param note the note to add
     * @param campaign the campaign to add the note to
     * @param statusService optional service for tracking processing status
     * @param noteId optional note ID for status tracking
     * @return NoteCreateResponse with success status, note info, and artifact counts
     */
    public NoteCreateResponse addNoteWithResponse(Note note, Campain campaign,
                                                  NoteProcessingStatusService statusService,
                                                  String noteId) {
        // Validation: null checks
        if (note == null || campaign == null) {
            String error = "Note and campaign cannot be null";
            System.err.println(error);
            return createErrorResponse(null, null, error);
        }
        
        // Validate note
        if (!note.isValid()) {
            String error = "Note validation failed: " + note.toString();
            System.err.println(error);
            return createErrorResponse(note.getId(), note.getTitle(), "Note validation failed");
        }
        
        // Special validation for override notes
        if (note.isOverride()) {
            if (!campaignManager.hasExistingNotes(campaign)) {
                String error = "Cannot add override note: No existing notes in campaign to override";
                System.err.println(error);
                return createErrorResponse(note.getId(), note.getTitle(), error);
            }
            System.out.println("Override note validation passed: Found existing notes in campaign");
        }
        
        long startTime = System.currentTimeMillis();
        ArtifactProcessingResult artifactResult = null;
        
        LOGGER.info("[{}] Note processing started", note.getId());
        
        // Create trace for the entire note adding workflow
        try (OTelTrace trace = traceManager.createTrace(
            "note-embedding",
            campaign.getUuid(),
            note.getId(),
            null, // userId
            note.toString() // input
        )) {
            trace.addEvent("embedding_started");
            
            // Create observation for embedding generation
            try (OTelEmbeddingObservation observation = 
                new OTelEmbeddingObservation("embedding-generation", trace.getContext())) {
                
                observation.withModel(embeddingService.getEmbeddingModel())
                           .withInput(note);
                
                // Update status: Embedding stage starting
                if (statusService != null && noteId != null) {
                    statusService.updateStage(noteId, "embedding", "Generowanie embeddingu notatki...", 5);
                }
                
                // Generate embedding with exact token usage
                LOGGER.info("[{}] Starting embedding generation", note.getId());
                long embeddingStart = System.currentTimeMillis();
                String textForEmbedding = note.getFullTextForEmbedding();
                EmbeddingResult embeddingResult = 
                    embeddingService.generateEmbeddingWithUsage(textForEmbedding);
                
                long embeddingDuration = System.currentTimeMillis() - embeddingStart;
                LOGGER.info("[{}] Embedding generation completed in {}ms", note.getId(), embeddingDuration);
                observation.withTokensUsed(embeddingResult.getTokensUsed())
                           .withDuration(embeddingDuration);
                
                // Update status: Embedding completed
                if (statusService != null && noteId != null) {
                    statusService.updateStage(noteId, "embedding", "Embedding wygenerowany", 20);
                }
                
                // Delegate storage to CampaignManager
                boolean stored = campaignManager.addNoteToCampaign(note, campaign, embeddingResult.getEmbedding());
                
                if (!stored) {
                    observation.setError("Failed to store note in campaign");
                    trace.setStatus(false, "Failed to store note in campaign");
                    String error = "Failed to store note in campaign";
                    System.err.println(error);
                    return createErrorResponse(note.getId(), note.getTitle(), error);
                }
                
                observation.setSuccess();
                
                // Add metadata to trace
                trace.setAttribute("embedding.size", embeddingResult.getEmbedding().size());
                trace.setAttribute("embedding.tokens", embeddingResult.getTokensUsed());
                trace.setAttribute("storage.status", "success");
                
                // Process artifacts after successful storage
                try {
                    // Step 1: Extract artifacts and relationships (without saving)
                    // Update status: NAE stage starting
                    if (statusService != null && noteId != null) {
                        statusService.updateStage(noteId, "nae", "Wydobywanie artefaktów...", 25);
                    }
                    
                    LOGGER.info("[{}] Starting artifact extraction", note.getId());
                    long extractStart = System.currentTimeMillis();
                    artifactResult = artifactService.extractArtifactsAndRelationships(note, campaign, trace);
                    long extractDuration = System.currentTimeMillis() - extractStart;
                    LOGGER.info("[{}] Artifact extraction completed in {}ms", note.getId(), extractDuration);
                    
                    // Update status: NAE completed
                    if (statusService != null && noteId != null) {
                        statusService.updateStage(noteId, "nae", "Artefakty wydobyte", 50);
                    }
                    
                    if (!artifactResult.isSuccessful()) {
                        System.err.println("Artifact extraction failed: " + artifactResult.getErrorMessage());
                        trace.addEvent("artifact_extraction_failed");
                        // Note: We don't return error here because the note was successfully stored
                    } else if (artifactResult.getArtifacts().isEmpty() && artifactResult.getRelationships().isEmpty()) {
                        // No artifacts or relationships found - nothing to deduplicate
                        trace.addEvent("no_artifacts_extracted");
                    } else if (deduplicationCoordinator != null) {
                        // Step 2: Perform deduplication
                        trace.addEvent("deduplication_started");
                        
                        // Update status: ARE stage starting
                        if (statusService != null && noteId != null) {
                            statusService.updateStage(noteId, "are", "Identyfikowanie relacji...", 55);
                        }
                        
                        DeduplicationResult artifactDedup = deduplicationCoordinator.processArtifacts(
                            artifactResult.getArtifacts(), note, campaign, 
                            campaign.getQuadrantCollectionName(), trace);
                        
                        DeduplicationResult relationshipDedup = deduplicationCoordinator.processRelationships(
                            artifactResult.getRelationships(), note, campaign, 
                            campaign.getQuadrantCollectionName(), trace);
                        
                        // Update status: ARE completed
                        if (statusService != null && noteId != null) {
                            statusService.updateStage(noteId, "are", "Relacje zidentyfikowane", 80);
                        }
                        
                        // Store deduplication results in artifact result
                        artifactResult.setDeduplicationResult(artifactDedup);
                        
                        // Step 3: Analyze decisions and create merge proposals
                        // Update status: Deduplication stage starting
                        if (statusService != null && noteId != null) {
                            statusService.updateStage(noteId, "deduplication", "Sprawdzanie duplikatów...", 82);
                        }
                        
                        List<MergeProposal> proposals = new ArrayList<>();
                        boolean requiresUserConfirmation = false;
                        int autoMergedArtifacts = 0;
                        int autoMergedRelationships = 0;
                        int confidenceThreshold = deduplicationConfig.getLLMConfidenceThreshold();
                        
                        // Process artifact decisions
                        for (Map.Entry<String, List<DeduplicationDecision>> entry : 
                             artifactDedup.getArtifactDecisions().entrySet()) {
                            List<DeduplicationDecision> decisions = entry.getValue();
                            
                            // Process each decision for this artifact
                            for (DeduplicationDecision decision : decisions) {
                                if (decision.isSame()) {
                                    // Find the artifact
                                    Artifact newArtifact = findArtifactById(artifactResult.getArtifacts(), entry.getKey());
                                    if (newArtifact != null) {
                                        MergeProposal proposal = new MergeProposal(
                                            newArtifact.getId(),
                                            newArtifact.getName(),
                                            decision.getCandidateId(),
                                            decision.getCandidateName(),
                                            "artifact",
                                            decision.getConfidence(),
                                            decision.getReasoning(),
                                            decision.shouldAutoMerge(confidenceThreshold)
                                        );
                                        proposals.add(proposal);
                                        
                                        if (decision.shouldAutoMerge(confidenceThreshold)) {
                                            // Auto-merge high confidence duplicates
                                            boolean merged = artifactService.mergeArtifacts(
                                                decision.getCandidateName(), newArtifact, campaign.getNeo4jLabel(),
                                                campaign.getQuadrantCollectionName());
                                            if (merged) {
                                                autoMergedArtifacts++;
                                                proposal.setApproved(true);
                                            }
                                        } else {
                                            requiresUserConfirmation = true;
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Process relationship decisions
                        for (Map.Entry<String, List<DeduplicationDecision>> entry : 
                             relationshipDedup.getRelationshipDecisions().entrySet()) {
                            List<DeduplicationDecision> decisions = entry.getValue();
                            
                            // Process each decision for this relationship
                            for (DeduplicationDecision decision : decisions) {
                                if (decision.isSame()) {
                                    // Find the relationship
                                    Relationship newRel = findRelationshipById(
                                        artifactResult.getRelationships(), entry.getKey());
                                    if (newRel != null) {
                                        MergeProposal proposal = new MergeProposal(
                                            newRel.getId(),
                                            newRel.getLabel(),
                                            decision.getCandidateId(),
                                            decision.getCandidateName(),
                                            "relationship",
                                            decision.getConfidence(),
                                            decision.getReasoning(),
                                            decision.shouldAutoMerge(confidenceThreshold)
                                        );
                                        proposals.add(proposal);
                                        
                                        if (decision.shouldAutoMerge(confidenceThreshold)) {
                                            // Auto-merge high confidence duplicates for relationships
                                            boolean merged = artifactService.mergeRelationships(
                                                newRel.getSourceArtifactName(),
                                                newRel.getTargetArtifactName(),
                                                decision.getCandidateName(), // existing relationship label
                                                newRel,
                                                campaign.getNeo4jLabel(),
                                                campaign.getQuadrantCollectionName());
                                            if (merged) {
                                                autoMergedRelationships++;
                                                proposal.setApproved(true);
                                            }
                                        } else {
                                            requiresUserConfirmation = true;
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Update status: Deduplication completed
                        if (statusService != null && noteId != null) {
                            statusService.updateStage(noteId, "deduplication", "Duplikaty przetworzone", 95);
                        }
                        
                        artifactResult.setMergeProposals(proposals);
                        
                        // Step 4: Decide on next action
                        if (requiresUserConfirmation) {
                            // Store session and return response with proposals
                            sessionManager.createSession(
                                note.getId(), 
                                campaign.getUuid(),
                                artifactResult.getArtifacts(),
                                artifactResult.getRelationships()
                            );
                            
                            trace.addEvent("deduplication_requires_confirmation");
                            trace.setAttribute("merge_proposals.count", proposals.size());
                            trace.setAttribute("auto_merged.artifacts", autoMergedArtifacts);
                            
                        } else {
                            // All decisions are auto-merge or no duplicates found
                            // Update status: Saving stage starting
                            if (statusService != null && noteId != null) {
                                statusService.updateStage(noteId, "saving", "Zapisywanie do bazy wiedzy...", 96);
                            }
                            
                            // Save new artifacts/relationships to Neo4j with embeddings
                            List<Artifact> newArtifacts = artifactDedup.getNewArtifacts();
                            List<Relationship> newRelationships = relationshipDedup.getNewRelationships();
                            
                            // Build artifact remapping map to fix relationship sources/targets after auto-merge
                            // This prevents lost relationships when merged artifacts are referenced
                            Map<String, String> artifactRemapping = new java.util.HashMap<>();
                            for (Map.Entry<String, List<DeduplicationDecision>> entry : 
                                 artifactDedup.getArtifactDecisions().entrySet()) {
                                List<DeduplicationDecision> decisions = entry.getValue();
                                
                                for (DeduplicationDecision decision : decisions) {
                                    if (decision.isSame() && decision.shouldAutoMerge(confidenceThreshold)) {
                                        // Map new artifact name to existing candidate name
                                        Artifact newArtifact = findArtifactById(artifactResult.getArtifacts(), entry.getKey());
                                        if (newArtifact != null) {
                                            artifactRemapping.put(newArtifact.getName(), decision.getCandidateName());
                                            System.out.println("Auto-merge artifact remapping: " + newArtifact.getName() + 
                                                             " -> " + decision.getCandidateName());
                                        }
                                    }
                                }
                            }
                            
                            // Apply remapping to relationships before saving
                            // If a relationship's source or target was auto-merged, update it to point to the existing artifact
                            if (!artifactRemapping.isEmpty()) {
                                for (Relationship rel : newRelationships) {
                                    if (artifactRemapping.containsKey(rel.getSourceArtifactName())) {
                                        String newSourceName = artifactRemapping.get(rel.getSourceArtifactName());
                                        System.out.println("Remapping relationship source: " + rel.getSourceArtifactName() + 
                                                         " -> " + newSourceName + " for relationship " + rel.getLabel());
                                        rel.setSourceArtifactName(newSourceName);
                                    }
                                    if (artifactRemapping.containsKey(rel.getTargetArtifactName())) {
                                        String newTargetName = artifactRemapping.get(rel.getTargetArtifactName());
                                        System.out.println("Remapping relationship target: " + rel.getTargetArtifactName() + 
                                                         " -> " + newTargetName + " for relationship " + rel.getLabel());
                                        rel.setTargetArtifactName(newTargetName);
                                    }
                                }
                            }
                            
                            boolean saved = artifactService.saveToNeo4j(newArtifacts, newRelationships, campaign,
                                                                       campaign.getQuadrantCollectionName());
                            
                            if (saved) {
                                trace.addEvent("artifacts_saved_after_deduplication");
                                trace.setAttribute("saved.artifacts", newArtifacts.size());
                                trace.setAttribute("saved.relationships", newRelationships.size());
                                trace.setAttribute("auto_merged.artifacts", autoMergedArtifacts);
                                trace.setAttribute("auto_merged.relationships", autoMergedRelationships);
                            } else {
                                System.err.println("Failed to save artifacts to Neo4j after deduplication");
                                trace.addEvent("save_failed_after_deduplication");
                            }
                        }
                        
                        trace.addEvent("deduplication_completed");
                    } else {
                        // No deduplication coordinator - use old workflow (backward compatibility)
                        // Update status: Saving stage starting
                        if (statusService != null && noteId != null) {
                            statusService.updateStage(noteId, "saving", "Zapisywanie do bazy wiedzy...", 96);
                        }
                        
                        // Save with embeddings for consistency
                        artifactService.saveToNeo4j(
                            artifactResult.getArtifacts(), 
                            artifactResult.getRelationships(), 
                            campaign,
                            campaign.getQuadrantCollectionName());
                        trace.addEvent("artifacts_saved_without_deduplication");
                    }
                    
                    if (artifactResult.isSuccessful()) {
                        trace.setAttribute("artifacts.extracted", artifactResult.getArtifacts().size());
                        trace.setAttribute("relationships.extracted", artifactResult.getRelationships().size());
                    }
                } catch (Exception e) {
                    System.err.println("Error during artifact processing: " + e.getMessage());
                    trace.recordException(e);
                    trace.addEvent("artifact_processing_error");
                    // Note: We don't return error here because the note was successfully stored
                }
                
                long totalDuration = System.currentTimeMillis() - startTime;
                trace.setAttribute("total.duration_ms", totalDuration);
                trace.setStatus(true, "Note added successfully");
                
                LOGGER.info("[{}] Note processing completed in {}ms", note.getId(), totalDuration);
                
                // Create successful response with artifact information
                return createSuccessResponse(note, artifactResult);
            }
            
        } catch (Exception e) {
            String errorMessage = "Internal server error: " + e.getMessage();
            System.err.println("Error adding note: " + e.getMessage());
            System.err.println("Exception details: " + e.getClass().getSimpleName());
            return createErrorResponse(note.getId(), note.getTitle(), errorMessage);
        }
    }
    
    /**
     * Adds a note to the specified campaign and returns detailed response.
     * Convenience method that delegates to the full implementation without status tracking.
     * Maintained for backwards compatibility.
     * 
     * @param note the note to add
     * @param campaign the campaign to add the note to
     * @return NoteCreateResponse with success status, note info, and artifact counts
     */
    public NoteCreateResponse addNoteWithResponse(Note note, Campain campaign) {
        return addNoteWithResponse(note, campaign, null, null);
    }

    
    /**
     * Creates an error response.
     */
    private NoteCreateResponse createErrorResponse(String noteId, String title, String errorMessage) {
        NoteCreateResponse response = new NoteCreateResponse();
        response.setNoteId(noteId);
        response.setTitle(title);
        response.setSuccess(false);
        response.setMessage(errorMessage);
        response.setArtifactCount(0);
        response.setRelationshipCount(0);
        return response;
    }
    
    /**
     * Creates a success response with artifact information.
     */
    private NoteCreateResponse createSuccessResponse(Note note, ArtifactProcessingResult artifactResult) {
        NoteCreateResponse response = new NoteCreateResponse(
            note.getId(),
            note.getTitle(),
            true,
            "Note created successfully and artifacts extracted"
        );
        
        response.setCampaignUuid(note.getCampaignUuid());
        
        if (artifactResult != null && artifactResult.isSuccessful()) {
            response.setArtifactCount(artifactResult.getArtifacts().size());
            response.setRelationshipCount(artifactResult.getRelationships().size());
            
            // Add deduplication information
            if (artifactResult.getDeduplicationResult() != null) {
                response.setDeduplicationResult(artifactResult.getDeduplicationResult());
            }
            
            if (!artifactResult.getMergeProposals().isEmpty()) {
                response.setArtifactMergeProposals(artifactResult.getMergeProposals());
                
                // Check if any proposals require user confirmation
                boolean requiresConfirmation = artifactResult.getMergeProposals().stream()
                    .anyMatch(p -> !p.isAutoMerge());
                response.setRequiresUserConfirmation(requiresConfirmation);
                
                // Count auto-merged items
                int autoMergedArtifacts = (int) artifactResult.getMergeProposals().stream()
                    .filter(p -> "artifact".equals(p.getItemType()) && p.isAutoMerge() && p.isApproved())
                    .count();
                    
                int autoMergedRelationships = (int) artifactResult.getMergeProposals().stream()
                    .filter(p -> "relationship".equals(p.getItemType()) && p.isAutoMerge() && p.isApproved())
                    .count();
                
                response.setMergedArtifactCount(autoMergedArtifacts);
                response.setMergedRelationshipCount(autoMergedRelationships);
            }
        } else {
            // Note was stored successfully but artifact processing failed or wasn't performed
            response.setArtifactCount(0);
            response.setRelationshipCount(0);
            if (artifactResult != null && !artifactResult.isSuccessful()) {
                response.setMessage("Note created successfully but artifact extraction failed: " + 
                                   artifactResult.getErrorMessage());
            }
        }
        
        return response;
    }
    
    /**
     * Helper method to find artifact by ID in a list.
     */
    private Artifact findArtifactById(List<Artifact> artifacts, String id) {
        return artifacts.stream()
            .filter(a -> a.getId().equals(id))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Helper method to find relationship by ID in a list.
     */
    private Relationship findRelationshipById(List<Relationship> relationships, String id) {
        return relationships.stream()
            .filter(r -> r.getId().equals(id))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Check if all required services are available
     */
    public boolean checkServicesAvailability() {
        return campaignManager.checkDatabasesAvailability() && 
               embeddingService != null && 
               traceManager != null;
    }
    
    /**
     * Retrieves full Note objects from Qdrant by their IDs.
     * Maintains the order of input noteIds to preserve semantic search ranking.
     * 
     * @param noteIds list of note IDs to retrieve
     * @param collectionName Qdrant collection name to retrieve from
     * @return list of Note objects in the same order as noteIds, empty list on error
     */
    public List<Note> getNotesByIds(List<String> noteIds, String collectionName) {
        if (noteIds == null || noteIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        if (collectionName == null || collectionName.trim().isEmpty()) {
            System.err.println("Collection name cannot be null or empty");
            return new ArrayList<>();
        }
        
        try {
            QdrantClient qdrantClient = dbConnectionManager.getQdrantRepository().getClient();
            if (qdrantClient == null) {
                System.err.println("Qdrant client not available");
                return new ArrayList<>();
            }
            
            // Create a map to store retrieved notes for ordering
            Map<String, Note> noteMap = new LinkedHashMap<>();
            
            // Convert note IDs to numeric IDs (using same hashing as storage)
            List<PointId> pointIds = new ArrayList<>();
            for (String noteId : noteIds) {
                long numericId = Note.getNumericId(noteId);
                pointIds.add(PointId.newBuilder().setNum(numericId).build());
            }
            
            // Retrieve points from Qdrant
            List<RetrievedPoint> retrievedPoints = qdrantClient.retrieveAsync(
                collectionName,
                pointIds,
                true, // with payload
                false, // with vectors
                null  // read consistency
            ).get();
            
            // Convert retrieved points to Note objects
            for (RetrievedPoint point : retrievedPoints) {
                Note note = reconstructNoteFromPayload(point);
                if (note != null) {
                    noteMap.put(note.getId(), note);
                }
            }
            
            // Maintain original order from noteIds
            List<Note> orderedNotes = new ArrayList<>();
            for (String noteId : noteIds) {
                Note note = noteMap.get(noteId);
                if (note != null) {
                    orderedNotes.add(note);
                }
            }
            
            return orderedNotes;
            
        } catch (Exception e) {
            System.err.println("Error retrieving notes from Qdrant: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Caused by: " + e.getCause().getMessage());
            }
            return new ArrayList<>();
        }
    }
    
    /**
     * Reconstructs a Note object from Qdrant point payload.
     * 
     * @param point the retrieved point from Qdrant
     * @return reconstructed Note object or null if reconstruction fails
     */
    private Note reconstructNoteFromPayload(RetrievedPoint point) {
        try {
            var payload = point.getPayloadMap();
            
            if (!payload.containsKey("note_id") || !payload.containsKey("title") || 
                !payload.containsKey("content") || !payload.containsKey("campaign_uuid")) {
                System.err.println("Missing required fields in Qdrant payload");
                return null;
            }
            
            // Create basic note
            String campaignUuid = payload.get("campaign_uuid").getStringValue();
            String title = payload.get("title").getStringValue();
            String content = payload.get("content").getStringValue();
            
            Note note = new Note(campaignUuid, title, content);
            
            // Set ID from payload
            note.setId(payload.get("note_id").getStringValue());
            
            // Set timestamps if available
            if (payload.containsKey("created_at")) {
                try {
                    LocalDateTime createdAt = LocalDateTime.parse(payload.get("created_at").getStringValue());
                    note.setCreatedAt(createdAt);
                } catch (Exception e) {
                    // Keep default timestamp if parsing fails
                }
            }
            
            if (payload.containsKey("updated_at")) {
                try {
                    LocalDateTime updatedAt = LocalDateTime.parse(payload.get("updated_at").getStringValue());
                    note.setUpdatedAt(updatedAt);
                } catch (Exception e) {
                    // Keep default timestamp if parsing fails
                }
            }
            
            // Set override flags
            if (payload.containsKey("is_override")) {
                note.setOverride(payload.get("is_override").getBoolValue());
            }
            
            if (payload.containsKey("is_overridden")) {
                note.setOverridden(payload.get("is_overridden").getBoolValue());
            }
            
            if (payload.containsKey("override_reason")) {
                note.setOverrideReason(payload.get("override_reason").getStringValue());
            }
            
            return note;
            
        } catch (Exception e) {
            System.err.println("Error reconstructing note from payload: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Asynchronously processes note addition in background thread.
     * 
     * Updates processing status at each stage:
     * - embedding (0-20%)
     * - nae (20-50%)
     * - are (50-80%)
     * - deduplication (80-95%)
     * - saving (95-100%)
     * 
     * @param note the note to add
     * @param campaign the campaign to add the note to
     * @param statusService service for tracking status
     * @return CompletableFuture with the processing result
     */
    @Async
    public CompletableFuture<NoteCreateResponse> addNoteAsync(Note note, Campain campaign, 
                                                              NoteProcessingStatusService statusService) {
        String noteId = note.getId();
        
        try {
            // Perform actual note addition with full workflow including progress tracking
            NoteCreateResponse response = addNoteWithResponse(note, campaign, statusService, noteId);
            
            if (!response.isSuccess()) {
                // Mark as failed
                NoteProcessingStatus errorStatus = statusService.getStatus(noteId);
                errorStatus.setStatus("failed");
                errorStatus.setErrorMessage(response.getMessage());
                statusService.updateStatus(errorStatus);
                
                return CompletableFuture.failedFuture(
                    new RuntimeException("Failed to add note: " + response.getMessage())
                );
            }
            
            // Mark as completed
            NoteProcessingStatus finalStatus = statusService.getStatus(noteId);
            finalStatus.setStatus("completed");
            finalStatus.setStage("completed");
            finalStatus.setStageDescription("Notatka została przetworzna pomyślnie");
            finalStatus.setProgress(100);
            finalStatus.setResult(response);
            finalStatus.setCompletedAt(LocalDateTime.now());
            statusService.updateStatus(finalStatus);
            
            return CompletableFuture.completedFuture(response);
            
        } catch (Exception e) {
            System.err.println("Error during async note processing: " + e.getMessage());
            
            // Mark as failed
            try {
                NoteProcessingStatus errorStatus = statusService.getStatus(noteId);
                errorStatus.setStatus("failed");
                errorStatus.setErrorMessage(e.getMessage());
                statusService.updateStatus(errorStatus);
            } catch (Exception statusError) {
                System.err.println("Error updating error status: " + statusError.getMessage());
            }
            
            return CompletableFuture.failedFuture(e);
        }
    }
} 