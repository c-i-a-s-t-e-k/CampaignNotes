package CampaignNotes;

import java.util.HashMap;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import CampaignNotes.database.DatabaseConnectionManager;
import CampaignNotes.dto.EdgeDTO;
import CampaignNotes.dto.GraphDTO;
import CampaignNotes.dto.NodeDTO;
import model.Campain;

/**
 * Service for retrieving graph data from Neo4j database.
 * Provides methods to fetch campaign-specific knowledge graphs.
 */
@Service
public class GraphService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphService.class);
    
    private final DatabaseConnectionManager dbConnectionManager;
    private final CampaignManager campaignManager;
    
    public GraphService(DatabaseConnectionManager dbConnectionManager, CampaignManager campaignManager) {
        this.dbConnectionManager = dbConnectionManager;
        this.campaignManager = campaignManager;
    }
    
    /**
     * Retrieves the full graph for a campaign.
     * Fetches all nodes and relationships belonging to the specified campaign.
     * 
     * @param campaignUuid UUID of the campaign
     * @return GraphDTO containing nodes and edges
     */
    public GraphDTO getGraphForCampaign(String campaignUuid) {
        LOGGER.info("Fetching graph for campaign: {}", campaignUuid);
        
        if (campaignUuid == null || campaignUuid.trim().isEmpty()) {
            throw new IllegalArgumentException("Campaign UUID cannot be null or empty");
        }
        
        // Verify campaign exists
        Campain campaign = campaignManager.getCampaignByUuid(campaignUuid);
        if (campaign == null) {
            throw new IllegalArgumentException("Campaign not found with UUID: " + campaignUuid);
        }
        
        GraphDTO graph = new GraphDTO();
        Driver driver = dbConnectionManager.getNeo4jRepository().getDriver();
        
        if (driver == null) {
            LOGGER.error("Neo4j driver not available");
            return graph; // Return empty graph
        }
        
        try (Session session = driver.session()) {
            // Map to track nodes we've already added (by internal ID)
            Map<Long, String> processedNodes = new HashMap<>();
            
            // Query to fetch all nodes and relationships for the campaign
            String cypher = 
                "MATCH (n) " +
                "WHERE n.campaign_uuid = $campaignUuid " +
                "OPTIONAL MATCH (n)-[r]-(m) " +
                "WHERE m.campaign_uuid = $campaignUuid " +
                "RETURN n, r, m";
            
            Map<String, Object> params = Map.of("campaignUuid", campaignUuid);
            
            Result result = session.run(cypher, params);
            
            while (result.hasNext()) {
                Record record = result.next();
                
                // Process first node (n)
                Value nValue = record.get("n");
                if (!nValue.isNull()) {
                    Node node = nValue.asNode();
                    String nodeId = processNode(node, graph, processedNodes);
                }
                
                // Process relationship (r) and second node (m) if they exist
                Value rValue = record.get("r");
                Value mValue = record.get("m");
                
                if (!rValue.isNull() && !mValue.isNull()) {
                    Relationship rel = rValue.asRelationship();
                    Node secondNode = mValue.asNode();
                    
                    // Process second node
                    String secondNodeId = processNode(secondNode, graph, processedNodes);
                    
                    // Process relationship
                    processRelationship(rel, graph, processedNodes);
                }
            }
            
            LOGGER.info("Graph fetched successfully: {} nodes, {} edges", 
                       graph.getNodes().size(), graph.getEdges().size());
            
        } catch (Exception e) {
            LOGGER.error("Error fetching graph for campaign {}: {}", campaignUuid, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch graph: " + e.getMessage(), e);
        }
        
        return graph;
    }
    
    /**
     * Processes a Neo4j node and adds it to the graph if not already processed.
     * 
     * @param node Neo4j node
     * @param graph GraphDTO to add the node to
     * @param processedNodes Map of already processed nodes
     * @return The node ID (from properties or internal ID)
     */
    private String processNode(Node node, GraphDTO graph, Map<Long, String> processedNodes) {
        long internalId = node.id();
        
        // Check if we've already processed this node
        if (processedNodes.containsKey(internalId)) {
            return processedNodes.get(internalId);
        }
        
        // Extract node properties
        String id = node.get("id").asString("");
        if (id.isEmpty()) {
            id = String.valueOf(internalId);
        }
        
        String name = node.get("name").asString("");
        String type = node.get("type").asString("");
        String description = node.get("description").asString("");
        String campaignUuid = node.get("campaign_uuid").asString("");
        String noteId = node.get("note_id").asString("");
        
        NodeDTO nodeDTO = new NodeDTO(id, name, type, description, campaignUuid, noteId);
        graph.addNode(nodeDTO);
        
        processedNodes.put(internalId, id);
        
        return id;
    }
    
    /**
     * Processes a Neo4j relationship and adds it to the graph.
     * 
     * @param rel Neo4j relationship
     * @param graph GraphDTO to add the edge to
     * @param processedNodes Map of processed nodes to get source/target IDs
     */
    private void processRelationship(Relationship rel, GraphDTO graph, Map<Long, String> processedNodes) {
        String id = rel.get("id").asString("");
        if (id.isEmpty()) {
            id = String.valueOf(rel.id());
        }
        
        // Get source and target node IDs
        long startNodeId = rel.startNodeId();
        long endNodeId = rel.endNodeId();
        
        String sourceId = processedNodes.get(startNodeId);
        String targetId = processedNodes.get(endNodeId);
        
        if (sourceId == null || targetId == null) {
            LOGGER.warn("Skipping relationship {} - source or target node not found", id);
            return;
        }
        
        String label = rel.type();
        String description = rel.get("description").asString("");
        String reasoning = rel.get("reasoning").asString("");
        
        EdgeDTO edgeDTO = new EdgeDTO(id, sourceId, targetId, label, description, reasoning);
        graph.addEdge(edgeDTO);
    }
}

