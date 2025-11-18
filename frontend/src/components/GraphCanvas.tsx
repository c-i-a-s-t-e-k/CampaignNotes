import React, { useRef, useEffect } from 'react';
import { InteractiveNvlWrapper } from '@neo4j-nvl/react';
import { useCampaignStore, useUIStore } from '../stores';
import { useGraphData } from '../hooks/useGraphData';
import { useNeo4jGraph } from '../hooks/useNeo4jGraph';
import { Card } from './ui/card';
import { Loader2, Network, ZoomIn, ZoomOut } from 'lucide-react';
import { Button } from './ui/button';

/**
 * Graph canvas component for visualizing campaign knowledge graph.
 * Uses Neo4j Visualization Library for interactive graph rendering.
 */
const GraphCanvas: React.FC = () => {
  const { selectedCampaign } = useCampaignStore();
  const { setSelectedArtifactId } = useUIStore();
  const nvlRef = useRef<any>(null);

  const { data: graph, isLoading, error } = useGraphData(selectedCampaign?.uuid);

  // Handle node click
  const handleNodeClick = (nodeId: string) => {
    console.log('[GraphCanvas] Node clicked:', nodeId);
    setSelectedArtifactId(nodeId);
  };

  // Prepare graph data for NVL
  const { nodes, relationships, onNodeClick } = useNeo4jGraph({
    graph: graph || null,
    onNodeClick: handleNodeClick,
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
