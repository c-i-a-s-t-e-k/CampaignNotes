import React from 'react';
import { SourceReference } from '../types/assistant';
import { useUIStore } from '../stores';
import { Card } from './ui/card';
import { FileText } from 'lucide-react';

interface SourcesListProps {
  sources: SourceReference[];
}

/**
 * Component displaying source references (notes) with clickable links.
 */
const SourcesList: React.FC<SourcesListProps> = ({ sources }) => {
  const { setSelectedNoteId } = useUIStore();

  if (sources.length === 0) {
    return null;
  }

  const handleSourceClick = (noteId: string) => {
    setSelectedNoteId(noteId);
  };

  return (
    <Card className="p-4">
      <h4 className="text-sm font-semibold mb-3">
        Sources ({sources.length})
      </h4>
      <div className="space-y-2">
        {sources.map((source) => (
          <div
            key={source.noteId}
            className="p-2 bg-accent rounded-md cursor-pointer hover:bg-accent/80 transition-colors flex items-start gap-2"
            onClick={() => handleSourceClick(source.noteId)}
          >
            <FileText className="h-4 w-4 mt-0.5 text-muted-foreground flex-shrink-0" />
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium">{source.noteTitle}</p>
            </div>
          </div>
        ))}
      </div>
    </Card>
  );
};

export default SourcesList;

