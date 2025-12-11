/**
 * Graph related types
 */

export interface Node {
  id: string;
  name: string;
  type: string;
  description?: string;
  campaignUuid: string;
  noteIds?: string[];
}

export interface Edge {
  id: string;
  source: string;
  target: string;
  label: string;
  description?: string;
  reasoning?: string;
  noteIds?: string[];
}

export interface Graph {
  nodes: Node[];
  edges: Edge[];
}

export interface Artifact {
  id: string;
  name: string;
  type: string;
  description?: string;
}

export interface RelationType {
  label: string;
  count: number;
}

export interface ArtifactPair {
  source: Artifact;
  target: Artifact;
  relationshipId: string;
  label: string;
  description?: string;
}

