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
 * Get campaign graph filtered by a specific note
 */
export const getNoteGraph = async (
  campaignUuid: string,
  noteId: string
): Promise<Graph> => {
  const response = await apiClient.get<Graph>(
    `/campaigns/${campaignUuid}/graph/notes/${noteId}`
  );
  return response.data;
};

/**
 * Get neighbors (directly connected artifacts) for a specific artifact
 * Used for graph expansion on double click
 */
export const getArtifactNeighbors = async (
  campaignUuid: string,
  artifactId: string
): Promise<Graph> => {
  const response = await apiClient.get<Graph>(
    `/campaigns/${campaignUuid}/graph/artifacts/${artifactId}/neighbors`
  );
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
