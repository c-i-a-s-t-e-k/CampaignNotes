/**
 * UI store - manages UI state (panels, loading, etc.)
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface UIStore {
  // Panel states
  isCampaignListOpen: boolean;
  isSearchPanelOpen: boolean;
  isNoteEditorOpen: boolean;
  
  // Selected note for detail view
  selectedNoteId: string | null;
  
  // Actions
  toggleCampaignList: () => void;
  toggleSearchPanel: () => void;
  toggleNoteEditor: () => void;
  setCampaignListOpen: (open: boolean) => void;
  setSearchPanelOpen: (open: boolean) => void;
  setNoteEditorOpen: (open: boolean) => void;
  setSelectedNoteId: (noteId: string | null) => void;
}

export const useUIStore = create<UIStore>()(
  persist(
    (set) => ({
      // Default states
      isCampaignListOpen: true,
      isSearchPanelOpen: true,
      isNoteEditorOpen: true,
      selectedNoteId: null,
      
      // Toggle actions
      toggleCampaignList: () =>
        set((state) => ({ isCampaignListOpen: !state.isCampaignListOpen })),
      
      toggleSearchPanel: () =>
        set((state) => ({ isSearchPanelOpen: !state.isSearchPanelOpen })),
      
      toggleNoteEditor: () =>
        set((state) => ({ isNoteEditorOpen: !state.isNoteEditorOpen })),
      
      // Set actions
      setCampaignListOpen: (open) => set({ isCampaignListOpen: open }),
      setSearchPanelOpen: (open) => set({ isSearchPanelOpen: open }),
      setNoteEditorOpen: (open) => set({ isNoteEditorOpen: open }),
      setSelectedNoteId: (noteId) => set({ selectedNoteId: noteId }),
    }),
    {
      name: 'ui-storage',
      partialize: (state) => ({
        isCampaignListOpen: state.isCampaignListOpen,
        isSearchPanelOpen: state.isSearchPanelOpen,
        isNoteEditorOpen: state.isNoteEditorOpen,
      }),
    }
  )
);

