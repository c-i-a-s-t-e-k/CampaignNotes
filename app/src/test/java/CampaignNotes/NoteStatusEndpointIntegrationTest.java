
package CampaignNotes;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import CampaignNotes.dto.NoteCreateResponse;
import CampaignNotes.dto.NoteProcessingStatus;

/**
 * Integration test for GET /api/campaigns/{campaignUuid}/notes/{noteId}/status endpoint.
 * 
 * Tests the complete workflow:
 * 1. Web layer startup with MockMvc
 * 2. Loading status into cache via NoteProcessingStatusService
 * 3. Changing status and verifying API responses
 * 4. Cleanup
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, 
                classes = {CampaignNotes.controller.NoteController.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Note Status Endpoint Integration Tests")
class NoteStatusEndpointIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private NoteProcessingStatusService statusService;
    
    @MockBean
    private NoteService noteService;
    
    @MockBean
    private CampaignManager campaignManager;
    
    @MockBean
    private DeduplicationSessionManager sessionManager;
    
    @MockBean
    private ArtifactGraphService artifactService;
    
    private static final String TEST_CAMPAIGN_UUID = "test-campaign-uuid";
    private static final String TEST_NOTE_ID = "test-note-id-123";
    
    @Test
    @DisplayName("Should return 404 when note status not found in cache")
    void shouldReturn404WhenStatusNotFound() throws Exception {
        // Given: status service returns "not_found" status
        NoteProcessingStatus notFoundStatus = new NoteProcessingStatus(TEST_NOTE_ID);
        notFoundStatus.setStatus("not_found");
        when(statusService.getStatus(anyString())).thenReturn(notFoundStatus);
        
        // When: requesting status for non-existent note
        // Then: should return 404 Not Found
        mockMvc.perform(get("/api/campaigns/{campaignUuid}/notes/{noteId}/status", 
                        TEST_CAMPAIGN_UUID, TEST_NOTE_ID)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
    
    @Test
    @DisplayName("Should return processing status when status is in cache")
    void shouldReturnProcessingStatus() throws Exception {
        // Given: status loaded into cache
        NoteProcessingStatus initialStatus = new NoteProcessingStatus(TEST_NOTE_ID);
        initialStatus.setStatus("processing");
        initialStatus.setStage("embedding");
        initialStatus.setStageDescription("Tworzenie embeddingów");
        initialStatus.setProgress(25);
        initialStatus.setStartedAt(LocalDateTime.now());
        
        when(statusService.getStatus(TEST_NOTE_ID)).thenReturn(initialStatus);
        
        // When: requesting status
        // Then: should return 200 OK with correct status data
        mockMvc.perform(get("/api/campaigns/{campaignUuid}/notes/{noteId}/status", 
                        TEST_CAMPAIGN_UUID, TEST_NOTE_ID)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.noteId").value(TEST_NOTE_ID))
                .andExpect(jsonPath("$.status").value("processing"))
                .andExpect(jsonPath("$.stage").value("embedding"))
                .andExpect(jsonPath("$.stageDescription").value("Tworzenie embeddingów"))
                .andExpect(jsonPath("$.progress").value(25));
    }
    
    @Test
    @DisplayName("Should return updated status after status change")
    void shouldReturnUpdatedStatusAfterChange() throws Exception {
        // Given: initial status in cache
        NoteProcessingStatus initialStatus = new NoteProcessingStatus(TEST_NOTE_ID);
        initialStatus.setStatus("processing");
        initialStatus.setStage("embedding");
        initialStatus.setProgress(10);
        when(statusService.getStatus(TEST_NOTE_ID)).thenReturn(initialStatus);
        
        // Verify initial status
        mockMvc.perform(get("/api/campaigns/{campaignUuid}/notes/{noteId}/status", 
                        TEST_CAMPAIGN_UUID, TEST_NOTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(10))
                .andExpect(jsonPath("$.stage").value("embedding"));
        
        // When: updating status to new stage
        NoteProcessingStatus updatedStatus = new NoteProcessingStatus(TEST_NOTE_ID);
        updatedStatus.setStatus("processing");
        updatedStatus.setStage("nae");
        updatedStatus.setStageDescription("Ekstrakcja artefaktów");
        updatedStatus.setProgress(50);
        when(statusService.getStatus(TEST_NOTE_ID)).thenReturn(updatedStatus);
        
        // Then: should return updated status
        mockMvc.perform(get("/api/campaigns/{campaignUuid}/notes/{noteId}/status", 
                        TEST_CAMPAIGN_UUID, TEST_NOTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteId").value(TEST_NOTE_ID))
                .andExpect(jsonPath("$.status").value("processing"))
                .andExpect(jsonPath("$.stage").value("nae"))
                .andExpect(jsonPath("$.stageDescription").value("Ekstrakcja artefaktów"))
                .andExpect(jsonPath("$.progress").value(50));
    }
    
    @Test
    @DisplayName("Should return completed status with result")
    void shouldReturnCompletedStatusWithResult() throws Exception {
        // Given: completed status with result
        NoteCreateResponse result = new NoteCreateResponse();
        result.setNoteId(TEST_NOTE_ID);
        result.setSuccess(true);
        result.setMessage("Notatka została przetworzona pomyślnie");
        result.setArtifactCount(5);
        result.setRelationshipCount(3);
        result.setRequiresUserConfirmation(false);
        
        NoteProcessingStatus completedStatus = new NoteProcessingStatus(TEST_NOTE_ID);
        completedStatus.setStatus("completed");
        completedStatus.setStage("completed");
        completedStatus.setProgress(100);
        completedStatus.setResult(result);
        completedStatus.setCompletedAt(LocalDateTime.now());
        
        when(statusService.getStatus(TEST_NOTE_ID)).thenReturn(completedStatus);
        
        // Then: should return completed status with result
        mockMvc.perform(get("/api/campaigns/{campaignUuid}/notes/{noteId}/status", 
                        TEST_CAMPAIGN_UUID, TEST_NOTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteId").value(TEST_NOTE_ID))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.stage").value("completed"))
                .andExpect(jsonPath("$.progress").value(100))
                .andExpect(jsonPath("$.result").exists())
                .andExpect(jsonPath("$.result.noteId").value(TEST_NOTE_ID))
                .andExpect(jsonPath("$.result.success").value(true))
                .andExpect(jsonPath("$.result.artifactCount").value(5))
                .andExpect(jsonPath("$.result.relationshipCount").value(3))
                .andExpect(jsonPath("$.completedAt").exists());
    }
    
    @Test
    @DisplayName("Should return failed status with error message")
    void shouldReturnFailedStatusWithErrorMessage() throws Exception {
        // Given: failed status with error message
        String errorMessage = "Błąd podczas przetwarzania notatki";
        NoteProcessingStatus failedStatus = new NoteProcessingStatus(TEST_NOTE_ID);
        failedStatus.setStatus("failed");
        failedStatus.setErrorMessage(errorMessage);
        failedStatus.setCompletedAt(LocalDateTime.now());
        
        when(statusService.getStatus(TEST_NOTE_ID)).thenReturn(failedStatus);
        
        // Then: should return failed status with error message
        mockMvc.perform(get("/api/campaigns/{campaignUuid}/notes/{noteId}/status", 
                        TEST_CAMPAIGN_UUID, TEST_NOTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteId").value(TEST_NOTE_ID))
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.errorMessage").value(errorMessage))
                .andExpect(jsonPath("$.completedAt").exists());
    }
    
    @Test
    @DisplayName("Should handle multiple status updates correctly")
    void shouldHandleMultipleStatusUpdates() throws Exception {
        // Given: initial status
        NoteProcessingStatus status1 = new NoteProcessingStatus(TEST_NOTE_ID);
        status1.setStatus("processing");
        status1.setStage("embedding");
        status1.setProgress(20);
        when(statusService.getStatus(TEST_NOTE_ID)).thenReturn(status1);
        
        mockMvc.perform(get("/api/campaigns/{campaignUuid}/notes/{noteId}/status", 
                        TEST_CAMPAIGN_UUID, TEST_NOTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(20))
                .andExpect(jsonPath("$.stage").value("embedding"));
        
        // When: performing multiple status updates
        NoteProcessingStatus status2 = new NoteProcessingStatus(TEST_NOTE_ID);
        status2.setStatus("processing");
        status2.setStage("nae");
        status2.setProgress(40);
        when(statusService.getStatus(TEST_NOTE_ID)).thenReturn(status2);
        
        mockMvc.perform(get("/api/campaigns/{campaignUuid}/notes/{noteId}/status", 
                        TEST_CAMPAIGN_UUID, TEST_NOTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(40))
                .andExpect(jsonPath("$.stage").value("nae"));
        
        NoteProcessingStatus status3 = new NoteProcessingStatus(TEST_NOTE_ID);
        status3.setStatus("processing");
        status3.setStage("are");
        status3.setProgress(60);
        when(statusService.getStatus(TEST_NOTE_ID)).thenReturn(status3);
        
        mockMvc.perform(get("/api/campaigns/{campaignUuid}/notes/{noteId}/status", 
                        TEST_CAMPAIGN_UUID, TEST_NOTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(60))
                .andExpect(jsonPath("$.stage").value("are"));
        
        NoteProcessingStatus status4 = new NoteProcessingStatus(TEST_NOTE_ID);
        status4.setStatus("processing");
        status4.setStage("deduplication");
        status4.setProgress(80);
        when(statusService.getStatus(TEST_NOTE_ID)).thenReturn(status4);
        
        mockMvc.perform(get("/api/campaigns/{campaignUuid}/notes/{noteId}/status", 
                        TEST_CAMPAIGN_UUID, TEST_NOTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(80))
                .andExpect(jsonPath("$.stage").value("deduplication"));
        
        // Then: final status should be correct
        NoteCreateResponse result = new NoteCreateResponse();
        result.setNoteId(TEST_NOTE_ID);
        result.setSuccess(true);
        
        NoteProcessingStatus completedStatus = new NoteProcessingStatus(TEST_NOTE_ID);
        completedStatus.setStatus("completed");
        completedStatus.setProgress(100);
        completedStatus.setResult(result);
        when(statusService.getStatus(TEST_NOTE_ID)).thenReturn(completedStatus);
        
        mockMvc.perform(get("/api/campaigns/{campaignUuid}/notes/{noteId}/status", 
                        TEST_CAMPAIGN_UUID, TEST_NOTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.progress").value(100));
    }
}
