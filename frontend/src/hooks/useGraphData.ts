/**
 * Hook for fetching and managing graph data.
 * This is a simplified version for MVP - full Neo4j NVL integration can be added later.
 */

import { useQuery } from '@tanstack/react-query';
import { getCampaignGraph } from '../api';
import { Graph } from '../types';

export const useGraphData = (campaignUuid: string | undefined) => {
  return useQuery({
    queryKey: ['graph', campaignUuid],
    queryFn: () => {
      if (!campaignUuid) {
        throw new Error('No campaign UUID provided');
      }
      return getCampaignGraph(campaignUuid);
    },
    enabled: !!campaignUuid,
  });
};

