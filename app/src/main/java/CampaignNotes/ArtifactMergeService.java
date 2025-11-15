package CampaignNotes;

import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

import CampaignNotes.database.DatabaseConnectionManager;
import CampaignNotes.database.Neo4jRepository;
import model.Artifact;
import model.Relationship;

/**
 * Service for merging artifacts and relationships in Neo4j during deduplication.
 * Handles the consolidation of duplicate nodes while preserving relationships
 * and note source tracking.
 * 
 * @deprecated This service has been superseded by merge methods in ArtifactGraphService
 *             which also handle embedding updates in Qdrant. Use ArtifactGraphService
 *             methods instead: mergeArtifacts() and mergeRelationships().
 */
@Deprecated
public class ArtifactMergeService {
    
    private final DatabaseConnectionManager dbConnectionManager;
    private final GraphEmbeddingService graphEmbeddingService;
    
    /**
     * Constructor with dependency injection.
     * 
     * @param dbConnectionManager the database connection manager
     * @param graphEmbeddingService the graph embedding service for updating embeddings
     */
    public ArtifactMergeService(DatabaseConnectionManager dbConnectionManager,
                               GraphEmbeddingService graphEmbeddingService) {
        this.dbConnectionManager = dbConnectionManager;
        this.graphEmbeddingService = graphEmbeddingService;
    }
    
    /**
     * Merges a new artifact into an existing artifact.
     * Complete merge: combines note_ids from both artifacts.
     * 
     * @param targetArtifactName the name of the existing artifact to merge into
     * @param newArtifact the new artifact to merge
     * @param campaign the campaign context
     * @return true if merge was successful, false otherwise
     * @deprecated Use ArtifactGraphService.mergeArtifacts() instead, which also updates embeddings in Qdrant
     */
    @Deprecated
    public boolean mergeArtifacts(String targetArtifactName, Artifact newArtifact, 
                                 String campaignLabel) {
        try {
            Driver driver = dbConnectionManager.getNeo4jRepository().getDriver();
            if (driver == null) {
                System.err.println("Neo4j driver not available");
                return false;
            }
            
            try (Session session = driver.session()) {
                return session.executeWrite(tx -> {
                    try {
                        String sanitizedLabel = Neo4jRepository.sanitizeNeo4jLabel(campaignLabel) + "_Artifact";
                        
                        // Get existing artifact to merge note_ids
                        String getCypher = String.format(
                            "MATCH (a:%s {name: $target_name}) " +
                            "RETURN a.note_ids AS note_ids",
                            sanitizedLabel
                        );
                        
                        var getCursor = tx.run(getCypher, 
                            Map.of("target_name", targetArtifactName));
                        
                        List<String> existingNoteIds = new java.util.ArrayList<>();
                        if (getCursor.hasNext()) {
                            var record = getCursor.next();
                            var noteIdsValue = record.get("note_ids");
                            if (!noteIdsValue.isNull()) {
                                existingNoteIds = noteIdsValue.asList(v -> v.asString());
                            }
                        }
                        
                        // Merge new note IDs
                        List<String> mergedNoteIds = new java.util.ArrayList<>(existingNoteIds);
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
                            return true;
                        }
                        
                        return false;
                        
                    } catch (Exception e) {
                        System.err.println("Error in merge transaction: " + e.getMessage());
                        return false;
                    }
                });
            }
            
        } catch (Exception e) {
            System.err.println("Error merging artifacts: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Merges a new relationship into an existing relationship.
     * Complete merge: combines note_ids from both relationships.
     * 
     * @param sourceArtifactName source artifact name for target relationship
     * @param targetArtifactName target artifact name for target relationship
     * @param relationshipLabel label of the relationship to merge into
     * @param newRelationship the new relationship to merge
     * @param campaignLabel the campaign label
     * @return true if merge was successful, false otherwise
     * @deprecated Use ArtifactGraphService.mergeRelationships() instead, which also updates embeddings in Qdrant
     */
    @Deprecated
    public boolean mergeRelationships(String sourceArtifactName, String targetArtifactName,
                                     String relationshipLabel, Relationship newRelationship,
                                     String campaignLabel) {
        try {
            Driver driver = dbConnectionManager.getNeo4jRepository().getDriver();
            if (driver == null) {
                System.err.println("Neo4j driver not available");
                return false;
            }
            
            try (Session session = driver.session()) {
                return session.executeWrite(tx -> {
                    try {
                        String sanitizedLabel = Neo4jRepository.sanitizeNeo4jLabel(campaignLabel) + "_Artifact";
                        String sanitizedRelType = Neo4jRepository.sanitizeRelationshipType(relationshipLabel);
                        String sanitizedNewRelType = Neo4jRepository.sanitizeRelationshipType(newRelationship.getLabel());
                        
                        // Get existing relationship to merge note_ids
                        String getCypher = String.format(
                            "MATCH (a:%s {name: $source_name})-[r:%s]->(b:%s {name: $target_name}) " +
                            "RETURN r.note_ids AS note_ids",
                            sanitizedLabel, sanitizedRelType, sanitizedLabel
                        );
                        
                        var getCursor = tx.run(getCypher,
                            Map.of("source_name", sourceArtifactName,
                                   "target_name", targetArtifactName));
                        
                        List<String> existingNoteIds = new java.util.ArrayList<>();
                        if (getCursor.hasNext()) {
                            var record = getCursor.next();
                            var noteIdsValue = record.get("note_ids");
                            if (!noteIdsValue.isNull()) {
                                existingNoteIds = noteIdsValue.asList(v -> v.asString());
                            }
                        }
                        
                        // Merge note IDs
                        List<String> mergedNoteIds = new java.util.ArrayList<>(existingNoteIds);
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
                            return true;
                        }
                        
                        return false;
                        
                    } catch (Exception e) {
                        System.err.println("Error in relationship merge transaction: " + e.getMessage());
                        return false;
                    }
                });
            }
            
        } catch (Exception e) {
            System.err.println("Error merging relationships: " + e.getMessage());
            return false;
        }
    }
}

