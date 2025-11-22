/**
 * Campaign store - manages selected campaign state
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { Campaign } from '../types';
import { useAssistantStore } from './assistantStore';
import { useGraphStore } from './graphSlice';

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
        
        // Clear assistant state when campaign changes
        useAssistantStore.getState().clearResponse();
        useGraphStore.getState().clearGraphData();
      },
      
      clearCampaign: () => {
        console.log('[CampaignStore] Clearing campaign');
        set({ selectedCampaign: null });
        
        // Clear assistant state when campaign is cleared
        useAssistantStore.getState().clearResponse();
        useGraphStore.getState().clearGraphData();
      },
    }),
    {
      name: 'campaign-storage',
      partialize: (state) => ({ selectedCampaign: state.selectedCampaign }),
    }
  )
);

