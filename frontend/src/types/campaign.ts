/**
 * Campaign related types
 */

export interface Campaign {
  uuid: string;
  name: string;
  description?: string;
  createdAt: number;
  updatedAt: number;
  isActive: boolean;
}

export interface CampaignCreateRequest {
  name: string;
  description?: string;
}

