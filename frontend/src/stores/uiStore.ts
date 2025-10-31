/**
 * UI store - manages UI state (panels, loading, etc.)
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { SearchResult } from '../types/search';

interface UIStore {
  // Panel states
  isCampaignListOpen: boolean;
  isSearchPanelOpen: boolean;
  isNoteEditorOpen: boolean;
  
  // Selected note for detail view
  selectedNoteId: string | null;
  
  // Search state
  searchQuery: string;
  searchResults: SearchResult[];
  
  // Actions
  toggleCampaignList: () => void;
  toggleSearchPanel: () => void;
  toggleNoteEditor: () => void;
  setCampaignListOpen: (open: boolean) => void;
  setSearchPanelOpen: (open: boolean) => void;
  setNoteEditorOpen: (open: boolean) => void;
  setSelectedNoteId: (noteId: string | null) => void;
  setSearchQuery: (query: string) => void;
  setSearchResults: (results: SearchResult[]) => void;
  clearSearch: () => void;
}

export const useUIStore = create<UIStore>()(
  persist(
    (set) => ({
      // Default states
      isCampaignListOpen: true,
      isSearchPanelOpen: true,
      isNoteEditorOpen: true,
      selectedNoteId: null,
      searchQuery: '',
      searchResults: [],
      
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
      setSearchQuery: (query) => set({ searchQuery: query }),
      setSearchResults: (results) => set({ searchResults: results }),
      clearSearch: () => set({ searchQuery: '', searchResults: [] }),
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

