import React, { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { submitAssistantQuery } from '../api';
import { useAssistantStore, useCampaignStore, useGraphStore } from '../stores';
import { Input } from './ui/input';
import { Button } from './ui/button';
import { Card } from './ui/card';
import { Loader2, Send, Sparkles } from 'lucide-react';
import AssistantResponseComponent from './AssistantResponseComponent';
import toast from 'react-hot-toast';

/**
 * Main Assistant Panel component.
 * Replaces SearchPanel with natural language query capabilities.
 */
const AssistantPanel: React.FC = () => {
  const { selectedCampaign } = useCampaignStore();
  const { currentQuery, currentResponse, setCurrentQuery, setResponse } = useAssistantStore();
  const { setGraphData } = useGraphStore();
  const [localQuery, setLocalQuery] = useState('');

  const queryMutation = useMutation({
    mutationFn: (query: string) => {
      if (!selectedCampaign) {
        throw new Error('No campaign selected');
      }
      return submitAssistantQuery(selectedCampaign.uuid, { query });
    },
    onSuccess: (response) => {
      setCurrentQuery(localQuery);
      setResponse(response);
      
      // Delegate graph rendering if present
      if (response.graphData) {
        setGraphData(response.graphData);
      }
      
      if (response.responseType === 'error') {
        toast.error('An error occurred while processing your query');
      }
    },
    onError: (error) => {
      toast.error('Failed to communicate with the server');
      console.error('Assistant query error:', error);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!selectedCampaign) {
      toast.error('Please select a campaign first');
      return;
    }

    if (!localQuery.trim()) {
      toast.error('Please enter a question');
      return;
    }

    queryMutation.mutate(localQuery.trim());
  };

  if (!selectedCampaign) {
    return (
      <Card className="p-6">
        <div className="flex items-center justify-center gap-2 text-muted-foreground">
          <Sparkles className="h-5 w-5" />
          <p className="text-sm text-center">
            Select a campaign to ask questions
          </p>
        </div>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <Card className="p-4">
        <div className="flex items-center gap-2 mb-4">
          <Sparkles className="h-5 w-5 text-primary" />
          <h3 className="text-lg font-semibold">Campaign Assistant</h3>
        </div>
        
        <form onSubmit={handleSubmit} className="space-y-3">
          <div className="flex gap-2">
            <Input
              placeholder="Ask about characters, locations, events..."
              value={localQuery}
              onChange={(e) => setLocalQuery(e.target.value)}
              disabled={queryMutation.isPending}
              className="flex-1"
            />
            <Button
              type="submit"
              disabled={queryMutation.isPending || !localQuery.trim()}
              size="icon"
            >
              {queryMutation.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Send className="h-4 w-4" />
              )}
            </Button>
          </div>
          
          {currentQuery && !queryMutation.isPending && (
            <p className="text-xs text-muted-foreground">
              Last query: "{currentQuery}"
            </p>
          )}
        </form>
      </Card>

      {queryMutation.isPending && (
        <Card className="p-4">
          <div className="flex items-center gap-3">
            <Loader2 className="h-5 w-5 animate-spin text-primary" />
            <div className="flex-1">
              <p className="text-sm font-medium">Processing your question...</p>
              <p className="text-xs text-muted-foreground">
                This may take a few moments
              </p>
            </div>
          </div>
        </Card>
      )}

      {currentResponse && !queryMutation.isPending && (
        <AssistantResponseComponent response={currentResponse} />
      )}
    </div>
  );
};

export default AssistantPanel;

