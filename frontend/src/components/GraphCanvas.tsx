import React, { useRef, useEffect, useCallback, useState, useMemo } from 'react';
import { InteractiveNvlWrapper } from '@neo4j-nvl/react';
import { useCampaignStore, useUIStore, useGraphStore } from '../stores';
import { useGraphData } from '../hooks/useGraphData';
import { useNeo4jGraph } from '../hooks/useNeo4jGraph';
import { getArtifactNeighbors } from '../api/graph';
import { Card } from './ui/card';
import { Loader2, Network, ZoomIn, ZoomOut } from 'lucide-react';
import { Button } from './ui/button';
import { Graph, Node, Edge } from '../types';

/**
 * Graph canvas component for visualizing campaign knowledge graph.
 * Uses Neo4j Visualization Library for interactive graph rendering.
 * Supports both regular graph data and assistant-provided graph data.
 */
const GraphCanvas: React.FC = () => {
  const { selectedCampaign } = useCampaignStore();
  const { setSelectedArtifactId, selectedNoteId } = useUIStore();
  const { assistantGraphData } = useGraphStore();
  const nvlRef = useRef<any>(null);
  const lastClickRef = useRef<{ nodeId: string; timestamp: number } | null>(null);
  
  // Local state for expanded nodes and edges
  const [expandedNodes, setExpandedNodes] = useState<Node[]>([]);
  const [expandedEdges, setExpandedEdges] = useState<Edge[]>([]);

  const { data: fetchedGraph, isLoading, error } = useGraphData(
    selectedCampaign?.uuid,
    selectedNoteId
  );
  
  // Use assistant graph data if available, otherwise use fetched graph
  const graph = assistantGraphData || fetchedGraph;

  // Reset expansions when note changes
  useEffect(() => {
    setExpandedNodes([]);
    setExpandedEdges([]);
  }, [selectedNoteId]);

  // Handle double click to expand graph
  const handleNodeDoubleClick = useCallback(
    async (nodeId: string) => {
      console.log('[GraphCanvas] Node double-clicked:', nodeId);

      if (!selectedCampaign?.uuid) {
        console.warn('[GraphCanvas] Campaign UUID not available');
        return;
      }

      try {
        // Fetch neighbors from API
        const neighbors = await getArtifactNeighbors(
          selectedCampaign.uuid,
          nodeId
        );

        // Add new nodes to expanded state, avoiding duplicates
        const newNodes = neighbors.nodes.filter(
          (n) =>
            !expandedNodes.some((en) => en.id === n.id) &&
            !graph?.nodes.some((gn) => gn.id === n.id)
        );

        // Add new edges to expanded state, avoiding duplicates
        const newEdges = neighbors.edges.filter(
          (e) =>
            !expandedEdges.some(
              (ee) => ee.id === e.id && ee.source === e.source && ee.target === e.target
            ) &&
            !graph?.edges.some(
              (ge) => ge.id === e.id && ge.source === e.source && ge.target === e.target
            )
        );

        setExpandedNodes((prev) => [...prev, ...newNodes]);
        setExpandedEdges((prev) => [...prev, ...newEdges]);

        console.log(
          '[GraphCanvas] Graph expanded: added',
          newNodes.length,
          'nodes and',
          newEdges.length,
          'edges'
        );
      } catch (err) {
        console.error('[GraphCanvas] Error expanding graph:', err);
      }
    },
    [selectedCampaign?.uuid, graph, expandedNodes, expandedEdges]
  );

  // Handle node click - detects double click for graph expansion
  const handleNodeClick = useCallback(
    (nodeId: string) => {
      console.log('[GraphCanvas] Node clicked:', nodeId);
      const currentTime = Date.now();
      const doubleClickDelay = 300; // ms

      // Check if this is a double click
      if (
        lastClickRef.current &&
        lastClickRef.current.nodeId === nodeId &&
        currentTime - lastClickRef.current.timestamp < doubleClickDelay
      ) {
        console.log('[GraphCanvas] Double click detected on node:', nodeId);
        lastClickRef.current = null; // Reset to prevent triple-click
        handleNodeDoubleClick(nodeId);
      } else {
        // Single click - select the artifact
        setSelectedArtifactId(nodeId);
        lastClickRef.current = { nodeId, timestamp: currentTime };
      }
    },
    [setSelectedArtifactId, handleNodeDoubleClick]
  );

  // Merge API graph with local expansions
  const mergedGraph = useMemo(() => {
    if (!graph) return null;

    return {
      nodes: [
        ...graph.nodes,
        ...expandedNodes.filter(
          (n) => !graph.nodes.some((gn) => gn.id === n.id)
        ),
      ],
      edges: [
        ...graph.edges,
        ...expandedEdges.filter(
          (e) =>
            !graph.edges.some(
              (ge) => ge.id === e.id && ge.source === e.source && ge.target === e.target
            )
        ),
      ],
    };
  }, [graph, expandedNodes, expandedEdges]);

  // Prepare graph data for NVL
  const { nodes, relationships, onNodeClick } = useNeo4jGraph({
    graph: mergedGraph,
    onNodeClick: handleNodeClick,
    onNodeDoubleClick: handleNodeDoubleClick,
  });

  // Zoom controls
  const handleZoomIn = () => {
    if (nvlRef.current) {
      const currentZoom = nvlRef.current.getScale?.() || 1;
      nvlRef.current.setZoom?.(currentZoom * 1.3);
    }
  };

  const handleZoomOut = () => {
    if (nvlRef.current) {
      const currentZoom = nvlRef.current.getScale?.() || 1;
      nvlRef.current.setZoom?.(currentZoom * 0.7);
    }
  };

  if (!selectedCampaign) {
    return (
      <Card className="p-6 h-full flex items-center justify-center">
        <div className="text-center">
          <Network className="h-12 w-12 text-muted-foreground mx-auto mb-2" />
          <p className="text-sm text-muted-foreground">
            Select a campaign to view its knowledge graph
          </p>
        </div>
      </Card>
    );
  }

  if (isLoading) {
    return (
      <Card className="p-6 h-full flex items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </Card>
    );
  }

  if (error) {
    return (
      <Card className="p-6 h-full flex items-center justify-center">
        <div className="text-center">
          <p className="text-sm text-destructive">Failed to load graph</p>
        </div>
      </Card>
    );
  }

  if (!graph || (graph.nodes.length === 0 && graph.edges.length === 0)) {
    return (
      <Card className="p-6 h-full flex items-center justify-center">
        <div className="text-center">
          <Network className="h-12 w-12 text-muted-foreground mx-auto mb-2" />
          <p className="text-sm text-muted-foreground">
            No artifacts yet. Add notes to build your knowledge graph.
          </p>
        </div>
      </Card>
    );
  }

  return (
    <Card className="h-full relative overflow-hidden">
      {/* Graph info header */}
      <div className="absolute top-4 left-4 z-10 bg-background/90 backdrop-blur-sm px-3 py-2 rounded-md border border-border shadow-sm">
        <p className="text-xs text-muted-foreground">
          {graph.nodes.length} artifacts • {graph.edges.length} relationships
          {selectedNoteId && ' (filtered)'}
        </p>
      </div>

      {/* Zoom controls */}
      <div className="absolute top-4 right-4 z-10 flex flex-col gap-2">
        <Button
          variant="outline"
          size="icon"
          onClick={handleZoomIn}
          className="bg-background/90 backdrop-blur-sm"
          title="Zoom in"
        >
          <ZoomIn className="h-4 w-4" />
        </Button>
        <Button
          variant="outline"
          size="icon"
          onClick={handleZoomOut}
          className="bg-background/90 backdrop-blur-sm"
          title="Zoom out"
        >
          <ZoomOut className="h-4 w-4" />
        </Button>
      </div>

      {/* NVL Interactive Wrapper */}
      <div className="nvl-container overflow-hidden h-full w-full">
        <InteractiveNvlWrapper
          ref={nvlRef}
          nodes={nodes}
          rels={relationships}
          nvlOptions={{
            initialZoom: 1,
            minZoom: 0.1,
            maxZoom: 4,
            layout: 'forceDirected',
            allowDynamicMinZoom: true,
            disableWebGL: false,
            instanceId: 'campaign-graph',
          }}
          mouseEventCallbacks={{
            onNodeClick: (node) => onNodeClick(node),
            onCanvasClick: true,
            onPan: true,
            onZoom: true,
            onDrag: true,
            onDragEnd: true,
            onDragStart: true,
          }}
        />
      </div>
    </Card>
  );
};

export default GraphCanvas;
