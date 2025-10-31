/**
 * Graph API functions
 */

import { Graph } from '../types';
import apiClient from './client';

/**
 * Get campaign graph (all nodes and edges)
 */
export const getCampaignGraph = async (campaignUuid: string): Promise<Graph> => {
  const response = await apiClient.get<Graph>(`/campaigns/${campaignUuid}/graph`);
  return response.data;
};

