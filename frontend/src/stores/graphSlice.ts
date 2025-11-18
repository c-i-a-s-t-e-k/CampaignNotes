/**
 * Graph store - manages graph visualization state
 */

import { create } from 'zustand';

interface GraphStore {
  selectedNodeId: string | null;
  setSelectedNodeId: (nodeId: string | null) => void;
}

export const useGraphStore = create<GraphStore>((set) => ({
  selectedNodeId: null,
  
  setSelectedNodeId: (nodeId) => {
    console.log('[GraphStore] Selecting node:', nodeId);
    set({ selectedNodeId: nodeId });
  },
}));

