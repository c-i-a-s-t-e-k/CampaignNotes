/**
 * Graph store - manages graph visualization state
 */

import { create } from 'zustand';

interface GraphStore {
  selectedNodeId: string | null;
  setSelectedNodeId: (nodeId: string | null) => void;
  
  // Relation filter state
  filteredRelationType: string | null;
  setFilteredRelationType: (relationType: string | null) => void;
}

export const useGraphStore = create<GraphStore>((set) => ({
  selectedNodeId: null,
  
  setSelectedNodeId: (nodeId) => {
    console.log('[GraphStore] Selecting node:', nodeId);
    set({ selectedNodeId: nodeId });
  },
  
  // Relation filter
  filteredRelationType: null,
  
  setFilteredRelationType: (relationType) => {
    console.log('[GraphStore] Filtering by relation type:', relationType);
    set({ filteredRelationType: relationType });
  },
}));

