package CampaignNotes;

import CampaignNotes.llm.OpenAIEmbeddingService;
import CampaignNotes.tracking.LangfuseConfig;
import CampaignNotes.tracking.LangfuseHttpClient;
import CampaignNotes.tracking.LangfuseModelService;
import CampaignNotes.tracking.trace.TraceManager;
import CampaignNotes.tracking.trace.observations.EmbedingObservation;
import CampaignNotes.tracking.trace.payload.IngestionPayloadBuilder;
import CampaignNotes.tracking.trace.traces.Trace;
import CampaignNotes.tracking.trace.traces.TraceType;
import model.ArtifactProcessingResult;
import model.Campain;
import model.EmbeddingResult;
import model.Note;

/**
 * Service for managing campaign notes.
 * Handles note validation, embedding generation, and delegates storage to CampaignManager.
 */
public class NoteService {
    
    private final CampaignManager campaignManager; 
    private final OpenAIEmbeddingService embeddingService;
    private final TraceManager traceManager;
    private final ArtifactGraphService artifactService;
    
    /**
     * Constructor initializes all required services with new tracking components.
     */
    public NoteService() {
        this.campaignManager = new CampaignManager();
        this.embeddingService = new OpenAIEmbeddingService();
        
        // Initialize new tracking components
        LangfuseConfig config = new LangfuseConfig();
        LangfuseHttpClient httpClient = new LangfuseHttpClient(config);
        LangfuseModelService modelService = new LangfuseModelService(httpClient);
        IngestionPayloadBuilder payloadBuilder = new IngestionPayloadBuilder(modelService);
        
        this.traceManager = new TraceManager(httpClient, payloadBuilder);
        this.artifactService = new ArtifactGraphService(traceManager);
    }
    
    /**
     * Adds a note to the specified campaign.
     * Validates the note, generates embedding, stores it, and processes artifacts.
     * For override notes, ensures that there are existing notes to override.
     * 
     * Implements proper trace lifecycle management following ArtifactGraphService pattern:
     * 1. TRACE LIFECYCLE START: Create and initialize trace
     * 2. Process embedding with observation tracking
     * 3. TRACE LIFECYCLE END: Finalize trace with success/error
     * 
     * @param note the note to add
     * @param campaign the campaign to add the note to
     * @return true if the note was successfully added, false otherwise
     */
    public boolean addNote(Note note, Campain campaign) {
        if (note == null || campaign == null) {
            System.err.println("Note and campaign cannot be null");
            return false;
        }
        
        // Validate note
        if (!note.isValid()) {
            System.err.println("Note validation failed: " + note.toString());
            return false;
        }
        
        // Special validation for override notes
        if (note.isOverride()) {
            if (!campaignManager.hasExistingNotes(campaign)) {
                System.err.println("Cannot add override note: No existing notes in campaign to override");
                return false;
            }
            System.out.println("Override note validation passed: Found existing notes in campaign");
        }
        
        long startTime = System.currentTimeMillis();
        Trace trace = null; // Work with Trace object for proper lifecycle management
        
        try {
            System.out.println("Starting note embedding for note: " + note.getId());
            
            // TRACE LIFECYCLE START: Create and initialize trace
            try {
                trace = traceManager.createTraceWithInput(
                    "note-embedding", 
                    campaign.getUuid(), 
                    note.getId(),
                    note.getFullTextForEmbedding(),
                    java.util.Collections.emptyList(), // No categories for embedding
                    TraceType.EMBEDDING_TRACE,
                    null // userId
                );
                System.out.println("EmbedingTrace initialized successfully: " + (trace != null ? trace.getTraceId() : "null"));
            } catch (Exception e) {
                System.err.println("Error creating trace: " + e.getMessage());
                trace = null;
            }
            
            if (trace == null) {
                System.err.println("Warning: Failed to create trace, continuing without tracking");
            }
            
            // Generate embedding with exact token usage
            String textForEmbedding = note.getFullTextForEmbedding();
            
            // Use new method that returns both embedding and exact token count
            EmbeddingResult embeddingResult = 
                embeddingService.generateEmbeddingWithUsage(textForEmbedding);
            
            // TRACE LIFECYCLE: Add embedding observation to trace
            if (trace != null) {
                try {
                    EmbedingObservation observation = new EmbedingObservation(
                        "note-embedding", 
                        traceManager.getHttpClient(), 
                        traceManager.getPayloadBuilder()
                    )
                        .withNote(note)
                        .withCampaignId(campaign.getUuid())
                        .withModel(embeddingService.getEmbeddingModel())
                        .withTokenUsage(embeddingResult.getTokensUsed())
                        .finalizeForSending();
                    
                    // Add observation to trace (proper trace lifecycle)
                    traceManager.trackNewObservation(trace, observation);
                    System.out.println("EmbedingObservation sent successfully: " + observation.getObservationId() + 
                        " (tokens: " + embeddingResult.getTokensUsed() + ")");
                } catch (Exception e) {
                    System.err.println("Error tracking embedding observation: " + e.getMessage());
                }
            }
            
            // Delegate storage to CampaignManager
            boolean stored = campaignManager.addNoteToCampaign(note, campaign, embeddingResult.getEmbedding());
            
            if (stored) {
                System.out.println("Note successfully added to campaign: " + campaign.getName() + 
                    " (Used " + embeddingResult.getTokensUsed() + " tokens)");
                
                // Process artifacts after successful storage
                System.out.println("Starting artifact extraction for note: " + note.getId());
                try {
                    ArtifactProcessingResult artifactResult = artifactService.processNoteArtifacts(note, campaign);
                    
                    if (artifactResult.isSuccessful()) {
                        System.out.println("Artifact processing completed: " + 
                                         artifactResult.getArtifacts().size() + " artifacts, " +
                                         artifactResult.getRelationships().size() + " relationships extracted");
                    } else {
                        System.err.println("Artifact processing failed: " + artifactResult.getErrorMessage());
                        // Note: We don't return false here because the note was successfully stored
                        // Artifact processing failure is not critical to the main workflow
                    }
                } catch (Exception e) {
                    System.err.println("Error during artifact processing: " + e.getMessage());
                    // Continue - artifact processing is supplementary to note storage
                }
                
                long totalDuration = System.currentTimeMillis() - startTime;
                
                // TRACE LIFECYCLE END: Finalize trace with successful results
                finalizeTraceWithSuccess(trace, embeddingResult.getTokensUsed(), totalDuration, 
                    embeddingResult.getEmbedding().size(), stored);
                
                return true;
            } else {
                // TRACE LIFECYCLE END: Finalize trace with failure
                finalizeTraceWithError(trace, "Failed to store note in campaign", startTime);
                
                System.err.println("Failed to store note in campaign");
                return false;
            }
            
        } catch (Exception e) {
            String errorMessage = "Error adding note: " + e.getMessage();
            System.err.println(errorMessage);
            System.err.println("Exception details: " + e.getClass().getSimpleName());
            
            // TRACE LIFECYCLE END: Finalize trace with exception error
            finalizeTraceWithError(trace, e.getMessage(), startTime);
            
            return false;
        }
    }
    
    /**
     * Finalizes trace with successful results (proper trace lifecycle completion).
     * 
     * @param trace the trace to finalize
     * @param tokensUsed tokens used for embedding generation
     * @param totalDuration total processing duration
     * @param embeddingSize size of generated embedding vector
     * @param stored whether note was successfully stored
     */
    private void finalizeTraceWithSuccess(Trace trace, int tokensUsed, long totalDuration, 
                                         int embeddingSize, boolean stored) {
        if (trace != null && !trace.isFinalized()) {
            try {
                // Use TraceManager's updateTraceOutput method for proper lifecycle
                java.util.List<String> outputMetadata = java.util.Arrays.asList(
                    "embedding_size:" + embeddingSize,
                    "tokens_used:" + tokensUsed,
                    "storage_status:" + (stored ? "success" : "failed")
                );
                
                traceManager.updateTraceOutput(trace, 1, 0, // 1 embedding generated, 0 relationships 
                    outputMetadata, "success", totalDuration).join(); // Wait for completion
                
                System.out.println("EmbedingTrace finalized successfully: " + trace.getTraceId() + 
                    " (tokens: " + tokensUsed + ", duration: " + totalDuration + "ms)");
                
            } catch (Exception e) {
                System.err.println("Error finalizing trace with success: " + e.getMessage());
            }
        }
    }
    
    /**
     * Finalizes trace with error (proper trace lifecycle completion on failure).
     * 
     * @param trace the trace to finalize
     * @param errorMessage error message
     * @param startTime workflow start time
     */
    private void finalizeTraceWithError(Trace trace, String errorMessage, long startTime) {
        if (trace != null && !trace.isFinalized()) {
            try {
                long totalDuration = System.currentTimeMillis() - startTime;
                
                // Use TraceManager's updateTraceOutput method for proper lifecycle
                traceManager.updateTraceOutput(trace, 0, 0, 
                    java.util.Collections.emptyList(), "error: " + errorMessage, totalDuration).join();
                
                System.err.println("EmbedingTrace finalized with error: " + trace.getTraceId() + " - " + errorMessage);
                
            } catch (Exception e) {
                System.err.println("Error finalizing trace with error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Check if all required services are available
     */
    public boolean checkServicesAvailability() {
        return campaignManager.checkDatabasesAvailability() && 
               embeddingService != null && 
               traceManager != null;
    }
} 