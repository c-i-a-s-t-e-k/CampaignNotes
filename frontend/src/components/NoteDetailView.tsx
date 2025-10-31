import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { getNote } from '../api';
import { useCampaignStore } from '../stores';
import { Card } from './ui/card';
import { Button } from './ui/button';
import { X, Loader2 } from 'lucide-react';

interface NoteDetailViewProps {
  noteId: string;
  onClose: () => void;
}

/**
 * Component to display full note details.
 */
const NoteDetailView: React.FC<NoteDetailViewProps> = ({ noteId, onClose }) => {
  const { selectedCampaign } = useCampaignStore();

  const { data: note, isLoading, error } = useQuery({
    queryKey: ['note', selectedCampaign?.uuid, noteId],
    queryFn: () => {
      if (!selectedCampaign) {
        throw new Error('No campaign selected');
      }
      return getNote(selectedCampaign.uuid, noteId);
    },
    enabled: !!selectedCampaign && !!noteId,
  });

  if (isLoading) {
    return (
      <Card className="p-6">
        <div className="flex items-center justify-center">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </div>
      </Card>
    );
  }

  if (error) {
    return (
      <Card className="p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold text-destructive">Error</h3>
          <Button variant="ghost" size="sm" onClick={onClose}>
            <X className="h-4 w-4" />
          </Button>
        </div>
        <p className="text-sm text-muted-foreground">Failed to load note</p>
      </Card>
    );
  }

  if (!note) {
    return null;
  }

  return (
    <Card className="p-6">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-semibold">Note Details</h3>
        <Button variant="ghost" size="sm" onClick={onClose}>
          <X className="h-4 w-4" />
        </Button>
      </div>

      <div className="space-y-4">
        <div>
          <h4 className="font-medium mb-1">{note.title}</h4>
          <p className="text-xs text-muted-foreground">
            Created: {new Date(note.createdAt).toLocaleDateString()}
          </p>
        </div>

        <div>
          <p className="text-sm whitespace-pre-wrap">{note.content}</p>
        </div>

        <div className="pt-4 border-t border-border">
          <p className="text-xs text-muted-foreground">
            {note.wordCount} words
          </p>
        </div>
      </div>
    </Card>
  );
};

export default NoteDetailView;

