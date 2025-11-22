import { Graph } from './graph';

export interface AssistantQueryRequest {
  query: string;
}

export interface AssistantResponse {
  responseType: 'text' | 'text_and_graph' | 'error' | 'clarification_needed' | 'out_of_scope';
  errorType?: string;
  textResponse: string;
  graphData?: Graph;
  sources: SourceReference[];
  executedActions: string[];
  debugInfo?: Record<string, any>;
}

export interface SourceReference {
  noteId: string;
  noteTitle: string;
}

