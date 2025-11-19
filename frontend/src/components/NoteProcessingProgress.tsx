import React, { useEffect } from 'react';
import { NoteProcessingStatus, NoteCreateResponse } from '../types';
import { Progress } from './ui/progress';
import { Card } from './ui/card';

interface NoteProcessingProgressProps {
  status: NoteProcessingStatus;
  onComplete: (result: NoteCreateResponse) => void;
  onError: (errorMessage: string) => void;
}

/**
 * Component to display note processing progress with visual feedback
 * 
 * Shows:
 * - Progress bar with percentage
 * - Current processing stage description
 * - Loading spinner animation
 */
export const NoteProcessingProgress: React.FC<NoteProcessingProgressProps> = ({
  status,
  onComplete,
  onError,
}) => {
  // Trigger callbacks when status changes to completed or failed
  useEffect(() => {
    if (status.status === 'completed' && status.result) {
      onComplete(status.result);
    } else if (status.status === 'failed') {
      onError(status.errorMessage || 'Note processing failed');
    }
  }, [status.status, status.result, status.errorMessage, onComplete, onError]);

  const progress = status.progress || 0;
  const stageDescription = status.stageDescription || 'Przetwarzanie notatki...';

  return (
    <Card className="w-full p-6 space-y-4">
      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <h3 className="font-semibold text-lg">Przetwarzanie notatki</h3>
          <span className="text-sm text-muted-foreground">{progress}%</span>
        </div>
        
        <Progress value={progress} className="w-full h-2" />
      </div>

      <div className="space-y-2">
        <p className="text-sm font-medium text-foreground">
          {stageDescription}
        </p>
        <p className="text-xs text-muted-foreground">
          Proszę czekać, operacja może potrwać kilka minut...
        </p>
      </div>

      <div className="flex items-center gap-2">
        <div className="flex gap-1">
          <span className="inline-block w-2 h-2 bg-primary rounded-full animate-bounce"></span>
          <span className="inline-block w-2 h-2 bg-primary rounded-full animate-bounce" style={{ animationDelay: '0.2s' }}></span>
          <span className="inline-block w-2 h-2 bg-primary rounded-full animate-bounce" style={{ animationDelay: '0.4s' }}></span>
        </div>
        <span className="text-sm text-muted-foreground">Przetwarzanie...</span>
      </div>
    </Card>
  );
};

export default NoteProcessingProgress;

