/**
 * Search API functions
 */

import { SearchRequest, SearchResult } from '../types';
import apiClient from './client';

/**
 * Perform semantic search in a campaign
 */
export const searchNotes = async (
  campaignUuid: string,
  request: SearchRequest
): Promise<SearchResult[]> => {
  const response = await apiClient.post<SearchResult[]>(
    `/campaigns/${campaignUuid}/search`,
    request
  );
  return response.data;
};

