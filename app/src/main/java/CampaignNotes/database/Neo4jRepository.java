package CampaignNotes.database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.exceptions.ServiceUnavailableException;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Repository responsible for managing the Neo4j driver lifecycle
 * and providing connectivity checks.
 */
public class Neo4jRepository {
    private final Dotenv dotenv;
    private Driver neo4jDriver;

    public Neo4jRepository(Dotenv dotenv) {
        this.dotenv = dotenv;
    }

    public Driver getDriver() {
        if (neo4jDriver == null) {
            if (dotenv == null) {
                System.err.println("Environment variables (Dotenv) not loaded");
                return null;
            }
            String dbUri = dotenv.get("NEO4J_URI");
            String dbUser = dotenv.get("NEO4J_USER");
            String dbPassword = dotenv.get("NEO4J_PASSWORD");

            if (dbUri == null || dbUser == null || dbPassword == null) {
                System.err.println("Neo4j connection parameters are not properly defined");
                return null;
            }

            try {
                neo4jDriver = GraphDatabase.driver(dbUri, AuthTokens.basic(dbUser, dbPassword));
                neo4jDriver.verifyConnectivity();
            } catch (Exception e) {
                System.err.println("Failed to initialize Neo4j driver: " + e.getMessage());
                return null;
            }
        }
        return neo4jDriver;
    }

    public boolean checkAvailability() {
        try {
            Driver driver = getDriver();
            if (driver != null) {
                driver.verifyConnectivity();
                return true;
            }
            return false;
        } catch (ServiceUnavailableException e) {
            System.err.println("Neo4j database is unavailable: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Error connecting to Neo4j: " + e.getMessage());
            return false;
        }
    }

    /**
     * Deletes all nodes and relationships from Neo4j for a given campaign UUID.
     * Performs a hard delete by removing all nodes with matching campaign_uuid property.
     * 
     * @param campaignUuid The UUID of the campaign whose data should be deleted
     * @return true if deletion was successful, false otherwise
     */
    public boolean deleteHardCampaignSubgraphById(String campaignUuid) {
        try {
            Driver driver = getDriver();
            if (driver == null) {
                System.err.println("Neo4j driver not available");
                return false;
            }

            try (var session = driver.session()) {
                return session.executeWrite(tx -> {
                    try {
                        // Delete all nodes with matching campaign_uuid and their relationships
                        String cypher = "MATCH (a) WHERE a.campaign_uuid = $campaignUuid DETACH DELETE a";
                        
                        var result = tx.run(cypher, Map.of("campaignUuid", campaignUuid));
                        var summary = result.consume();
                        
                        int nodesDeleted = summary.counters().nodesDeleted();
                        System.out.println("Deleted " + nodesDeleted + " nodes from Neo4j for campaign UUID: " + campaignUuid);
                        
                        return nodesDeleted >= 0; // Return true even if no nodes were found
                    } catch (Exception e) {
                        System.err.println("Error deleting campaign subgraph from Neo4j: " + e.getMessage());
                        e.printStackTrace();
                        return false;
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Error deleting campaign subgraph from Neo4j: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void close() {
        if (neo4jDriver != null) {
            try {
                neo4jDriver.close();
            } catch (Exception e) {
                System.err.println("Error closing Neo4j connection: " + e.getMessage());
            }
        }
    }

    /**
     * Sanitizes a string to be used as a Neo4j label.
     * Neo4j labels cannot contain spaces, hyphens, or special characters.
     * Only letters, numbers, and underscores are allowed.
     * 
     * @param input the input string to sanitize
     * @return sanitized label suitable for Neo4j
     */
    public static String sanitizeNeo4jLabel(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "DefaultLabel";
        }
        
        String sanitized = input.replaceAll("[^a-zA-Z0-9_]", "_")  // Replace invalid characters with underscore
                   .replaceAll("_{2,}", "_")           // Replace multiple underscores with single
                   .replaceAll("^_+|_+$", "");         // Remove leading/trailing underscores
        
        return sanitized.substring(0, Math.min(sanitized.length(), 100)); // Limit length to prevent very long labels
    }
    
    /**
     * Sanitizes a relationship type for Neo4j.
     * Neo4j relationship types must be valid identifiers.
     * 
     * @param label the relationship label to sanitize
     * @return sanitized relationship type suitable for Neo4j
     */
    public static String sanitizeRelationshipType(String label) {
        if (label == null || label.trim().isEmpty()) {
            return "RELATED_TO";
        }
        
        return label.toUpperCase()
                   .replaceAll("[^A-Z0-9_]", "_")
                   .replaceAll("_{2,}", "_")
                   .replaceAll("^_+|_+$", "");
    }
    
    /**
     * Retrieves all artifacts for a given campaign from Neo4j.
     * Returns a list of artifact maps with properties: id, name, type, description.
     * 
     * @param campaignUuid The UUID of the campaign
     * @return List of artifact maps, empty list if no artifacts or error
     */
    public List<Map<String, Object>> getAllArtifacts(String campaignUuid) {
        List<Map<String, Object>> artifacts = new ArrayList<>();
        
        try {
            Driver driver = getDriver();
            if (driver == null) {
                System.err.println("Neo4j driver not available");
                return artifacts;
            }
            
            try (var session = driver.session()) {
                String cypher = "MATCH (a) WHERE a.campaign_uuid = $campaignUuid " +
                               "RETURN a.id AS id, a.name AS name, a.type AS type, a.description AS description " +
                               "ORDER BY a.name";
                
                var result = session.run(cypher, Map.of("campaignUuid", campaignUuid));
                
                while (result.hasNext()) {
                    Record record = result.next();
                    Map<String, Object> artifact = new HashMap<>();
                    artifact.put("id", record.get("id").asString());
                    artifact.put("name", record.get("name").asString());
                    artifact.put("type", record.get("type").asString());
                    artifact.put("description", record.get("description").asString(""));
                    artifacts.add(artifact);
                }
                
                System.out.println("Retrieved " + artifacts.size() + " artifacts for campaign: " + campaignUuid);
            }
            
        } catch (Exception e) {
            System.err.println("Error retrieving artifacts from Neo4j: " + e.getMessage());
            e.printStackTrace();
        }
        
        return artifacts;
    }
    
    /**
     * Retrieves all unique relationship types/labels for a given campaign from Neo4j.
     * Returns a list of relationship label strings.
     * 
     * @param campaignUuid The UUID of the campaign
     * @return List of unique relationship labels, empty list if none or error
     */
    public List<String> getAllRelationshipTypes(String campaignUuid) {
        List<String> relationshipTypes = new ArrayList<>();
        
        try {
            Driver driver = getDriver();
            if (driver == null) {
                System.err.println("Neo4j driver not available");
                return relationshipTypes;
            }
            
            try (var session = driver.session()) {
                String cypher = "MATCH (a)-[r]->(b) " +
                               "WHERE a.campaign_uuid = $campaignUuid AND b.campaign_uuid = $campaignUuid " +
                               "RETURN DISTINCT r.label AS label " +
                               "ORDER BY r.label";
                
                var result = session.run(cypher, Map.of("campaignUuid", campaignUuid));
                
                while (result.hasNext()) {
                    Record record = result.next();
                    String label = record.get("label").asString("");
                    if (!label.isEmpty()) {
                        relationshipTypes.add(label);
                    }
                }
                
                System.out.println("Retrieved " + relationshipTypes.size() + " relationship types for campaign: " + campaignUuid);
            }
            
        } catch (Exception e) {
            System.err.println("Error retrieving relationship types from Neo4j: " + e.getMessage());
            e.printStackTrace();
        }
        
        return relationshipTypes;
    }
    
    /**
     * Retrieves all artifact pairs connected by a specific relationship type.
     * Returns a list of maps containing source and target artifact information.
     * 
     * @param campaignUuid The UUID of the campaign
     * @param relationshipLabel The relationship label to filter by
     * @return List of relationship maps with source and target artifacts, empty list if none or error
     */
    public List<Map<String, Object>> getArtifactPairsByRelationType(String campaignUuid, String relationshipLabel) {
        List<Map<String, Object>> pairs = new ArrayList<>();
        
        try {
            Driver driver = getDriver();
            if (driver == null) {
                System.err.println("Neo4j driver not available");
                return pairs;
            }
            
            try (var session = driver.session()) {
                String cypher = "MATCH (a)-[r]->(b) " +
                               "WHERE a.campaign_uuid = $campaignUuid AND b.campaign_uuid = $campaignUuid " +
                               "AND r.label = $relationshipLabel " +
                               "RETURN a.id AS sourceId, a.name AS sourceName, a.type AS sourceType, " +
                               "b.id AS targetId, b.name AS targetName, b.type AS targetType, " +
                               "r.id AS relationshipId, r.label AS label, r.description AS description";
                
                var result = session.run(cypher, Map.of(
                    "campaignUuid", campaignUuid,
                    "relationshipLabel", relationshipLabel
                ));
                
                while (result.hasNext()) {
                    Record record = result.next();
                    Map<String, Object> pair = new HashMap<>();
                    
                    // Source artifact
                    Map<String, Object> source = new HashMap<>();
                    source.put("id", record.get("sourceId").asString());
                    source.put("name", record.get("sourceName").asString());
                    source.put("type", record.get("sourceType").asString());
                    pair.put("source", source);
                    
                    // Target artifact
                    Map<String, Object> target = new HashMap<>();
                    target.put("id", record.get("targetId").asString());
                    target.put("name", record.get("targetName").asString());
                    target.put("type", record.get("targetType").asString());
                    pair.put("target", target);
                    
                    // Relationship
                    pair.put("relationshipId", record.get("relationshipId").asString());
                    pair.put("label", record.get("label").asString());
                    pair.put("description", record.get("description").asString(""));
                    
                    pairs.add(pair);
                }
                
                System.out.println("Retrieved " + pairs.size() + " artifact pairs for relationship: " + relationshipLabel);
            }
            
        } catch (Exception e) {
            System.err.println("Error retrieving artifact pairs from Neo4j: " + e.getMessage());
            e.printStackTrace();
        }
        
        return pairs;
    }
}


