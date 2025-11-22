/**
 * Assistant store - manages assistant query state
 */

import { create } from 'zustand';
import { AssistantResponse } from '../types/assistant';

interface AssistantStore {
  currentQuery: string;
  currentResponse: AssistantResponse | null;
  
  setCurrentQuery: (query: string) => void;
  setResponse: (response: AssistantResponse) => void;
  clearResponse: () => void;
}

export const useAssistantStore = create<AssistantStore>((set) => ({
  currentQuery: '',
  currentResponse: null,
  
  setCurrentQuery: (query) => set({ currentQuery: query }),
  setResponse: (response) => set({ currentResponse: response }),
  clearResponse: () => set({ currentResponse: null, currentQuery: '' }),
}));

