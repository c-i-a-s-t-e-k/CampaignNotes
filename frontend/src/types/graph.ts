/**
 * Graph related types
 */

export interface Node {
  id: string;
  name: string;
  type: string;
  description?: string;
  campaignUuid: string;
  noteId?: string;
}

export interface Edge {
  id: string;
  source: string;
  target: string;
  label: string;
  description?: string;
  reasoning?: string;
}

export interface Graph {
  nodes: Node[];
  edges: Edge[];
}

