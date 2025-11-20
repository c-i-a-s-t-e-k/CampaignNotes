package CampaignNotes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import CampaignNotes.dto.NoteProcessingStatus;

@ExtendWith(MockitoExtension.class)
public class NoteProcessingStatusServiceTest {

    @Mock
    private NoteProcessingStatusService self; // Mock for self-injection

    @Spy
    @InjectMocks
    private NoteProcessingStatusService service;

    @Test
    void testUpdateStage_CacheMissThenHit() {
        // Given
        String noteId = "test-note-123";
        ReflectionTestUtils.setField(service, "self", self);

        NoteProcessingStatus notFoundStatus = new NoteProcessingStatus();
        notFoundStatus.setNoteId(noteId);
        notFoundStatus.setStatus("not_found");

        // When: First call to getStatus (cache miss)
        when(self.getStatus(noteId)).thenReturn(notFoundStatus);
        when(self.updateStatus(any(NoteProcessingStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Action
        service.updateStage(noteId, "embedding", "Embedding note...", 10);

        // Then
        ArgumentCaptor<NoteProcessingStatus> statusCaptor = ArgumentCaptor.forClass(NoteProcessingStatus.class);
        verify(self).updateStatus(statusCaptor.capture());

        NoteProcessingStatus capturedStatus = statusCaptor.getValue();
        assertEquals(noteId, capturedStatus.getNoteId());
        assertEquals("processing", capturedStatus.getStatus());
        assertEquals("embedding", capturedStatus.getStage());
        assertEquals(10, capturedStatus.getProgress());

        // When: Second call to getStatus (should be a cache hit, returning the updated object)
        when(self.getStatus(noteId)).thenReturn(capturedStatus);
        
        // Action 2
        service.updateStage(noteId, "nae", "Extracting artifacts...", 50);

        // Then 2
        verify(self, times(2)).updateStatus(statusCaptor.capture());
        NoteProcessingStatus secondCapturedStatus = statusCaptor.getValue();
        assertEquals("nae", secondCapturedStatus.getStage());
        assertEquals(50, secondCapturedStatus.getProgress());
        // Verify that it was based on the previous state, not a "not_found" state
        assertEquals("processing", secondCapturedStatus.getStatus()); 
    }
}
