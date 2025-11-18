import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getAllCampaigns, deleteCampaign, getDeletedCampaigns, restoreCampaign } from '../api/campaigns';
import { useCampaignStore } from '../stores';
import CampaignCreateForm from './CampaignCreateForm';
import { Button } from './ui/button';
import { Card } from './ui/card';
import { Plus, Loader2, Trash2, RotateCcw, ChevronDown, ChevronUp, AlertCircle } from 'lucide-react';

/**
 * Campaign list component.
 * Displays all active campaigns and allows selecting, creating, deleting, and restoring campaigns.
 */
const CampaignList: React.FC = () => {
  const [isCreating, setIsCreating] = useState(false);
  const [isDeletedExpanded, setIsDeletedExpanded] = useState(false);
  const [deleteConfirmUuid, setDeleteConfirmUuid] = useState<string | null>(null);
  const { selectedCampaign, selectCampaign } = useCampaignStore();
  const queryClient = useQueryClient();

  // Active campaigns query
  const { data: campaigns, isLoading, error } = useQuery({
    queryKey: ['campaigns'],
    queryFn: getAllCampaigns,
  });

  // Deleted campaigns query
  const { data: deletedCampaigns = [] } = useQuery({
    queryKey: ['campaigns-deleted'],
    queryFn: getDeletedCampaigns,
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: deleteCampaign,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['campaigns'] });
      queryClient.invalidateQueries({ queryKey: ['campaigns-deleted'] });
      setDeleteConfirmUuid(null);
    },
  });

  // Restore mutation
  const restoreMutation = useMutation({
    mutationFn: restoreCampaign,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['campaigns'] });
      queryClient.invalidateQueries({ queryKey: ['campaigns-deleted'] });
    },
  });

  const handleDeleteClick = (uuid: string, event: React.MouseEvent) => {
    event.stopPropagation();
    setDeleteConfirmUuid(uuid);
  };

  const handleConfirmDelete = (uuid: string) => {
    deleteMutation.mutate(uuid);
  };

  const handleRestoreClick = (uuid: string, event: React.MouseEvent) => {
    event.stopPropagation();
    restoreMutation.mutate(uuid);
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-4">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-4 text-sm text-destructive flex items-center gap-2">
        <AlertCircle className="h-4 w-4" />
        Error loading campaigns
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="p-4 border-b border-border">
        <div className="flex items-center justify-between mb-2">
          <h2 className="text-lg font-semibold">Campaigns</h2>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => setIsCreating(!isCreating)}
          >
            <Plus className="h-4 w-4" />
          </Button>
        </div>

        {isCreating && (
          <CampaignCreateForm onClose={() => setIsCreating(false)} />
        )}
      </div>

      {/* Active campaigns list */}
      <div className="flex-1 overflow-y-auto p-2 space-y-2">
        {campaigns?.length === 0 && deletedCampaigns.length === 0 && (
          <div className="text-sm text-muted-foreground p-4 text-center">
            No campaigns yet. Create one to get started.
          </div>
        )}

        {campaigns?.map((campaign) => (
          <Card
            key={campaign.uuid}
            className={`p-3 cursor-pointer transition-colors hover:bg-accent group ${
              selectedCampaign?.uuid === campaign.uuid
                ? 'bg-accent border-primary'
                : ''
            }`}
            onClick={() => selectCampaign(campaign)}
          >
            <div className="flex items-start justify-between gap-2">
              <div className="flex-1 min-w-0">
                <h3 className="font-medium text-sm">{campaign.name}</h3>
                {campaign.description && (
                  <p className="text-xs text-muted-foreground mt-1 line-clamp-2">
                    {campaign.description}
                  </p>
                )}
              </div>
              <Button
                size="sm"
                variant="ghost"
                className="h-6 w-6 p-0 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0"
                onClick={(e) => handleDeleteClick(campaign.uuid, e)}
                disabled={deleteConfirmUuid === campaign.uuid}
              >
                <Trash2 className="h-3.5 w-3.5 text-destructive" />
              </Button>
            </div>

            {/* Delete confirmation */}
            {deleteConfirmUuid === campaign.uuid && (
              <div className="mt-2 pt-2 border-t border-border/50 flex items-center gap-1">
                <span className="text-xs text-muted-foreground flex-1">
                  Remove from active list?
                </span>
                <Button
                  size="sm"
                  variant="ghost"
                  className="h-6 text-xs"
                  onClick={(e) => {
                    e.stopPropagation();
                    setDeleteConfirmUuid(null);
                  }}
                  disabled={deleteMutation.isPending}
                >
                  Cancel
                </Button>
                <Button
                  size="sm"
                  variant="destructive"
                  className="h-6 text-xs"
                  onClick={(e) => {
                    e.stopPropagation();
                    handleConfirmDelete(campaign.uuid);
                  }}
                  disabled={deleteMutation.isPending}
                >
                  {deleteMutation.isPending ? 'Deleting...' : 'Delete'}
                </Button>
              </div>
            )}
          </Card>
        ))}

        {/* Deleted campaigns section */}
        {deletedCampaigns.length > 0 && (
          <div className="pt-2 border-t border-border/50">
            <button
              onClick={() => setIsDeletedExpanded(!isDeletedExpanded)}
              className="w-full flex items-center justify-between gap-2 p-2 hover:bg-muted rounded text-sm transition-colors"
            >
              <span className="font-medium text-muted-foreground">
                Deleted Campaigns ({deletedCampaigns.length})
              </span>
              {isDeletedExpanded ? (
                <ChevronUp className="h-4 w-4" />
              ) : (
                <ChevronDown className="h-4 w-4" />
              )}
            </button>

            {isDeletedExpanded && (
              <div className="space-y-1 mt-1">
                {deletedCampaigns.map((campaign) => (
                  <Card
                    key={campaign.uuid}
                    className="p-2 bg-muted/50 border-muted/50 group"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex-1 min-w-0">
                        <h3 className="font-medium text-xs text-muted-foreground line-clamp-1">
                          {campaign.name}
                        </h3>
                      </div>
                      <Button
                        size="sm"
                        variant="ghost"
                        className="h-5 w-5 p-0 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0"
                        onClick={(e) => handleRestoreClick(campaign.uuid, e)}
                        disabled={restoreMutation.isPending}
                        title="Restore campaign"
                      >
                        <RotateCcw className="h-3 w-3 text-primary" />
                      </Button>
                    </div>
                  </Card>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default CampaignList;

