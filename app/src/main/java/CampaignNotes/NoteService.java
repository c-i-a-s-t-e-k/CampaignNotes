package CampaignNotes;

import CampaignNotes.llm.OpenAIEmbeddingService;
import CampaignNotes.tracking.otel.OTelEmbeddingObservation;
import CampaignNotes.tracking.otel.OTelTraceManager;
import CampaignNotes.tracking.otel.OTelTraceManager.OTelTrace;
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
    private final OTelTraceManager traceManager;
    private final ArtifactGraphService artifactService;
    
    /**
     * Constructor initializes all required services with OpenTelemetry tracking.
     */
    public NoteService() {
        this.campaignManager = new CampaignManager();
        this.embeddingService = new OpenAIEmbeddingService();
        
        // Initialize OpenTelemetry trace manager
        this.traceManager = new OTelTraceManager();
        this.artifactService = new ArtifactGraphService();
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
        
        // Create trace for the entire note adding workflow
        try (OTelTrace trace = traceManager.createTrace(
            "note-embedding",
            campaign.getUuid(),
            note.getId(),
            null // userId
        )) {
            trace.addEvent("embedding_started");
            
            // Create observation for embedding generation
            try (OTelEmbeddingObservation observation = 
                new OTelEmbeddingObservation("embedding-generation", trace.getContext())) {
                
                observation.withModel(embeddingService.getEmbeddingModel())
                           .withInput(note);
                
                // Generate embedding with exact token usage
                String textForEmbedding = note.getFullTextForEmbedding();
                EmbeddingResult embeddingResult = 
                    embeddingService.generateEmbeddingWithUsage(textForEmbedding);
                
                long embeddingDuration = System.currentTimeMillis() - startTime;
                observation.withTokensUsed(embeddingResult.getTokensUsed())
                           .withDuration(embeddingDuration);
                
                // Delegate storage to CampaignManager
                boolean stored = campaignManager.addNoteToCampaign(note, campaign, embeddingResult.getEmbedding());
                
                if (stored) {
                    observation.setSuccess();
                    
                    // Add metadata to trace
                    trace.setAttribute("embedding.size", embeddingResult.getEmbedding().size());
                    trace.setAttribute("embedding.tokens", embeddingResult.getTokensUsed());
                    trace.setAttribute("storage.status", "success");
                    
                    // Process artifacts after successful storage
                    try {
                        ArtifactProcessingResult artifactResult = artifactService.processNoteArtifacts(note, campaign);
                        
                        if (artifactResult.isSuccessful()) {
                            
                            trace.setAttribute("artifacts.count", artifactResult.getArtifacts().size());
                            trace.setAttribute("relationships.count", artifactResult.getRelationships().size());
                        } else {
                            System.err.println("Artifact processing failed: " + artifactResult.getErrorMessage());
                            trace.addEvent("artifact_processing_failed");
                            // Note: We don't return false here because the note was successfully stored
                        }
                    } catch (Exception e) {
                        System.err.println("Error during artifact processing: " + e.getMessage());
                        trace.recordException(e);
                        trace.addEvent("artifact_processing_error");
                    }
                    
                    long totalDuration = System.currentTimeMillis() - startTime;
                    trace.setAttribute("total.duration_ms", totalDuration);
                    trace.setStatus(true, "Note added successfully");
                    
                    return true;
                } else {
                    observation.setError("Failed to store note in campaign");
                    trace.setStatus(false, "Failed to store note in campaign");
                    System.err.println("Failed to store note in campaign");
                    return false;
                }
            }
            
        } catch (Exception e) {
            String errorMessage = "Error adding note: " + e.getMessage();
            System.err.println(errorMessage);
            System.err.println("Exception details: " + e.getClass().getSimpleName());
            return false;
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