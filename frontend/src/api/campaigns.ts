/**
 * Campaign API functions
 */

import { Campaign, CampaignCreateRequest } from '../types';
import apiClient from './client';

/**
 * Get all campaigns
 */
export const getAllCampaigns = async (): Promise<Campaign[]> => {
  const response = await apiClient.get<Campaign[]>('/campaigns');
  return response.data;
};

/**
 * Get a single campaign by UUID
 */
export const getCampaign = async (uuid: string): Promise<Campaign> => {
  const response = await apiClient.get<Campaign>(`/campaigns/${uuid}`);
  return response.data;
};

/**
 * Create a new campaign
 */
export const createCampaign = async (request: CampaignCreateRequest): Promise<Campaign> => {
  const response = await apiClient.post<Campaign>('/campaigns', request);
  return response.data;
};

/**
 * Delete a campaign (soft delete)
 */
export const deleteCampaign = async (uuid: string): Promise<void> => {
  await apiClient.delete(`/campaigns/${uuid}`);
};

/**
 * Get all deleted campaigns
 */
export const getDeletedCampaigns = async (): Promise<Campaign[]> => {
  const response = await apiClient.get<Campaign[]>('/campaigns/deleted/list');
  return response.data;
};

/**
 * Restore a deleted campaign
 */
export const restoreCampaign = async (uuid: string): Promise<Campaign> => {
  const response = await apiClient.post<Campaign>(`/campaigns/${uuid}/restore`);
  return response.data;
};

