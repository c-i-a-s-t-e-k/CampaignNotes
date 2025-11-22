import React from 'react';
import { useUIStore } from '../stores';
import CampaignList from '../components/CampaignList';
import GraphCanvas from '../components/GraphCanvas';
import AssistantPanel from '../components/AssistantPanel';
import NoteEditor from '../components/NoteEditor';
import NoteDetailView from '../components/NoteDetailView';
import ArtifactDetailsPanel from '../components/ArtifactDetailsPanel';
import { Button } from '../components/ui/button';
import { ChevronLeft, ChevronRight, PanelLeftClose, PanelRightClose } from 'lucide-react';

/**
 * Main layout component.
 * Organizes the application UI into panels: campaign list, graph, search, and note editor.
 */
const MainLayout: React.FC = () => {
  const {
    isCampaignListOpen,
    isSearchPanelOpen,
    isNoteEditorOpen,
    toggleCampaignList,
    toggleSearchPanel,
    toggleNoteEditor,
    selectedNoteId,
    selectedArtifactId,
    setSelectedNoteId,
  } = useUIStore();

  return (
    <div className="h-screen flex flex-col bg-background">
      {/* Header */}
      <header className="h-16 border-b border-border flex items-center px-6">
        <h1 className="text-2xl font-bold">CampaignNotes</h1>
      </header>

      {/* Main content area */}
      <div className="flex-1 flex overflow-hidden">
        {/* Left Panel - Campaign List */}
        <div
          className={`border-r border-border transition-all duration-300 ${
            isCampaignListOpen ? 'w-80' : 'w-0'
          } overflow-hidden`}
        >
          <CampaignList />
        </div>

        {/* Toggle button for left panel */}
        <div className="flex items-center">
          <Button
            variant="ghost"
            size="sm"
            onClick={toggleCampaignList}
            className="h-full rounded-none"
          >
            {isCampaignListOpen ? (
              <ChevronLeft className="h-4 w-4" />
            ) : (
              <ChevronRight className="h-4 w-4" />
            )}
          </Button>
        </div>

        {/* Center - Graph Canvas */}
        <div className="flex-1 p-4 overflow-hidden">
          <GraphCanvas />
        </div>

        {/* Toggle button for right panel */}
        <div className="flex items-center">
          <Button
            variant="ghost"
            size="sm"
            onClick={toggleSearchPanel}
            className="h-full rounded-none"
          >
            {isSearchPanelOpen ? (
              <ChevronRight className="h-4 w-4" />
            ) : (
              <ChevronLeft className="h-4 w-4" />
            )}
          </Button>
        </div>

        {/* Right Panel - Assistant, Artifact Details & Note Detail */}
        <div
          className={`border-l border-border transition-all duration-300 ${
            isSearchPanelOpen ? 'w-96' : 'w-0'
          } overflow-hidden`}
        >
          <div className="h-full overflow-y-auto p-4">
            {selectedNoteId ? (
              <NoteDetailView
                noteId={selectedNoteId}
                onClose={() => setSelectedNoteId(null)}
              />
            ) : selectedArtifactId ? (
              <ArtifactDetailsPanel />
            ) : (
              <AssistantPanel />
            )}
          </div>
        </div>
      </div>

      {/* Bottom Panel - Note Editor */}
      <div
        className={`border-t border-border transition-all duration-300 ${
          isNoteEditorOpen ? 'h-96' : 'h-0'
        } overflow-hidden`}
      >
        <div className="h-full overflow-y-auto p-4">
          <div className="flex items-center justify-between mb-2">
            <Button
              variant="ghost"
              size="sm"
              onClick={toggleNoteEditor}
              className="ml-auto"
            >
              {isNoteEditorOpen ? (
                <ChevronRight className="h-4 w-4 rotate-90" />
              ) : (
                <ChevronLeft className="h-4 w-4 rotate-90" />
              )}
            </Button>
          </div>
          <NoteEditor />
        </div>
      </div>

      {/* Toggle button for bottom panel (when closed) */}
      {!isNoteEditorOpen && (
        <div className="border-t border-border p-2 flex justify-center">
          <Button
            variant="ghost"
            size="sm"
            onClick={toggleNoteEditor}
          >
            <ChevronLeft className="h-4 w-4 -rotate-90" />
          </Button>
        </div>
      )}
    </div>
  );
};

export default MainLayout;

