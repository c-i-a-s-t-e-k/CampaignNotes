import React from 'react';
import { NoteCreateResponse } from '../types';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from './ui/dialog';
import { Button } from './ui/button';
import { CheckCircle2 } from 'lucide-react';

interface NoteCreateConfirmDialogProps {
  data: NoteCreateResponse;
  onClose: () => void;
}

/**
 * Dialog showing confirmation after note creation with artifact information.
 */
const NoteCreateConfirmDialog: React.FC<NoteCreateConfirmDialogProps> = ({
  data,
  onClose,
}) => {
  return (
    <Dialog open={true} onOpenChange={onClose}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-6 w-6 text-green-500" />
            <DialogTitle>Note Created Successfully</DialogTitle>
          </div>
          <DialogDescription>
            Your note has been processed and artifacts have been extracted.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          <div>
            <h4 className="font-medium text-sm mb-1">Note Title</h4>
            <p className="text-sm text-muted-foreground">{data.title}</p>
          </div>

          <div className="grid grid-cols-2 gap-4 p-4 bg-accent rounded-md">
            <div>
              <p className="text-xs text-muted-foreground">Artifacts</p>
              <p className="text-2xl font-bold">{data.artifactCount}</p>
            </div>
            <div>
              <p className="text-xs text-muted-foreground">Relationships</p>
              <p className="text-2xl font-bold">{data.relationshipCount}</p>
            </div>
          </div>

          {data.artifacts && data.artifacts.length > 0 && (
            <div>
              <h4 className="font-medium text-sm mb-2">Extracted Artifacts</h4>
              <div className="space-y-2 max-h-48 overflow-y-auto">
                {data.artifacts.map((artifact, index) => (
                  <div
                    key={index}
                    className="p-2 bg-accent rounded-md text-sm"
                  >
                    <p className="font-medium">{artifact.name}</p>
                    <p className="text-xs text-muted-foreground">
                      {artifact.type}
                    </p>
                    {artifact.description && (
                      <p className="text-xs text-muted-foreground mt-1">
                        {artifact.description}
                      </p>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {data.message && (
            <p className="text-sm text-muted-foreground">{data.message}</p>
          )}
        </div>

        <DialogFooter>
          <Button onClick={onClose}>Close</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default NoteCreateConfirmDialog;

