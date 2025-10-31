import React from 'react';
import { useCampaignStore } from '../stores';
import { useGraphData } from '../hooks/useGraphData';
import { Card } from './ui/card';
import { Loader2, Network } from 'lucide-react';

/**
 * Graph canvas component for visualizing campaign knowledge graph.
 * This is a simplified MVP version showing graph data as a list.
 * Full Neo4j NVL integration can be added in future iterations.
 */
const GraphCanvas: React.FC = () => {
  const { selectedCampaign } = useCampaignStore();

  const { data: graph, isLoading, error } = useGraphData(selectedCampaign?.uuid);

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
    <Card className="p-6 h-full overflow-auto">
      <div className="mb-4">
        <h3 className="text-lg font-semibold mb-2">Knowledge Graph</h3>
        <p className="text-sm text-muted-foreground">
          {graph.nodes.length} artifacts, {graph.edges.length} relationships
        </p>
      </div>

      {/* Simplified visualization - MVP version */}
      <div className="space-y-6">
        {/* Artifacts */}
        <div>
          <h4 className="text-sm font-semibold mb-3">Artifacts</h4>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {graph.nodes.map((node) => (
              <div
                key={node.id}
                className="p-3 bg-accent rounded-md border border-border"
              >
                <div className="flex items-start gap-2">
                  <div
                    className="h-3 w-3 rounded-full flex-shrink-0 mt-1"
                    style={{
                      backgroundColor: getColorForType(node.type),
                    }}
                  />
                  <div className="flex-1 min-w-0">
                    <p className="font-medium text-sm">{node.name}</p>
                    <p className="text-xs text-muted-foreground">{node.type}</p>
                    {node.description && (
                      <p className="text-xs text-muted-foreground mt-1 line-clamp-2">
                        {node.description}
                      </p>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Relationships */}
        {graph.edges.length > 0 && (
          <div>
            <h4 className="text-sm font-semibold mb-3">Relationships</h4>
            <div className="space-y-2">
              {graph.edges.map((edge) => {
                const sourceNode = graph.nodes.find((n) => n.id === edge.source);
                const targetNode = graph.nodes.find((n) => n.id === edge.target);

                return (
                  <div
                    key={edge.id}
                    className="p-3 bg-accent rounded-md text-sm"
                  >
                    <div className="flex items-center gap-2">
                      <span className="font-medium">{sourceNode?.name || edge.source}</span>
                      <span className="text-muted-foreground">→</span>
                      <span className="text-xs bg-background px-2 py-0.5 rounded">
                        {edge.label}
                      </span>
                      <span className="text-muted-foreground">→</span>
                      <span className="font-medium">{targetNode?.name || edge.target}</span>
                    </div>
                    {edge.description && (
                      <p className="text-xs text-muted-foreground mt-2">
                        {edge.description}
                      </p>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>

      <div className="mt-6 p-3 bg-muted rounded-md">
        <p className="text-xs text-muted-foreground">
          <strong>Note:</strong> This is a simplified view. Full interactive graph
          visualization with Neo4j NVL can be implemented in future versions.
        </p>
      </div>
    </Card>
  );
};

// Helper function to assign colors to artifact types
const getColorForType = (type: string): string => {
  const colors: Record<string, string> = {
    character: '#3b82f6',
    location: '#10b981',
    item: '#f59e0b',
    event: '#ef4444',
    organization: '#8b5cf6',
    concept: '#06b6d4',
  };

  const lowerType = type.toLowerCase();
  return colors[lowerType] || '#6b7280';
};

export default GraphCanvas;

