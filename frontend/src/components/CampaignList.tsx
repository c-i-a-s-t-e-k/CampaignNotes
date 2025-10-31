import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getAllCampaigns } from '../api';
import { useCampaignStore } from '../stores';
import CampaignCreateForm from './CampaignCreateForm';
import { Button } from './ui/button';
import { Card } from './ui/card';
import { Plus, Loader2 } from 'lucide-react';

/**
 * Campaign list component.
 * Displays all campaigns and allows selecting and creating campaigns.
 */
const CampaignList: React.FC = () => {
  const [isCreating, setIsCreating] = useState(false);
  const { selectedCampaign, selectCampaign } = useCampaignStore();

  const { data: campaigns, isLoading, error } = useQuery({
    queryKey: ['campaigns'],
    queryFn: getAllCampaigns,
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-4">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-4 text-sm text-destructive">
        Error loading campaigns
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full">
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

      <div className="flex-1 overflow-y-auto p-2 space-y-2">
        {campaigns?.length === 0 && (
          <div className="text-sm text-muted-foreground p-4 text-center">
            No campaigns yet. Create one to get started.
          </div>
        )}

        {campaigns?.map((campaign) => (
          <Card
            key={campaign.uuid}
            className={`p-3 cursor-pointer transition-colors hover:bg-accent ${
              selectedCampaign?.uuid === campaign.uuid
                ? 'bg-accent border-primary'
                : ''
            }`}
            onClick={() => selectCampaign(campaign)}
          >
            <h3 className="font-medium text-sm">{campaign.name}</h3>
            {campaign.description && (
              <p className="text-xs text-muted-foreground mt-1 line-clamp-2">
                {campaign.description}
              </p>
            )}
          </Card>
        ))}
      </div>
    </div>
  );
};

export default CampaignList;

