/**
 * Graph API functions
 */

import { Graph, Note } from '../types';
import apiClient from './client';

/**
 * Get campaign graph (all nodes and edges)
 */
export const getCampaignGraph = async (campaignUuid: string): Promise<Graph> => {
  const response = await apiClient.get<Graph>(`/campaigns/${campaignUuid}/graph`);
  return response.data;
};

/**
 * Get all notes associated with an artifact
 */
export const getArtifactNotes = async (
  campaignUuid: string,
  artifactId: string
): Promise<Note[]> => {
  const response = await apiClient.get<Note[]>(
    `/campaigns/${campaignUuid}/graph/artifacts/${artifactId}/notes`
  );
  return response.data;
};
