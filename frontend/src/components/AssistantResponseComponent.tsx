import React from 'react';
import { AssistantResponse as AssistantResponseType } from '../types/assistant';
import { Card } from './ui/card';
import { AlertCircle, Info, Ban } from 'lucide-react';
import SourcesList from './SourcesList';

interface AssistantResponseComponentProps {
  response: AssistantResponseType;
}

/**
 * Component for rendering assistant responses with different types.
 */
const AssistantResponseComponent: React.FC<AssistantResponseComponentProps> = ({ response }) => {
  
  // Error response
  if (response.responseType === 'error') {
    return (
      <Card className="p-4 border-destructive">
        <div className="flex items-start gap-2">
          <AlertCircle className="h-5 w-5 text-destructive flex-shrink-0 mt-0.5" />
          <div className="flex-1">
            <h4 className="text-sm font-semibold text-destructive mb-2">Error</h4>
            <p className="text-sm text-muted-foreground">{response.textResponse}</p>
            {response.debugInfo && (
              <details className="mt-2">
                <summary className="text-xs text-muted-foreground cursor-pointer">
                  Debug Info
                </summary>
                <pre className="text-xs mt-1 p-2 bg-muted rounded">
                  {JSON.stringify(response.debugInfo, null, 2)}
                </pre>
              </details>
            )}
          </div>
        </div>
      </Card>
    );
  }

  // Clarification needed
  if (response.responseType === 'clarification_needed') {
    return (
      <Card className="p-4 border-blue-500">
        <div className="flex items-start gap-2">
          <Info className="h-5 w-5 text-blue-500 flex-shrink-0 mt-0.5" />
          <div className="flex-1">
            <h4 className="text-sm font-semibold text-blue-500 mb-2">Clarification Needed</h4>
            <p className="text-sm whitespace-pre-wrap">{response.textResponse}</p>
          </div>
        </div>
      </Card>
    );
  }

  // Out of scope
  if (response.responseType === 'out_of_scope') {
    return (
      <Card className="p-4 border-orange-500">
        <div className="flex items-start gap-2">
          <Ban className="h-5 w-5 text-orange-500 flex-shrink-0 mt-0.5" />
          <div className="flex-1">
            <h4 className="text-sm font-semibold text-orange-500 mb-2">Out of Scope</h4>
            <p className="text-sm whitespace-pre-wrap">{response.textResponse}</p>
          </div>
        </div>
      </Card>
    );
  }

  // Text or text+graph response
  return (
    <div className="space-y-4">
      <Card className="p-4">
        <h4 className="text-sm font-semibold mb-3">Response</h4>
        <div className="prose prose-sm max-w-none">
          <p className="text-sm whitespace-pre-wrap leading-relaxed">{response.textResponse}</p>
        </div>
        
        {response.responseType === 'text_and_graph' && response.graphData && (
          <div className="mt-4 p-3 bg-blue-50 dark:bg-blue-950 rounded-md border border-blue-200 dark:border-blue-800">
            <p className="text-xs text-blue-800 dark:text-blue-200">
              <Info className="h-3 w-3 inline mr-1" />
              Graph visualization has been updated. Check the main graph canvas to see the related nodes and relationships.
            </p>
          </div>
        )}
      </Card>
      
      {response.sources && response.sources.length > 0 && (
        <SourcesList sources={response.sources} />
      )}
    </div>
  );
};

export default AssistantResponseComponent;

