/**
 * Campaign store - manages selected campaign state
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { Campaign } from '../types';

interface CampaignStore {
  selectedCampaign: Campaign | null;
  selectCampaign: (campaign: Campaign) => void;
  clearCampaign: () => void;
}

export const useCampaignStore = create<CampaignStore>()(
  persist(
    (set) => ({
      selectedCampaign: null,
      
      selectCampaign: (campaign) => {
        console.log('[CampaignStore] Selecting campaign:', campaign.uuid);
        set({ selectedCampaign: campaign });
      },
      
      clearCampaign: () => {
        console.log('[CampaignStore] Clearing campaign');
        set({ selectedCampaign: null });
      },
    }),
    {
      name: 'campaign-storage',
      partialize: (state) => ({ selectedCampaign: state.selectedCampaign }),
    }
  )
);

