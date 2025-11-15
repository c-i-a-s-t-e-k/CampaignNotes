import React, { useState, useEffect } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { confirmDeduplication } from '../api';
import { MergeProposal, NoteCreateResponse, NoteConfirmationRequest } from '../types';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from './ui/dialog';
import { Button } from './ui/button';
import { Checkbox } from './ui/checkbox';
import { Badge } from './ui/badge';
import { Separator } from './ui/separator';
import { AlertCircle, CheckCircle2, XCircle } from 'lucide-react';
import toast from 'react-hot-toast';

interface DeduplicationModalProps {
  data: NoteCreateResponse;
  onConfirmed: (finalData: NoteCreateResponse) => void;
  onCancel: () => void;
}

/**
 * Modal for reviewing and confirming deduplication proposals.
 * Allows users to approve or reject merge suggestions for artifacts and relationships.
 */
const DeduplicationModal: React.FC<DeduplicationModalProps> = ({
  data,
  onConfirmed,
  onCancel,
}) => {
  const queryClient = useQueryClient();
  
  // Local state: Map of proposal ID to approval status
  const [approvedProposals, setApprovedProposals] = useState<Map<string, boolean>>(new Map());

  // Initialize approved proposals - auto-merge proposals start as approved
  useEffect(() => {
    const initialApprovals = new Map<string, boolean>();
    data.artifactMergeProposals.forEach((proposal, index) => {
      const proposalKey = `${proposal.itemType}-${index}`;
      initialApprovals.set(proposalKey, proposal.autoMerge);
    });
    setApprovedProposals(initialApprovals);
  }, [data.artifactMergeProposals]);

  // Mutation for confirming deduplication
  const confirmMutation = useMutation({
    mutationFn: (request: NoteConfirmationRequest) => {
      return confirmDeduplication(data.campaignUuid, data.noteId, request);
    },
    onSuccess: (finalData) => {
      toast.success('Deduplication completed successfully!');
      queryClient.invalidateQueries({ queryKey: ['graph'] });
      onConfirmed(finalData);
    },
    onError: (error: any) => {
      const message = error.response?.data?.message || 'Failed to confirm deduplication';
      toast.error(message);
      console.error(error);
    },
  });

  // Toggle approval status for a proposal
  const toggleProposal = (proposalKey: string) => {
    setApprovedProposals((prev) => {
      const newMap = new Map(prev);
      newMap.set(proposalKey, !prev.get(proposalKey));
      return newMap;
    });
  };

  // Approve all proposals
  const approveAll = () => {
    const newMap = new Map<string, boolean>();
    data.artifactMergeProposals.forEach((_, index) => {
      const artifactKey = `artifact-${index}`;
      const relationshipKey = `relationship-${index}`;
      newMap.set(artifactKey, true);
      newMap.set(relationshipKey, true);
    });
    setApprovedProposals(newMap);
  };

  // Reject all proposals
  const rejectAll = () => {
    const newMap = new Map<string, boolean>();
    data.artifactMergeProposals.forEach((_, index) => {
      const artifactKey = `artifact-${index}`;
      const relationshipKey = `relationship-${index}`;
      newMap.set(artifactKey, false);
      newMap.set(relationshipKey, false);
    });
    setApprovedProposals(newMap);
  };

  // Handle confirmation submission
  const handleConfirm = () => {
    const approved: MergeProposal[] = [];
    
    data.artifactMergeProposals.forEach((proposal, index) => {
      const proposalKey = `${proposal.itemType}-${index}`;
      const isApproved = approvedProposals.get(proposalKey) || false;
      
      if (isApproved) {
        approved.push({
          ...proposal,
          approved: true,
        });
      }
    });

    const request: NoteConfirmationRequest = {
      campaignUuid: data.campaignUuid,
      noteId: data.noteId,
      approvedMergeProposals: approved,
    };

    confirmMutation.mutate(request);
  };

  // Group proposals by type
  const artifactProposals = data.artifactMergeProposals.filter(p => p.itemType === 'artifact');
  const relationshipProposals = data.artifactMergeProposals.filter(p => p.itemType === 'relationship');

  // Count stats
  const requiresConfirmationCount = data.artifactMergeProposals.filter(p => !p.autoMerge).length;
  const autoMergeCount = data.artifactMergeProposals.filter(p => p.autoMerge).length;
  const approvedCount = Array.from(approvedProposals.values()).filter(Boolean).length;

  return (
    <Dialog open={true} onOpenChange={() => {}}>
      <DialogContent className="sm:max-w-[700px] max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <div className="flex items-center gap-2">
            <AlertCircle className="h-6 w-6 text-yellow-500" />
            <DialogTitle>Review Potential Duplicates</DialogTitle>
          </div>
          <DialogDescription>
            We found potential duplicate items in your note. Please review and confirm which items should be merged.
          </DialogDescription>
        </DialogHeader>

        {/* Stats Section */}
        <div className="grid grid-cols-3 gap-4 p-4 bg-accent rounded-md">
          <div>
            <p className="text-xs text-muted-foreground">Total Proposals</p>
            <p className="text-2xl font-bold">{data.artifactMergeProposals.length}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Auto-Merge</p>
            <p className="text-2xl font-bold text-green-600">{autoMergeCount}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Needs Review</p>
            <p className="text-2xl font-bold text-yellow-600">{requiresConfirmationCount}</p>
          </div>
        </div>

        <div className="space-y-4 py-4">
          {/* Artifact Proposals */}
          {artifactProposals.length > 0 && (
            <div>
              <h4 className="font-medium text-sm mb-3 flex items-center gap-2">
                Artifact Duplicates
                <Badge variant="secondary">{artifactProposals.length}</Badge>
              </h4>
              <div className="space-y-2">
                {artifactProposals.map((proposal, index) => {
                  const proposalKey = `artifact-${index}`;
                  const isApproved = approvedProposals.get(proposalKey) || false;
                  
                  return (
                    <div
                      key={proposalKey}
                      className={`p-3 rounded-md border ${
                        proposal.autoMerge
                          ? 'bg-green-500/5 border-green-500/20'
                          : 'bg-yellow-500/5 border-yellow-500/20'
                      }`}
                    >
                      <div className="flex items-start gap-3">
                        <Checkbox
                          checked={isApproved}
                          onCheckedChange={() => toggleProposal(proposalKey)}
                          className="mt-1"
                        />
                        <div className="flex-1">
                          <div className="flex items-center gap-2 mb-1">
                            <span className="font-medium text-sm">
                              {proposal.newItemName}
                            </span>
                            <span className="text-muted-foreground">→</span>
                            <span className="font-medium text-sm">
                              {proposal.existingItemName}
                            </span>
                            {proposal.autoMerge ? (
                              <Badge variant="default" className="bg-green-600">
                                <CheckCircle2 className="h-3 w-3 mr-1" />
                                Auto-Merge
                              </Badge>
                            ) : (
                              <Badge variant="outline" className="border-yellow-600 text-yellow-600">
                                Review Required
                              </Badge>
                            )}
                          </div>
                          <p className="text-xs text-muted-foreground mb-1">
                            Confidence: {proposal.confidence}%
                          </p>
                          <p className="text-xs text-muted-foreground">
                            {proposal.reasoning}
                          </p>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {artifactProposals.length > 0 && relationshipProposals.length > 0 && (
            <Separator />
          )}

          {/* Relationship Proposals */}
          {relationshipProposals.length > 0 && (
            <div>
              <h4 className="font-medium text-sm mb-3 flex items-center gap-2">
                Relationship Duplicates
                <Badge variant="secondary">{relationshipProposals.length}</Badge>
              </h4>
              <div className="space-y-2">
                {relationshipProposals.map((proposal, index) => {
                  const proposalKey = `relationship-${index}`;
                  const isApproved = approvedProposals.get(proposalKey) || false;
                  
                  return (
                    <div
                      key={proposalKey}
                      className={`p-3 rounded-md border ${
                        proposal.autoMerge
                          ? 'bg-green-500/5 border-green-500/20'
                          : 'bg-yellow-500/5 border-yellow-500/20'
                      }`}
                    >
                      <div className="flex items-start gap-3">
                        <Checkbox
                          checked={isApproved}
                          onCheckedChange={() => toggleProposal(proposalKey)}
                          className="mt-1"
                        />
                        <div className="flex-1">
                          <div className="flex items-center gap-2 mb-1">
                            <span className="font-medium text-sm">
                              {proposal.newItemName}
                            </span>
                            <span className="text-muted-foreground">→</span>
                            <span className="font-medium text-sm">
                              {proposal.existingItemName}
                            </span>
                            {proposal.autoMerge ? (
                              <Badge variant="default" className="bg-green-600">
                                <CheckCircle2 className="h-3 w-3 mr-1" />
                                Auto-Merge
                              </Badge>
                            ) : (
                              <Badge variant="outline" className="border-yellow-600 text-yellow-600">
                                Review Required
                              </Badge>
                            )}
                          </div>
                          <p className="text-xs text-muted-foreground mb-1">
                            Confidence: {proposal.confidence}%
                          </p>
                          <p className="text-xs text-muted-foreground">
                            {proposal.reasoning}
                          </p>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>

        <DialogFooter className="flex gap-2 sm:gap-2">
          <Button
            variant="outline"
            onClick={rejectAll}
            disabled={confirmMutation.isPending}
          >
            <XCircle className="h-4 w-4 mr-2" />
            Reject All
          </Button>
          <Button
            variant="outline"
            onClick={approveAll}
            disabled={confirmMutation.isPending}
          >
            <CheckCircle2 className="h-4 w-4 mr-2" />
            Approve All
          </Button>
          <Button
            onClick={handleConfirm}
            disabled={confirmMutation.isPending}
            className="flex-1"
          >
            {confirmMutation.isPending ? 'Processing...' : `Confirm (${approvedCount} selected)`}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default DeduplicationModal;

