package CampaignNotes;

import java.util.List;

import model.Campain;
import model.Note;

/**
 * Service for managing campaign notes.
 * Handles note validation, embedding generation, and delegates storage to CampaignManager.
 */
public class NoteService {
    
    private final CampaignManager campaignManager;
    private final OpenAIEmbeddingService embeddingService;
    private final LangfuseClient langfuseClient;
    
    /**
     * Constructor initializes all required services.
     */
    public NoteService() {
        this.campaignManager = new CampaignManager();
        this.embeddingService = new OpenAIEmbeddingService();
        this.langfuseClient = new LangfuseClient();
    }
    
    /**
     * Adds a note to the specified campaign.
     * Validates the note, generates embedding, and delegates storage to CampaignManager.
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
            // Generate embedding
            long startTime = System.currentTimeMillis();
            String textForEmbedding = note.getFullTextForEmbedding();
            List<Double> embedding = embeddingService.generateEmbedding(textForEmbedding);
            long durationMs = System.currentTimeMillis() - startTime;
            
            // Track embedding generation in Langfuse
            int estimatedTokens = estimateTokenCount(textForEmbedding);
            langfuseClient.trackEmbedding(
                textForEmbedding, 
                embeddingService.getEmbeddingModel(),
                campaign.getUuid(),
                note.getId(),
                estimatedTokens,
                durationMs
            );
            
            // Delegate storage to CampaignManager
            boolean stored = campaignManager.addNoteToCampaign(note, campaign, embedding);
            
            if (stored) {
                System.out.println("Note successfully added to campaign: " + campaign.getName());
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
     * Estimates token count for text
     */
    private int estimateTokenCount(String text) {
        // Simple estimation: approximately 4 characters per token
        return text.length() / 4;
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