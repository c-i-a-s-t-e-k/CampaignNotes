/**
 * Search related types
 */

export interface SearchRequest {
  query: string;
  limit?: number;
}

export interface SearchResult {
  noteId: string;
  title: string;
  contentPreview: string;
  score: number;
}

