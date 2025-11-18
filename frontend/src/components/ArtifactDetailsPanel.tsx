/**
 * Panel displaying details of a selected artifact from the graph.
 * Shows artifact information and associated notes.
 */

import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useCampaignStore, useUIStore, useGraphStore } from '../stores';
import { useGraphData } from '../hooks/useGraphData';
import { getArtifactNotes } from '../api';
import { Card } from './ui/card';
import { Button } from './ui/button';
import { Badge } from './ui/badge';
import { Loader2, X, FileText, Network } from 'lucide-react';

/**
 * Component to display artifact details and associated notes.
 */
const ArtifactDetailsPanel: React.FC = () => {
  const { selectedCampaign } = useCampaignStore();
  const { setSelectedNoteId, setSelectedArtifactId } = useUIStore();
  const { selectedNodeId } = useGraphStore();

  // Fetch graph data to get artifact details
  const { data: graph } = useGraphData(selectedCampaign?.uuid);

  // Find the selected artifact node
  const artifact = graph?.nodes.find((node) => node.id === selectedNodeId);

  // Fetch notes for the artifact
  const { data: notes, isLoading, error } = useQuery({
    queryKey: ['artifactNotes', selectedCampaign?.uuid, selectedNodeId],
    queryFn: () => {
      if (!selectedCampaign?.uuid || !selectedNodeId) {
        throw new Error('Campaign UUID or artifact ID not provided');
      }
      return getArtifactNotes(selectedCampaign.uuid, selectedNodeId);
    },
    enabled: !!selectedCampaign?.uuid && !!selectedNodeId,
  });

  // Handle closing the panel
  const handleClose = () => {
    setSelectedArtifactId(null);
  };

  // Handle note click
  const handleNoteClick = (noteId: string) => {
    setSelectedNoteId(noteId);
  };

  if (!artifact) {
    return (
      <Card className="p-4">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold">Artifact Details</h3>
          <Button variant="ghost" size="sm" onClick={handleClose}>
            <X className="h-4 w-4" />
          </Button>
        </div>
        <p className="text-sm text-muted-foreground">Artifact not found</p>
      </Card>
    );
  }

  return (
    <Card className="p-4 h-full flex flex-col">
      {/* Header */}
      <div className="flex items-start justify-between mb-4">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-2">
            <Network className="h-5 w-5 text-muted-foreground flex-shrink-0" />
            <h3 className="text-lg font-semibold truncate">{artifact.name}</h3>
          </div>
          <Badge
            variant="secondary"
            className="mb-2"
            style={{
              backgroundColor: getColorForType(artifact.type) + '20',
              color: getColorForType(artifact.type),
              borderColor: getColorForType(artifact.type),
            }}
          >
            {artifact.type}
          </Badge>
          {artifact.description && (
            <p className="text-sm text-muted-foreground mt-2">
              {artifact.description}
            </p>
          )}
        </div>
        <Button variant="ghost" size="sm" onClick={handleClose}>
          <X className="h-4 w-4" />
        </Button>
      </div>

      {/* Notes Section */}
      <div className="flex-1 overflow-y-auto">
        <h4 className="text-sm font-semibold mb-3 flex items-center gap-2">
          <FileText className="h-4 w-4" />
          Associated Notes
        </h4>

        {isLoading && (
          <div className="flex items-center justify-center py-8">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        )}

        {error && (
          <div className="text-sm text-destructive py-4">
            Failed to load notes
          </div>
        )}

        {notes && notes.length === 0 && (
          <div className="text-sm text-muted-foreground py-4">
            No notes associated with this artifact
          </div>
        )}

        {notes && notes.length > 0 && (
          <div className="space-y-2">
            {notes.map((note) => (
              <div
                key={note.id}
                onClick={() => handleNoteClick(note.id)}
                className="p-3 bg-accent rounded-md border border-border hover:bg-accent/80 cursor-pointer transition-colors"
              >
                <h5 className="text-sm font-medium mb-1">{note.title}</h5>
                <p className="text-xs text-muted-foreground line-clamp-2">
                  {note.content}
                </p>
                <div className="flex items-center gap-2 mt-2 text-xs text-muted-foreground">
                  <span>{note.wordCount} words</span>
                  <span>•</span>
                  <span>
                    {new Date(note.createdAt).toLocaleDateString()}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </Card>
  );
};

// Helper function to get color for artifact type
const getColorForType = (type: string): string => {
  const colors: Record<string, string> = {
    characters: '#3b82f6',
    locations: '#10b981',
    items: '#f59e0b',
    events: '#ef4444',
    organizations: '#8b5cf6',
    concepts: '#06b6d4',
  };

  const lowerType = type.toLowerCase();
  return colors[lowerType] || '#6b7280';
};

export default ArtifactDetailsPanel;

