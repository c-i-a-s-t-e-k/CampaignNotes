/**
 * Note related types
 */

export interface Note {
  id: string;
  campaignUuid: string;
  title: string;
  content: string;
  createdAt: string;
  updatedAt: string;
  wordCount: number;
}

export interface NoteCreateRequest {
  title: string;
  content: string;
}

export interface ArtifactSummary {
  name: string;
  type: string;
  description: string;
}

export interface NoteCreateResponse {
  noteId: string;
  campaignUuid: string;
  title: string;
  success: boolean;
  message: string;
  artifactCount: number;
  relationshipCount: number;
  artifacts: ArtifactSummary[];
  deduplicationResult?: DeduplicationResult;
  requiresUserConfirmation: boolean;
  artifactMergeProposals: MergeProposal[];
  mergedArtifactCount: number;
  mergedRelationshipCount: number;
}

export interface MergeProposal {
  newItemId: string;
  newItemName: string;
  existingItemId: string;
  existingItemName: string;
  itemType: 'artifact' | 'relationship';
  confidence: number;  // 0-100
  reasoning: string;
  autoMerge: boolean;
  approved?: boolean;  // for frontend
}

export interface DeduplicationResult {
  artifactDecisions: Record<string, DeduplicationDecision>;
  relationshipDecisions: Record<string, DeduplicationDecision>;
  newArtifacts: ArtifactSummary[];
  newRelationships: RelationshipSummary[];
  metrics: DeduplicationMetrics;
}

export interface DeduplicationDecision {
  isSame: boolean;
  confidence: number;
  reasoning: string;
  candidateId: string;
  candidateName: string;
}

export interface DeduplicationMetrics {
  totalCandidates: number;
  totalDecisions: number;
  autoMergeCount: number;
  requiresConfirmationCount: number;
}

export interface RelationshipSummary {
  id: string;
  label: string;
  sourceArtifactName: string;
  targetArtifactName: string;
  description?: string;
}

export interface NoteConfirmationRequest {
  campaignUuid: string;
  noteId: string;
  approvedMergeProposals: MergeProposal[];
}

