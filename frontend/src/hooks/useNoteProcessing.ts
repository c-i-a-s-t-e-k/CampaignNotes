/**
 * Hook for polling note processing status
 */

import { useQuery } from '@tanstack/react-query';
import { getNoteStatus } from '../api/notes';
import { NoteProcessingStatus } from '../types';

/**
 * Custom hook to poll for note processing status
 * 
 * Automatically stops polling when processing is complete or failed
 * Polls every 2 seconds while processing
 * 
 * @param campaignUuid UUID of the campaign
 * @param noteId ID of the note being processed
 * @returns Query result with status data and loading states
 */
export const useNoteProcessing = (
  campaignUuid: string | undefined,
  noteId: string | undefined
) => {
  return useQuery<NoteProcessingStatus>({
    queryKey: ['noteStatus', campaignUuid, noteId],
    queryFn: () => getNoteStatus(campaignUuid!, noteId!),
    enabled: !!campaignUuid && !!noteId,
    refetchInterval: (query) => {
      // Access the actual data from query.state.data
      const data = query.state.data;
      // Stop polling when processing is complete or failed
      if (data?.status === 'completed' || data?.status === 'failed') {
        return false;
      }
      // Poll every 2 seconds while processing
      return 2000;
    },
    staleTime: 0, // Always fresh
    retry: (failureCount) => {
      // Retry up to 3 times on network errors, but don't retry on 404
      return failureCount < 3;
    },
  });
};

