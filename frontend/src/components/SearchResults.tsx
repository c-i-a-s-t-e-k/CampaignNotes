import React from 'react';
import { SearchResult } from '../types';
import { useUIStore } from '../stores';
import { Card } from './ui/card';
import { FileText } from 'lucide-react';

interface SearchResultsProps {
  results: SearchResult[];
}

/**
 * Component displaying search results.
 */
const SearchResults: React.FC<SearchResultsProps> = ({ results }) => {
  const { setSelectedNoteId } = useUIStore();

  const handleResultClick = (noteId: string) => {
    setSelectedNoteId(noteId);
  };

  return (
    <Card className="p-4">
      <h4 className="text-sm font-semibold mb-3">
        Results ({results.length})
      </h4>
      <div className="space-y-2">
        {results.map((result) => (
          <div
            key={result.noteId}
            className="p-3 bg-accent rounded-md cursor-pointer hover:bg-accent/80 transition-colors"
            onClick={() => handleResultClick(result.noteId)}
          >
            <div className="flex items-start gap-2">
              <FileText className="h-4 w-4 mt-0.5 text-muted-foreground flex-shrink-0" />
              <div className="flex-1 min-w-0">
                <h5 className="font-medium text-sm mb-1">{result.title}</h5>
                <p className="text-xs text-muted-foreground line-clamp-2">
                  {result.contentPreview}
                </p>
              </div>
            </div>
          </div>
        ))}
      </div>
    </Card>
  );
};

export default SearchResults;

