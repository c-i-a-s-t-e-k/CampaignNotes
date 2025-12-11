/**
 * Search API functions
 */

import { SearchRequest, SearchResult, Artifact, RelationType } from '../types';
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

/**
 * Perform semantic search for artifacts (top 3 results)
 */
export const searchArtifacts = async (
  campaignUuid: string,
  request: SearchRequest
): Promise<Artifact[]> => {
  const response = await apiClient.post<Artifact[]>(
    `/campaigns/${campaignUuid}/search/artifacts`,
    request
  );
  return response.data;
};

/**
 * Perform semantic search for relations (top 3 results)
 */
export const searchRelations = async (
  campaignUuid: string,
  request: SearchRequest
): Promise<RelationType[]> => {
  const response = await apiClient.post<RelationType[]>(
    `/campaigns/${campaignUuid}/search/relations`,
    request
  );
  return response.data;
};

