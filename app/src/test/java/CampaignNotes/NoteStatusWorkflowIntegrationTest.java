package CampaignNotes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import CampaignNotes.config.AsyncConfiguration;
import CampaignNotes.controller.NoteController;
import CampaignNotes.dto.NoteCreateResponse;

/**
 * Integration test that verifies the full GET /api/campaigns/{}/notes/{}/status workflow
 * with a real NoteProcessingStatusService and cache-backed lifecycle updates.
 */
@Tag("integration")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = NoteStatusWorkflowIntegrationTest.TestConfig.class
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NoteStatusWorkflowIntegrationTest {

    private static final String TEST_CAMPAIGN_UUID = "campaign-workflow-test";
    private static final String TEST_NOTE_ID = "note-workflow-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NoteProcessingStatusService statusService;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private NoteService noteService;

    @MockBean
    private CampaignManager campaignManager;

    @MockBean
    private DeduplicationSessionManager sessionManager;

    @MockBean
    private ArtifactGraphService artifactService;

    @AfterEach
    void clearStatusCache() {
        Cache cache = cacheManager.getCache("noteProcessingStatus");
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    @DisplayName("Full status workflow is exposed through the API")
    void shouldExposeStatusWorkflowThroughApi() throws Exception {
        mockMvc.perform(get("/api/campaigns/{campaignUuid}/notes/{noteId}/status",
                        TEST_CAMPAIGN_UUID, TEST_NOTE_ID)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        statusService.updateStage(TEST_NOTE_ID, "embedding", "Generowanie embeddingów", 15);

        mockMvc.perform(get("/api/campaigns/{campaignUuid}/notes/{noteId}/status",
                        TEST_CAMPAIGN_UUID, TEST_NOTE_ID)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteId").value(TEST_NOTE_ID))
                .andExpect(jsonPath("$.status").value("processing"))
                .andExpect(jsonPath("$.stage").value("embedding"))
                .andExpect(jsonPath("$.stageDescription").value("Generowanie embeddingów"))
                .andExpect(jsonPath("$.progress").value(15));

        statusService.updateStage(TEST_NOTE_ID, "deduplication", "Porównywanie artefaktów", 70);

        mockMvc.perform(get("/api/campaigns/{campaignUuid}/notes/{noteId}/status",
                        TEST_CAMPAIGN_UUID, TEST_NOTE_ID)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("deduplication"))
                .andExpect(jsonPath("$.stageDescription").value("Porównywanie artefaktów"))
                .andExpect(jsonPath("$.progress").value(70));

        NoteCreateResponse result = new NoteCreateResponse();
        result.setNoteId(TEST_NOTE_ID);
        result.setSuccess(true);
        result.setMessage("Notatka została przetworzona");
        result.setArtifactCount(4);
        result.setRelationshipCount(2);
        result.setRequiresUserConfirmation(false);

        statusService.setCompletionResult(TEST_NOTE_ID, result, true, null);

        mockMvc.perform(get("/api/campaigns/{campaignUuid}/notes/{noteId}/status",
                        TEST_CAMPAIGN_UUID, TEST_NOTE_ID)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.stage").value("completed"))
                .andExpect(jsonPath("$.progress").value(100))
                .andExpect(jsonPath("$.result.noteId").value(TEST_NOTE_ID))
                .andExpect(jsonPath("$.result.artifactCount").value(4))
                .andExpect(jsonPath("$.result.relationshipCount").value(2))
                .andExpect(jsonPath("$.completedAt").exists());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
        NoteController.class,
        NoteProcessingStatusService.class,
        AsyncConfiguration.class
    })
    static class TestConfig {
    }
}

