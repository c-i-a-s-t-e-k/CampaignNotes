package CampaignNotes.assistant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import CampaignNotes.database.Neo4jRepository;
import CampaignNotes.dto.EdgeDTO;
import CampaignNotes.dto.GraphDTO;
import CampaignNotes.dto.NodeDTO;

/**
 * Service for executing read-only Cypher queries against Neo4j.
 * Provides secure graph data retrieval with validation.
 */
@Service
public class GraphQueryService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphQueryService.class);
    
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(30);
    
    private final Neo4jRepository neo4jRepository;
    private final CypherValidator validator;
    
    public GraphQueryService(Neo4jRepository neo4jRepository, CypherValidator validator) {
        this.neo4jRepository = neo4jRepository;
        this.validator = validator;
    }
    
    /**
     * Executes a read-only Cypher query and returns graph data.
     * 
     * @param cypherQuery the Cypher query to execute
     * @param parameters the query parameters
     * @return GraphDTO with nodes and edges
     */
    public GraphDTO executeReadOnlyQuery(String cypherQuery, Map<String, Object> parameters) {
        LOGGER.info("Executing read-only Cypher query");
        LOGGER.debug("Query: {}", cypherQuery);
        LOGGER.debug("Parameters: {}", parameters);
        
        // Validate query
        CypherValidator.ValidationResult validation = validator.validate(cypherQuery);
        if (!validation.isValid()) {
            LOGGER.error("Cypher validation failed: {}", validation.getError());
            throw new InvalidCypherException(validation.getError());
        }
        
        Driver driver = neo4jRepository.getDriver();
        if (driver == null) {
            LOGGER.error("Neo4j driver not available");
            throw new RuntimeException("Neo4j driver not available");
        }
        
        try (Session session = driver.session(SessionConfig.builder()
                .withDefaultAccessMode(org.neo4j.driver.AccessMode.READ)
                .build())) {
            
            return session.executeRead(tx -> {
                try {
                    Result result = tx.run(cypherQuery, parameters);
                    GraphDTO graphData = parseResultToGraphDTO(result);
                    
                    LOGGER.info("Query executed successfully. Nodes: {}, Edges: {}",
                            graphData.getNodes().size(), graphData.getEdges().size());
                    
                    return graphData;
                    
                } catch (Exception e) {
                    LOGGER.error("Error executing Neo4j query: {}", e.getMessage(), e);
                    throw new RuntimeException("Failed to execute Neo4j query", e);
                }
            });
            
        } catch (Exception e) {
            LOGGER.error("Error in read-only query execution: {}", e.getMessage(), e);
            throw new RuntimeException("Graph query failed", e);
        }
    }
    
    /**
     * Parses Neo4j Result into GraphDTO.
     */
    private GraphDTO parseResultToGraphDTO(Result result) {
        GraphDTO graphDTO = new GraphDTO();
        List<NodeDTO> nodesCollected = new ArrayList<>();
        List<EdgeDTO> edgesCollected = new ArrayList<>();
        
        while (result.hasNext()) {
            var record = result.next();
            
            // Extract nodes and relationships from the record
            for (String key : record.keys()) {
                Value value = record.get(key);
                
                if (value.hasType(org.neo4j.driver.internal.types.InternalTypeSystem.TYPE_SYSTEM.NODE())) {
                    Node node = value.asNode();
                    NodeDTO nodeDTO = convertNodeToDTO(node);
                    
                    // Check for duplicates
                    if (nodesCollected.stream().noneMatch(n -> n.getId().equals(nodeDTO.getId()))) {
                        nodesCollected.add(nodeDTO);
                    }
                }
                else if (value.hasType(org.neo4j.driver.internal.types.InternalTypeSystem.TYPE_SYSTEM.RELATIONSHIP())) {
                    Relationship rel = value.asRelationship();
                    EdgeDTO edgeDTO = convertRelationshipToDTO(rel);
                    
                    // Check for duplicates
                    if (edgesCollected.stream().noneMatch(e -> e.getId().equals(edgeDTO.getId()))) {
                        edgesCollected.add(edgeDTO);
                    }
                }
                else if (value.hasType(org.neo4j.driver.internal.types.InternalTypeSystem.TYPE_SYSTEM.PATH())) {
                    var path = value.asPath();
                    
                    // Extract nodes from path
                    for (Node node : path.nodes()) {
                        NodeDTO nodeDTO = convertNodeToDTO(node);
                        if (nodesCollected.stream().noneMatch(n -> n.getId().equals(nodeDTO.getId()))) {
                            nodesCollected.add(nodeDTO);
                        }
                    }
                    
                    // Extract relationships from path
                    for (Relationship rel : path.relationships()) {
                        EdgeDTO edgeDTO = convertRelationshipToDTO(rel);
                        if (edgesCollected.stream().noneMatch(e -> e.getId().equals(edgeDTO.getId()))) {
                            edgesCollected.add(edgeDTO);
                        }
                    }
                }
                else if (value.hasType(org.neo4j.driver.internal.types.InternalTypeSystem.TYPE_SYSTEM.LIST())) {
                    // Handle lists of nodes or relationships
                    List<Object> list = value.asList();
                    for (Object item : list) {
                        if (item instanceof Node) {
                            NodeDTO nodeDTO = convertNodeToDTO((Node) item);
                            if (nodesCollected.stream().noneMatch(n -> n.getId().equals(nodeDTO.getId()))) {
                                nodesCollected.add(nodeDTO);
                            }
                        } else if (item instanceof Relationship) {
                            EdgeDTO edgeDTO = convertRelationshipToDTO((Relationship) item);
                            if (edgesCollected.stream().noneMatch(e -> e.getId().equals(edgeDTO.getId()))) {
                                edgesCollected.add(edgeDTO);
                            }
                        }
                    }
                }
            }
        }
        
        graphDTO.setNodes(nodesCollected);
        graphDTO.setEdges(edgesCollected);
        
        return graphDTO;
    }
    
    /**
     * Converts a Neo4j Node to NodeDTO.
     */
    private NodeDTO convertNodeToDTO(Node node) {
        NodeDTO nodeDTO = new NodeDTO();
        nodeDTO.setId(node.get("id").asString());
        nodeDTO.setName(node.get("name").asString("Unknown"));
        nodeDTO.setType(node.get("type").asString("unknown"));
        nodeDTO.setDescription(node.get("description").asString(""));
        nodeDTO.setCampaignUuid(node.get("campaignUuid").asString(""));
        
        // Note IDs
        if (node.containsKey("note_ids")) {
            nodeDTO.setNoteIds(node.get("note_ids").asList(Value::asString));
        } else {
            nodeDTO.setNoteIds(new ArrayList<>());
        }
        
        return nodeDTO;
    }
    
    /**
     * Converts a Neo4j Relationship to EdgeDTO.
     * Note: Source and target should use artifact IDs, not Neo4j internal element IDs.
     * This will need to be retrieved from the connected nodes.
     */
    private EdgeDTO convertRelationshipToDTO(Relationship rel) {
        EdgeDTO edgeDTO = new EdgeDTO();
        edgeDTO.setId(rel.get("id").asString());
        
        // TODO: Need to get actual artifact IDs from start/end nodes
        // For now, using element IDs as placeholder - this may need fixing
        edgeDTO.setSource(String.valueOf(rel.startNodeElementId()));
        edgeDTO.setTarget(String.valueOf(rel.endNodeElementId()));
        
        edgeDTO.setLabel(rel.get("label").asString(rel.type()));
        edgeDTO.setDescription(rel.get("description").asString(""));
        edgeDTO.setReasoning(rel.get("reasoning").asString(""));
        
        // Note IDs
        if (rel.containsKey("note_ids")) {
            edgeDTO.setNoteIds(rel.get("note_ids").asList(Value::asString));
        } else {
            edgeDTO.setNoteIds(new ArrayList<>());
        }
        
        return edgeDTO;
    }
    
    /**
     * Custom exception for invalid Cypher queries.
     */
    public static class InvalidCypherException extends RuntimeException {
        public InvalidCypherException(String message) {
            super(message);
        }
    }
}

