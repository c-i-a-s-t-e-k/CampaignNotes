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
  title: string;
  success: boolean;
  message: string;
  artifactCount: number;
  relationshipCount: number;
  artifacts: ArtifactSummary[];
}

