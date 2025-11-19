import React, { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createNote } from '../api';
import { NoteCreateRequest, NoteCreateResponse } from '../types';
import { useCampaignStore } from '../stores';
import { useNoteProcessing } from '../hooks/useNoteProcessing';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Textarea } from './ui/textarea';
import { Card } from './ui/card';
import toast from 'react-hot-toast';
import NoteCreateConfirmDialog from './NoteCreateConfirmDialog';
import DeduplicationModal from './DeduplicationModal';
import NoteProcessingProgress from './NoteProcessingProgress';

/**
 * Note editor component for creating new notes.
 */
const NoteEditor: React.FC = () => {
  const { selectedCampaign } = useCampaignStore();
  const queryClient = useQueryClient();
  const [confirmDialogData, setConfirmDialogData] = useState<NoteCreateResponse | null>(null);
  const [deduplicationModalData, setDeduplicationModalData] = useState<NoteCreateResponse | null>(null);
  const [processingNoteId, setProcessingNoteId] = useState<string | null>(null);

  // Hook for polling note processing status
  const { data: processingStatus, isLoading: isStatusLoading } = useNoteProcessing(
    selectedCampaign?.uuid,
    processingNoteId || undefined
  );

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors },
  } = useForm<NoteCreateRequest>();

  const content = watch('content', '');
  const wordCount = content ? content.trim().split(/\s+/).length : 0;

  const createMutation = useMutation({
    mutationFn: (data: NoteCreateRequest) => {
      if (!selectedCampaign) {
        throw new Error('No campaign selected');
      }
      return createNote(selectedCampaign.uuid, data);
    },
    onSuccess: (data) => {
      // Check if async processing is starting (status: "processing")
      if (data.success && !data.artifactCount && !data.relationshipCount) {
        // This is the initial 202 Accepted response, start polling
        setProcessingNoteId(data.noteId);
        toast.success('Note is being processed...');
      } else if (data.requiresUserConfirmation || (data.artifactMergeProposals && data.artifactMergeProposals.length > 0)) {
        // Show DeduplicationModal instead of ConfirmDialog
        setDeduplicationModalData(data);
      } else {
        // No deduplication needed or all auto-merged - show ConfirmDialog
        toast.success('Note created successfully!');
        setConfirmDialogData(data);
      }
      queryClient.invalidateQueries({ queryKey: ['graph', selectedCampaign?.uuid] });
      reset();
    },
    onError: (error: any) => {
      const message = error.response?.data?.message || 'Failed to create note';
      toast.error(message);
      console.error(error);
    },
  });

  // Handle processing status changes
  useEffect(() => {
    if (processingStatus?.status === 'completed' && processingStatus.result) {
      setProcessingNoteId(null);
      const result = processingStatus.result;
      
      if (result.requiresUserConfirmation || (result.artifactMergeProposals && result.artifactMergeProposals.length > 0)) {
        // Show DeduplicationModal
        setDeduplicationModalData(result);
      } else {
        // No deduplication needed or all auto-merged - show ConfirmDialog
        toast.success('Note created successfully!');
        setConfirmDialogData(result);
      }
    } else if (processingStatus?.status === 'failed') {
      setProcessingNoteId(null);
      toast.error(processingStatus.errorMessage || 'Note processing failed');
    }
  }, [processingStatus?.status, processingStatus?.result, processingStatus?.errorMessage]);

  // Handle deduplication confirmation
  const handleDeduplicationConfirmed = (finalData: NoteCreateResponse) => {
    setDeduplicationModalData(null);
    setConfirmDialogData(finalData);
    queryClient.invalidateQueries({ queryKey: ['graph', selectedCampaign?.uuid] });
  };

  // Handle deduplication cancellation
  const handleDeduplicationCancel = () => {
    setDeduplicationModalData(null);
    toast.error('Deduplication cancelled. Note was not saved.');
  };

  const onSubmit = (data: NoteCreateRequest) => {
    if (!selectedCampaign) {
      toast.error('Please select a campaign first');
      return;
    }

    if (wordCount > 500) {
      toast.error(`Content exceeds 500 words (${wordCount} words)`);
      return;
    }

    createMutation.mutate(data);
  };

  if (!selectedCampaign) {
    return (
      <Card className="p-6">
        <p className="text-sm text-muted-foreground text-center">
          Select a campaign to add notes
        </p>
      </Card>
    );
  }

  // Show processing progress while async processing is happening
  if (processingNoteId && processingStatus) {
    return (
      <NoteProcessingProgress
        status={processingStatus}
        onComplete={(result) => {
          if (result.requiresUserConfirmation || (result.artifactMergeProposals && result.artifactMergeProposals.length > 0)) {
            setDeduplicationModalData(result);
          } else {
            setConfirmDialogData(result);
          }
        }}
        onError={(errorMessage) => {
          setProcessingNoteId(null);
          toast.error(errorMessage);
        }}
      />
    );
  }

  return (
    <>
      <Card className="p-4">
        <h3 className="text-lg font-semibold mb-4">Add Note</h3>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <Input
              placeholder="Note title"
              {...register('title', {
                required: 'Title is required',
                minLength: { value: 1, message: 'Title is required' },
                maxLength: { value: 500, message: 'Title too long' },
              })}
            />
            {errors.title && (
              <p className="text-xs text-destructive mt-1">{errors.title.message}</p>
            )}
          </div>

          <div>
            <Textarea
              placeholder="Note content (max 500 words)"
              rows={8}
              {...register('content', {
                required: 'Content is required',
              })}
            />
            <div className="flex justify-between items-center mt-1">
              <div>
                {errors.content && (
                  <p className="text-xs text-destructive">{errors.content.message}</p>
                )}
              </div>
              <p
                className={`text-xs ${
                  wordCount > 500 ? 'text-destructive' : 'text-muted-foreground'
                }`}
              >
                {wordCount} / 500 words
              </p>
            </div>
          </div>

          <Button
            type="submit"
            className="w-full"
            disabled={createMutation.isPending || wordCount > 500}
          >
            {createMutation.isPending ? 'Creating...' : 'Create Note'}
          </Button>
        </form>
      </Card>

      {deduplicationModalData && (
        <DeduplicationModal
          data={deduplicationModalData}
          onConfirmed={handleDeduplicationConfirmed}
          onCancel={handleDeduplicationCancel}
        />
      )}

      {confirmDialogData && (
        <NoteCreateConfirmDialog
          data={confirmDialogData}
          onClose={() => setConfirmDialogData(null)}
        />
      )}
    </>
  );
};

export default NoteEditor;

