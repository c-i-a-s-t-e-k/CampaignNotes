/**
 * Hook for fetching and managing graph data.
 * Supports both full campaign graph and filtered graph by note.
 */

import { useQuery } from '@tanstack/react-query';
import { getCampaignGraph, getNoteGraph } from '../api';
import { Graph } from '../types';

export const useGraphData = (
  campaignUuid: string | undefined,
  noteId?: string | null
) => {
  return useQuery({
    queryKey: ['graph', campaignUuid, noteId],
    queryFn: () => {
      if (!campaignUuid) {
        throw new Error('No campaign UUID provided');
      }
      
      // If noteId is provided, fetch filtered graph
      if (noteId) {
        return getNoteGraph(campaignUuid, noteId);
      }
      
      // Otherwise fetch full campaign graph
      return getCampaignGraph(campaignUuid);
    },
    enabled: !!campaignUuid,
  });
};

