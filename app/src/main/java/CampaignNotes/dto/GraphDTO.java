package CampaignNotes.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing a campaign graph with nodes and edges.
 */
public class GraphDTO {
    private List<NodeDTO> nodes;
    private List<EdgeDTO> edges;
    
    public GraphDTO() {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
    }
    
    public GraphDTO(List<NodeDTO> nodes, List<EdgeDTO> edges) {
        this.nodes = nodes != null ? nodes : new ArrayList<>();
        this.edges = edges != null ? edges : new ArrayList<>();
    }
    
    public List<NodeDTO> getNodes() {
        return nodes;
    }
    
    public void setNodes(List<NodeDTO> nodes) {
        this.nodes = nodes;
    }
    
    public List<EdgeDTO> getEdges() {
        return edges;
    }
    
    public void setEdges(List<EdgeDTO> edges) {
        this.edges = edges;
    }
    
    public void addNode(NodeDTO node) {
        this.nodes.add(node);
    }
    
    public void addEdge(EdgeDTO edge) {
        this.edges.add(edge);
    }
}

