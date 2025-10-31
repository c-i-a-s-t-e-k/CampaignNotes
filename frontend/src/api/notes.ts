/**
 * Note API functions
 */

import { Note, NoteCreateRequest, NoteCreateResponse } from '../types';
import apiClient from './client';

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

