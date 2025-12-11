/**
 * Note API functions
 */

import { Note, NoteCreateRequest, NoteCreateResponse, NoteConfirmationRequest, NoteProcessingStatus, NoteListResponse } from '../types';
import apiClient from './client';

/**
 * Get all notes for a campaign with pagination support
 */
export const getAllNotes = async (
  campaignUuid: string,
  limit: number = 50,
  offset: number = 0
): Promise<NoteListResponse> => {
  const response = await apiClient.get<NoteListResponse>(
    `/campaigns/${campaignUuid}/notes`,
    {
      params: { limit, offset }
    }
  );
  return response.data;
};

/**
 * Create a new note in a campaign
 */
export const createNote = async (
  campaignUuid: string,
  request: NoteCreateRequest
): Promise<NoteCreateResponse> => {
  const response = await apiClient.post<NoteCreateResponse>(
    `/campaigns/${campaignUuid}/notes`,
    request
  );
  return response.data;
};

/**
 * Get a note by ID
 */
export const getNote = async (campaignUuid: string, noteId: string): Promise<Note> => {
  const response = await apiClient.get<Note>(`/campaigns/${campaignUuid}/notes/${noteId}`);
  return response.data;
};

/**
 * Get the processing status of a note being created asynchronously
 */
export const getNoteStatus = async (
  campaignUuid: string,
  noteId: string
): Promise<NoteProcessingStatus> => {
  const response = await apiClient.get<NoteProcessingStatus>(
    `/campaigns/${campaignUuid}/notes/${noteId}/status`
  );
  return response.data;
};

/**
 * Confirm deduplication decisions for a note
 */
export const confirmDeduplication = async (
  campaignUuid: string,
  noteId: string,
  request: NoteConfirmationRequest
): Promise<NoteCreateResponse> => {
  const response = await apiClient.post<NoteCreateResponse>(
    `/campaigns/${campaignUuid}/notes/${noteId}/confirm-deduplication`,
    request
  );
  return response.data;
};

