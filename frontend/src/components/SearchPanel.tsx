import React, { useState, useEffect } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { getAllNotes, searchNotes, getAllArtifacts, getAllRelationTypes, searchArtifacts, searchRelations } from '../api';
import { useCampaignStore, useUIStore, useGraphStore } from '../stores';
import { Note, SearchResult } from '../types';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Card } from './ui/card';
import { Search, Loader2, X, ChevronDown } from 'lucide-react';
import SearchResults from './SearchResults';
import toast from 'react-hot-toast';

/**
 * Search panel component for viewing all notes, artifacts, and relations.
 * Supports semantic search for each category.
 */
const SearchPanel: React.FC = () => {
  const { selectedCampaign } = useCampaignStore();
  const { searchQuery, searchResults, setSearchQuery, setSearchResults } = useUIStore();
  const { setSelectedNodeId, setFilteredRelationType } = useGraphStore();
  
  const [offset, setOffset] = useState(0);
  const [isSearchMode, setIsSearchMode] = useState(false);
  const LIMIT = 50;
  
  // Artifact search state
  const [artifactSearchQuery, setArtifactSearchQuery] = useState('');
  const [isArtifactSearchMode, setIsArtifactSearchMode] = useState(false);
  
  // Relation search state
  const [relationSearchQuery, setRelationSearchQuery] = useState('');
  const [isRelationSearchMode, setIsRelationSearchMode] = useState(false);
  const [isNotesCollapsed, setIsNotesCollapsed] = useState(false);
  const [isArtifactsCollapsed, setIsArtifactsCollapsed] = useState(false);
  const [isRelationsCollapsed, setIsRelationsCollapsed] = useState(false);

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
      return searchNotes(selectedCampaign.uuid, { query: query.trim(), limit: 3 });
    },
    onSuccess: (data) => {
      setSearchResults(data);
      if (data.length === 0) {
        toast('No note results found', { icon: '🔍' });
      }
    },
    onError: (error) => {
      toast.error('Search failed');
      console.error(error);
      setSearchResults([]);
    },
  });

  // Query for fetching all artifacts
  const { data: allArtifacts, isLoading: isLoadingArtifacts } = useQuery({
    queryKey: ['allArtifacts', selectedCampaign?.uuid],
    queryFn: () => {
      if (!selectedCampaign) {
        throw new Error('No campaign selected');
      }
      return getAllArtifacts(selectedCampaign.uuid);
    },
    enabled: !!selectedCampaign && !isArtifactSearchMode,
  });

  // Query for fetching all relation types
  const { data: allRelationTypes, isLoading: isLoadingRelations } = useQuery({
    queryKey: ['allRelationTypes', selectedCampaign?.uuid],
    queryFn: () => {
      if (!selectedCampaign) {
        throw new Error('No campaign selected');
      }
      return getAllRelationTypes(selectedCampaign.uuid);
    },
    enabled: !!selectedCampaign && !isRelationSearchMode,
  });

  // Mutation for searching artifacts
  const artifactSearchMutation = useMutation({
    mutationFn: (query: string) => {
      if (!selectedCampaign) {
        throw new Error('No campaign selected');
      }
      return searchArtifacts(selectedCampaign.uuid, { query: query.trim(), limit: 3 });
    },
    onSuccess: (data) => {
      if (data.length === 0) {
        toast('No artifact results found', { icon: '🔍' });
      }
    },
    onError: (error) => {
      toast.error('Artifact search failed');
      console.error(error);
    },
  });

  // Mutation for searching relations
  const relationSearchMutation = useMutation({
    mutationFn: (query: string) => {
      if (!selectedCampaign) {
        throw new Error('No campaign selected');
      }
      return searchRelations(selectedCampaign.uuid, { query: query.trim(), limit: 3 });
    },
    onSuccess: (data) => {
      if (data.length === 0) {
        toast('No relation results found', { icon: '🔍' });
      }
    },
    onError: (error) => {
      toast.error('Relation search failed');
      console.error(error);
    },
  });

  // Effect to reset all state when campaign changes
  useEffect(() => {
    setOffset(0);
    setIsSearchMode(false);
    setSearchQuery('');
    setSearchResults([]);
    setArtifactSearchQuery('');
    setIsArtifactSearchMode(false);
    setRelationSearchQuery('');
    setIsRelationSearchMode(false);
    setFilteredRelationType(null);
  }, [selectedCampaign?.uuid, setSearchQuery, setSearchResults, setFilteredRelationType]);

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

  const handleArtifactSearch = (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!selectedCampaign) {
      toast.error('Please select a campaign first');
      return;
    }

    if (!artifactSearchQuery.trim()) {
      setIsArtifactSearchMode(false);
      return;
    }

    setIsArtifactSearchMode(true);
    artifactSearchMutation.mutate(artifactSearchQuery.trim());
  };

  const handleClearArtifactSearch = () => {
    setArtifactSearchQuery('');
    setIsArtifactSearchMode(false);
  };

  const handleArtifactClick = (artifactId: string) => {
    setSelectedNodeId(artifactId);
  };

  const handleRelationSearch = (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!selectedCampaign) {
      toast.error('Please select a campaign first');
      return;
    }

    if (!relationSearchQuery.trim()) {
      setIsRelationSearchMode(false);
      return;
    }

    setIsRelationSearchMode(true);
    relationSearchMutation.mutate(relationSearchQuery.trim());
  };

  const handleClearRelationSearch = () => {
    setRelationSearchQuery('');
    setIsRelationSearchMode(false);
  };

  const handleRelationClick = (relationLabel: string) => {
    // Filter graph by relation type
    setFilteredRelationType(relationLabel);
    toast.success(`Filtering graph by: ${relationLabel}`);
  };

  const handleClearRelationFilter = () => {
    setFilteredRelationType(null);
    toast.success('Relation filter cleared');
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

  const mapNoteToSearchResult = (note: Note): SearchResult => ({
    noteId: note.id,
    title: note.title,
    contentPreview: note.content,
    score: 0,
  });

  const displayResults: SearchResult[] = isSearchMode
    ? searchResults || []
    : (allNotesData?.notes || []).map(mapNoteToSearchResult);
  const totalCount = isSearchMode ? searchResults.length : allNotesData?.totalCount || 0;
  const hasMore = isSearchMode ? false : allNotesData?.hasMore || false;
  const isLoading = isSearchMode ? searchMutation.isPending : isLoadingAll;

  const displayArtifacts = isArtifactSearchMode 
    ? (artifactSearchMutation.data || []) 
    : (allArtifacts || []);
    
  const displayRelations = isRelationSearchMode 
    ? (relationSearchMutation.data || []) 
    : (allRelationTypes || []);

  return (
    <div className="space-y-4 h-full overflow-y-auto pb-4">
      <Card className="p-4 space-y-4">
        <div className="flex items-center justify-between gap-2">
          <h3 className="text-lg font-semibold">
            {isSearchMode ? 'Note Search Results' : 'Notes'}
          </h3>
          <Button
            variant="ghost"
            size="icon"
            type="button"
            onClick={() => setIsNotesCollapsed((prev) => !prev)}
            aria-expanded={!isNotesCollapsed}
            aria-label={isNotesCollapsed ? 'Expand notes' : 'Collapse notes'}
          >
            <ChevronDown
              className={`h-4 w-4 transition-transform ${isNotesCollapsed ? '-rotate-90' : ''}`}
            />
          </Button>
        </div>
        {!isNotesCollapsed && (
          <>
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

            {/* Notes content */}
            {isLoading && !isSearchMode && (
              <div className="flex items-center justify-center gap-2 py-6">
                <Loader2 className="h-4 w-4 animate-spin" />
                <p className="text-sm text-muted-foreground">Loading notes...</p>
              </div>
            )}

            {!isLoading && displayResults.length === 0 && (
              <div className="py-6">
                <p className="text-sm text-muted-foreground text-center">
                  {isSearchMode ? 'No results found for your search' : 'No notes in this campaign yet'}
                </p>
              </div>
            )}

            {displayResults.length > 0 && (
              <>
                <SearchResults results={displayResults} />

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
              </>
            )}
          </>
        )}
      </Card>

      {/* Artifacts Section */}
      <Card className="p-4">
        <div className="flex items-center justify-between gap-2">
          <h3 className="text-lg font-semibold">
            {isArtifactSearchMode ? 'Artifact Search Results' : 'Artifacts'}
          </h3>
          <Button
            variant="ghost"
            size="icon"
            type="button"
            onClick={() => setIsArtifactsCollapsed((prev) => !prev)}
            aria-expanded={!isArtifactsCollapsed}
            aria-label={isArtifactsCollapsed ? 'Expand artifacts' : 'Collapse artifacts'}
          >
            <ChevronDown
              className={`h-4 w-4 transition-transform ${isArtifactsCollapsed ? '-rotate-90' : ''}`}
            />
          </Button>
        </div>

        {!isArtifactsCollapsed && (
          <>
            <form onSubmit={handleArtifactSearch} className="space-y-3 mt-4">
              <div className="flex gap-2">
                <Input
                  placeholder="Search artifacts..."
                  value={artifactSearchQuery}
                  onChange={(e) => setArtifactSearchQuery(e.target.value)}
                  disabled={artifactSearchMutation.isPending}
                />
                <Button
                  type="submit"
                  disabled={artifactSearchMutation.isPending}
                  title="Search artifacts"
                >
                  {artifactSearchMutation.isPending ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <Search className="h-4 w-4" />
                  )}
                </Button>
              </div>
              {isArtifactSearchMode && (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={handleClearArtifactSearch}
                  disabled={artifactSearchMutation.isPending}
                  className="w-full"
                >
                  Clear Search
                </Button>
              )}
            </form>

            {/* Artifacts List */}
            {isLoadingArtifacts && !isArtifactSearchMode && (
              <div className="mt-4 flex items-center justify-center">
                <Loader2 className="h-4 w-4 animate-spin" />
              </div>
            )}

            {displayArtifacts.length > 0 && (
              <div className="mt-4 space-y-2">
                {displayArtifacts.map((artifact) => (
                  <Card
                    key={artifact.id}
                    className="p-3 cursor-pointer hover:bg-accent transition-colors"
                    onClick={() => handleArtifactClick(artifact.id)}
                  >
                    <div className="flex items-start justify-between">
                      <div className="flex-1">
                        <h4 className="font-medium text-sm">{artifact.name}</h4>
                        <p className="text-xs text-muted-foreground mt-1">
                          Type: {artifact.type}
                        </p>
                        {artifact.description && (
                          <p className="text-xs text-muted-foreground mt-1 line-clamp-2">
                            {artifact.description}
                          </p>
                        )}
                      </div>
                    </div>
                  </Card>
                ))}
              </div>
            )}

            {displayArtifacts.length === 0 && !isLoadingArtifacts && (
              <p className="text-sm text-muted-foreground text-center mt-4">
                {isArtifactSearchMode ? 'No artifacts found' : 'No artifacts in this campaign'}
              </p>
            )}

            {isArtifactSearchMode && displayArtifacts.length > 0 && (
              <p className="text-xs text-muted-foreground text-center mt-2">
                Showing top {displayArtifacts.length} results
              </p>
            )}
          </>
        )}
      </Card>

      {/* Relations Section */}
      <Card className="p-4">
        <div className="flex items-center justify-between gap-2">
          <h3 className="text-lg font-semibold">
            {isRelationSearchMode ? 'Relation Search Results' : 'Relations'}
          </h3>
          <Button
            variant="ghost"
            size="icon"
            type="button"
            onClick={() => setIsRelationsCollapsed((prev) => !prev)}
            aria-expanded={!isRelationsCollapsed}
            aria-label={isRelationsCollapsed ? 'Expand relations' : 'Collapse relations'}
          >
            <ChevronDown
              className={`h-4 w-4 transition-transform ${isRelationsCollapsed ? '-rotate-90' : ''}`}
            />
          </Button>
        </div>

        {!isRelationsCollapsed && (
          <>
            <form onSubmit={handleRelationSearch} className="space-y-3 mt-4">
              <div className="flex gap-2">
                <Input
                  placeholder="Search relations..."
                  value={relationSearchQuery}
                  onChange={(e) => setRelationSearchQuery(e.target.value)}
                  disabled={relationSearchMutation.isPending}
                />
                <Button
                  type="submit"
                  disabled={relationSearchMutation.isPending}
                  title="Search relations"
                >
                  {relationSearchMutation.isPending ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <Search className="h-4 w-4" />
                  )}
                </Button>
              </div>
              {isRelationSearchMode && (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={handleClearRelationSearch}
                  disabled={relationSearchMutation.isPending}
                  className="w-full"
                >
                  Clear Search
                </Button>
              )}
            </form>

            {/* Clear Filter Button */}
            <Button
              variant="outline"
              size="sm"
              onClick={handleClearRelationFilter}
              className="w-full mt-3"
            >
              <X className="h-4 w-4 mr-2" />
              Clear Graph Filter
            </Button>

            {/* Relations List */}
            {isLoadingRelations && !isRelationSearchMode && (
              <div className="mt-4 flex items-center justify-center">
                <Loader2 className="h-4 w-4 animate-spin" />
              </div>
            )}

            {displayRelations.length > 0 && (
              <div className="mt-4 space-y-2">
                {displayRelations.map((relation) => (
                  <Card
                    key={relation.label}
                    className="p-3 cursor-pointer hover:bg-accent transition-colors"
                    onClick={() => handleRelationClick(relation.label)}
                  >
                    <div className="flex items-center justify-between">
                      <h4 className="font-medium text-sm">{relation.label}</h4>
                    </div>
                  </Card>
                ))}
              </div>
            )}

            {displayRelations.length === 0 && !isLoadingRelations && (
              <p className="text-sm text-muted-foreground text-center mt-4">
                {isRelationSearchMode ? 'No relations found' : 'No relations in this campaign'}
              </p>
            )}

            {isRelationSearchMode && displayRelations.length > 0 && (
              <p className="text-xs text-muted-foreground text-center mt-2">
                Showing top {displayRelations.length} results
              </p>
            )}
          </>
        )}
      </Card>
    </div>
  );
};

export default SearchPanel;
