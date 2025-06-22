package CampaignNotes;

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
    private final LangfuseClient langfuseClient;
    private final ArtifactGraphService artifactService;
    
    /**
     * Constructor initializes all required services.
     */
    public NoteService() {
        this.campaignManager = new CampaignManager();
        this.embeddingService = new OpenAIEmbeddingService();
        this.langfuseClient = new LangfuseClient();
        this.artifactService = new ArtifactGraphService();
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
        
        // Start tracking session in Langfuse
        String traceId = langfuseClient.trackNoteProcessingSession(
            "add-note", campaign.getUuid(), note.getId(), null);
        
        try {
            // Generate embedding with exact token usage
            long startTime = System.currentTimeMillis();
            String textForEmbedding = note.getFullTextForEmbedding();
            
            // Use new method that returns both embedding and exact token count
            EmbeddingResult embeddingResult = 
                embeddingService.generateEmbeddingWithUsage(textForEmbedding);
            
            long durationMs = System.currentTimeMillis() - startTime;
            
            // Track embedding generation in Langfuse with full note information and exact tokens
            langfuseClient.trackEmbedding(
                traceId,
                note,  // Pass the full note object instead of just the text
                embeddingService.getEmbeddingModel(),
                campaign.getUuid(),
                embeddingResult.getTokensUsed(),  // Use exact token count from OpenAI
                durationMs
            );
            
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
               langfuseClient.checkConnection();
    }
} 