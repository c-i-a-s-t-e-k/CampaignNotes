import { apiClient } from './client';
import { AssistantQueryRequest, AssistantResponse } from '../types/assistant';

/**
 * Submit an assistant query for a campaign.
 * 
 * @param campaignUuid UUID of the campaign to query
 * @param request the query request
 * @returns AssistantResponse with the result
 */
export const submitAssistantQuery = async (
  campaignUuid: string,
  request: AssistantQueryRequest
): Promise<AssistantResponse> => {
  const { data } = await apiClient.post<AssistantResponse>(
    `/campaigns/${campaignUuid}/assistant/query`,
    request
  );
  return data;
};

