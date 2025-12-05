import React, { useState, useEffect } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { getAllNotes, searchNotes } from '../api';
import { useCampaignStore, useUIStore } from '../stores';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Card } from './ui/card';
import { Search, Loader2 } from 'lucide-react';
import SearchResults from './SearchResults';
import toast from 'react-hot-toast';

/**
 * Search panel component for viewing all notes and semantic search.
 * Default state shows all notes with pagination, search state shows filtered results.
 */
const SearchPanel: React.FC = () => {
  const { selectedCampaign } = useCampaignStore();
  const { searchQuery, searchResults, setSearchQuery, setSearchResults } = useUIStore();
  
  const [offset, setOffset] = useState(0);
  const [isSearchMode, setIsSearchMode] = useState(false);
  const LIMIT = 50;

  // Query for fetching all notes
  const { data: allNotesData, isLoading: isLoadingAll } = useQuery({
    queryKey: ['allNotes', selectedCampaign?.uuid, offset],
    queryFn: () => {
      if (!selectedCampaign) {
        throw new Error('No campaign selected');
      }
      return getAllNotes(selectedCampaign.uuid, LIMIT, offset);
    },
    enabled: !!selectedCampaign && !isSearchMode,
  });

  // Mutation for searching notes
  const searchMutation = useMutation({
    mutationFn: (query: string) => {
      if (!selectedCampaign) {
        throw new Error('No campaign selected');
      }
      return searchNotes(selectedCampaign.uuid, { query: query.trim(), limit: 50 });
    },
    onSuccess: (data) => {
      setSearchResults(data);
      if (data.length === 0) {
        toast('No results found', { icon: '🔍' });
      }
    },
    onError: (error) => {
      toast.error('Search failed');
      console.error(error);
      setSearchResults([]);
    },
  });

  // Effect to reset offset when campaign changes
  useEffect(() => {
    setOffset(0);
    setIsSearchMode(false);
    setSearchQuery('');
    setSearchResults([]);
  }, [selectedCampaign?.uuid, setSearchQuery, setSearchResults]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!selectedCampaign) {
      toast.error('Please select a campaign first');
      return;
    }

    if (!searchQuery.trim()) {
      // If search is cleared, go back to all notes view
      setIsSearchMode(false);
      setOffset(0);
      setSearchResults([]);
      return;
    }

    // Enter search mode
    setIsSearchMode(true);
    setOffset(0);
    searchMutation.mutate(searchQuery.trim());
  };

  const handleLoadMore = () => {
    setOffset(offset + LIMIT);
  };

  const handleClearSearch = () => {
    setSearchQuery('');
    setSearchResults([]);
    setIsSearchMode(false);
    setOffset(0);
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

  const displayResults = isSearchMode ? searchResults : allNotesData?.notes || [];
  const totalCount = isSearchMode ? searchResults.length : allNotesData?.totalCount || 0;
  const hasMore = isSearchMode ? false : allNotesData?.hasMore || false;
  const isLoading = isSearchMode ? searchMutation.isPending : isLoadingAll;

  return (
    <div className="space-y-4">
      <Card className="p-4">
        <h3 className="text-lg font-semibold mb-4">
          {isSearchMode ? 'Search Results' : 'All Notes'}
        </h3>
        <form onSubmit={handleSearch} className="space-y-3">
          <div className="flex gap-2">
            <Input
              placeholder="Enter search query..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              disabled={isLoading}
            />
            <Button
              type="submit"
              disabled={isLoading}
              title={isSearchMode ? 'Search notes' : 'Search or clear'}
            >
              {isLoading ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Search className="h-4 w-4" />
              )}
            </Button>
          </div>
          {isSearchMode && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={handleClearSearch}
              disabled={isLoading}
              className="w-full"
            >
              Clear Search
            </Button>
          )}
        </form>
      </Card>

      {/* Show loading state for all notes */}
      {isLoading && !isSearchMode && (
        <Card className="p-6">
          <div className="flex items-center justify-center gap-2">
            <Loader2 className="h-4 w-4 animate-spin" />
            <p className="text-sm text-muted-foreground">Loading notes...</p>
          </div>
        </Card>
      )}

      {/* Show empty state */}
      {!isLoading && displayResults.length === 0 && (
        <Card className="p-6">
          <p className="text-sm text-muted-foreground text-center">
            {isSearchMode ? 'No results found for your search' : 'No notes in this campaign yet'}
          </p>
        </Card>
      )}

      {/* Show results */}
      {displayResults.length > 0 && (
        <>
          <SearchResults results={displayResults} />
          
          {/* Show pagination info and load more button */}
          <Card className="p-4">
            <div className="flex items-center justify-between gap-4">
              <p className="text-xs text-muted-foreground">
                Showing {displayResults.length} of {totalCount} notes
              </p>
              {hasMore && (
                <Button
                  onClick={handleLoadMore}
                  disabled={isLoading}
                  variant="outline"
                  size="sm"
                  className="ml-auto"
                >
                  {isLoading ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin mr-2" />
                      Loading...
                    </>
                  ) : (
                    'Load More'
                  )}
                </Button>
              )}
            </div>
          </Card>
        </>
      )}
    </div>
  );
};

export default SearchPanel;
