/**
 * Graph API functions
 */

import { Graph, Note, Artifact, RelationType, ArtifactPair } from '../types';
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

/**
 * Get all artifacts for a campaign
 */
export const getAllArtifacts = async (campaignUuid: string): Promise<Artifact[]> => {
  const response = await apiClient.get<Artifact[]>(
    `/campaigns/${campaignUuid}/graph/artifacts`
  );
  return response.data;
};

/**
 * Get all relation types for a campaign
 */
export const getAllRelationTypes = async (campaignUuid: string): Promise<RelationType[]> => {
  const response = await apiClient.get<RelationType[]>(
    `/campaigns/${campaignUuid}/graph/relations`
  );
  return response.data;
};

/**
 * Get all artifact pairs connected by a specific relationship type
 */
export const getArtifactPairsByRelation = async (
  campaignUuid: string,
  relationLabel: string
): Promise<ArtifactPair[]> => {
  const response = await apiClient.get<ArtifactPair[]>(
    `/campaigns/${campaignUuid}/graph/relations/${encodeURIComponent(relationLabel)}/pairs`
  );
  return response.data;
};
