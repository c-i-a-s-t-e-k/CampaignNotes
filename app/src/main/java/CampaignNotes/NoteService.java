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
        
        // Start tracking session using new TraceManager with appropriate TraceType
        String traceId = null;
        try {
            Trace trace = traceManager.createTraceByType(
                "note-embedding", 
                campaign.getUuid(), 
                note.getId(), 
                null, // userId
                java.util.Collections.emptyList(), // customTags
                "embedding-generation", // workflowType
                TraceType.EMBEDDING_TRACE // Use appropriate enum for embedding operations
            );
            trace.initialize().get(); // Wait for initialization
            traceId = trace.getTraceId();
        } catch (Exception e) {
            System.err.println("Error creating trace for embedding: " + e.getMessage());
        }
        
        
        try {
            // Generate embedding with exact token usage
            long startTime = System.currentTimeMillis();
            String textForEmbedding = note.getFullTextForEmbedding();
            
            // Use new method that returns both embedding and exact token count
            EmbeddingResult embeddingResult = 
                embeddingService.generateEmbeddingWithUsage(textForEmbedding);
            
            // Track embedding generation using direct observation creation
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
                
                observation.sendToTrace(traceId).get(); // Wait for completion
            } catch (Exception e) {
                System.err.println("Error tracking embedding: " + e.getMessage());
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
                
                return true;
            } else {
                System.err.println("Failed to store note in campaign");
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("Error adding note: " + e.getMessage());
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