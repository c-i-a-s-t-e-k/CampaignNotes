/**
 * Custom hook for Neo4j Visualization Library integration.
 * Handles graph initialization, styling, layout, and interactions.
 */

import { useEffect, useRef, useCallback } from 'react';
import type { Node as NVLNode, Relationship as NVLRelationship } from '@neo4j-nvl/base';
import { Graph, Node, Edge } from '../types';
import { useGraphStore } from '../stores';

// Color mapping for artifact types
const TYPE_COLORS: Record<string, string> = {
  characters: '#3b82f6',
  locations: '#10b981',
  items: '#f59e0b',
  events: '#ef4444',
  organizations: '#8b5cf6',
  concepts: '#06b6d4',
  default: '#34495e', 
};

/**
 * Convert API graph data to NVL format
 */
const convertToNVLFormat = (graph: Graph) => {
  console.log('[useNeo4jGraph] Converting graph data to NVL format:', graph);
  const nodes: NVLNode[] = graph.nodes.map((node: Node) => ({
    id: node.id,
    caption: node.name,
    captionSize: 2,
    color: TYPE_COLORS[node.type.toLowerCase()] || TYPE_COLORS.default,
    size: 100,
  }));

  const relationships: NVLRelationship[] = graph.edges.map((edge: Edge) => ({
    id: edge.id,
    from: edge.source,
    to: edge.target,
    caption: edge.label,
    captionSize: 6,
    type: edge.label,
    size: 100,
  }));

  return { nodes, relationships };
};

interface UseNeo4jGraphOptions {
  graph: Graph | null;
  onNodeClick?: (nodeId: string) => void;
  onNodeDoubleClick?: (nodeId: string) => void;
}

export const useNeo4jGraph = ({
  graph,
  onNodeClick,
  onNodeDoubleClick,
}: UseNeo4jGraphOptions) => {
  const { setSelectedNodeId } = useGraphStore();

  // Convert graph data to NVL format
  const nvlData = useCallback(() => {
    if (!graph) return { nodes: [], relationships: [] };

    try {
      const converted = convertToNVLFormat(graph);
      console.log('[useNeo4jGraph] Graph data prepared:', {
        nodeCount: converted.nodes.length,
        relationshipCount: converted.relationships.length,
      });
      return converted;
    } catch (error) {
      console.error('[useNeo4jGraph] Error converting graph:', error);
      return { nodes: [], relationships: [] };
    }
  }, [graph]);

  // Handle node click
  const handleNodeClick = useCallback(
    (node: NVLNode) => {
      console.log('[useNeo4jGraph] Node clicked:', node.id);
      setSelectedNodeId(node.id);
      if (onNodeClick) {
        onNodeClick(node.id);
      }
    },
    [setSelectedNodeId, onNodeClick]
  );

  // Handle node double click
  const handleNodeDoubleClick = useCallback(
    (node: NVLNode) => {
      console.log('[useNeo4jGraph] Node double-clicked:', node.id);
      if (onNodeDoubleClick) {
        onNodeDoubleClick(node.id);
      }
    },
    [onNodeDoubleClick]
  );

  // Handle canvas click
  const handleCanvasClick = useCallback(() => {
    console.log('[useNeo4jGraph] Canvas clicked - deselecting node');
    setSelectedNodeId(null);
  }, [setSelectedNodeId]);

  return {
    nodes: nvlData().nodes,
    relationships: nvlData().relationships,
    onNodeClick: handleNodeClick,
    onNodeDoubleClick: handleNodeDoubleClick,
    onCanvasClick: handleCanvasClick,
  };
};

