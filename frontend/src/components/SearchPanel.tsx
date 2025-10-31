import React, { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { searchNotes } from '../api';
import { SearchRequest, SearchResult } from '../types';
import { useCampaignStore, useUIStore } from '../stores';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Card } from './ui/card';
import { Search, Loader2 } from 'lucide-react';
import SearchResults from './SearchResults';
import toast from 'react-hot-toast';

/**
 * Search panel component for semantic search.
 */
const SearchPanel: React.FC = () => {
  const { selectedCampaign } = useCampaignStore();
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SearchResult[]>([]);

  const searchMutation = useMutation({
    mutationFn: (request: SearchRequest) => {
      if (!selectedCampaign) {
        throw new Error('No campaign selected');
      }
      return searchNotes(selectedCampaign.uuid, request);
    },
    onSuccess: (data) => {
      setResults(data);
      if (data.length === 0) {
        toast('No results found', { icon: '🔍' });
      }
    },
    onError: (error) => {
      toast.error('Search failed');
      console.error(error);
      setResults([]);
    },
  });

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!selectedCampaign) {
      toast.error('Please select a campaign first');
      return;
    }

    if (!query.trim()) {
      toast.error('Please enter a search query');
      return;
    }

    searchMutation.mutate({ query: query.trim(), limit: 5 });
  };

  if (!selectedCampaign) {
    return (
      <Card className="p-6">
        <p className="text-sm text-muted-foreground text-center">
          Select a campaign to search notes
        </p>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <Card className="p-4">
        <h3 className="text-lg font-semibold mb-4">Search Notes</h3>
        <form onSubmit={handleSearch} className="space-y-3">
          <div className="flex gap-2">
            <Input
              placeholder="Enter search query..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              disabled={searchMutation.isPending}
            />
            <Button
              type="submit"
              disabled={searchMutation.isPending || !query.trim()}
            >
              {searchMutation.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Search className="h-4 w-4" />
              )}
            </Button>
          </div>
        </form>
      </Card>

      {results.length > 0 && <SearchResults results={results} />}
    </div>
  );
};

export default SearchPanel;

