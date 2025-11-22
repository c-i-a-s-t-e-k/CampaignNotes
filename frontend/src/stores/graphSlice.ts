/**
 * Graph store - manages graph visualization state
 */

import { create } from 'zustand';
import { Graph } from '../types/graph';

interface GraphStore {
  selectedNodeId: string | null;
  assistantGraphData: Graph | null;
  
  setSelectedNodeId: (nodeId: string | null) => void;
  setGraphData: (graph: Graph | null) => void;
  clearGraphData: () => void;
}

export const useGraphStore = create<GraphStore>((set) => ({
  selectedNodeId: null,
  assistantGraphData: null,
  
  setSelectedNodeId: (nodeId) => {
    console.log('[GraphStore] Selecting node:', nodeId);
    set({ selectedNodeId: nodeId });
  },
  
  setGraphData: (graph) => {
    console.log('[GraphStore] Setting assistant graph data:', graph ? `${graph.nodes.length} nodes` : 'null');
    set({ assistantGraphData: graph });
  },
  
  clearGraphData: () => {
    console.log('[GraphStore] Clearing assistant graph data');
    set({ assistantGraphData: null });
  },
}));

