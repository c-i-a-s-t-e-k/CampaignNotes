package CampaignNotes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import CampaignNotes.database.DatabaseConnectionManager;
import CampaignNotes.llm.OpenAIEmbeddingService;
import CampaignNotes.tracking.otel.OTelEmbeddingObservation;
import CampaignNotes.tracking.otel.OTelTraceManager;
import CampaignNotes.tracking.otel.OTelTraceManager.OTelTrace;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.PointId;
import io.qdrant.client.grpc.Points.RetrievedPoint;
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
    private final DatabaseConnectionManager dbConnectionManager;
    
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
    }
    
    /**
     * Constructor with dependency injection.
     * 
     * @param campaignManager the campaign manager to use
     * @param embeddingService the embedding service to use
     * @param artifactService the artifact graph service to use
     * @param dbConnectionManager the database connection manager to use
     */
    public NoteService(CampaignManager campaignManager,
                      OpenAIEmbeddingService embeddingService,
                      ArtifactGraphService artifactService,
                      DatabaseConnectionManager dbConnectionManager) {
        this.campaignManager = campaignManager;
        this.embeddingService = embeddingService;
        this.traceManager = OTelTraceManager.getInstance();
        this.artifactService = artifactService;
        this.dbConnectionManager = dbConnectionManager;
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
            null, // userId
            note.toString() // input
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
} 