package CampaignNotes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import CampaignNotes.database.DatabaseConnectionManager;
import CampaignNotes.llm.OpenAIEmbeddingService;
import CampaignNotes.llm.OpenAILLMService;
import CampaignNotes.tracking.otel.OpenTelemetryConfig;

/**
 * Main Spring Boot application class for CampaignNotes REST API.
 * Provides RESTful endpoints for campaign management, notes, search, and graph visualization.
 */
@SpringBootApplication
public class Application {
    
    public static void main(String[] args) {
        // Initialize OpenTelemetry for tracing
        OpenTelemetryConfig.initialize();
        
        SpringApplication.run(Application.class, args);
    }
    
    /**
     * Bean for DatabaseConnectionManager - shared across all services.
     */
    @Bean
    public DatabaseConnectionManager databaseConnectionManager() {
        return new DatabaseConnectionManager();
    }
    
    /**
     * Bean for OpenAIEmbeddingService.
     */
    @Bean
    public OpenAIEmbeddingService embeddingService() {
        return new OpenAIEmbeddingService();
    }
    
    /**
     * Bean for OpenAILLMService.
     */
    @Bean
    public OpenAILLMService llmService() {
        return new OpenAILLMService();
    }
    
    /**
     * Bean for CampaignManager.
     */
    @Bean
    public CampaignManager campaignManager(DatabaseConnectionManager dbConnectionManager) {
        return new CampaignManager(dbConnectionManager);
    }
    
    /**
     * Bean for ArtifactCategoryService.
     */
    @Bean
    public ArtifactCategoryService artifactCategoryService(DatabaseConnectionManager dbConnectionManager) {
        return new ArtifactCategoryService(dbConnectionManager);
    }
    
    /**
     * Bean for ArtifactGraphService.
     */
    @Bean
    public ArtifactGraphService artifactGraphService(OpenAILLMService llmService, 
                                                     ArtifactCategoryService categoryService,
                                                     DatabaseConnectionManager dbConnectionManager) {
        return new ArtifactGraphService(llmService, categoryService, dbConnectionManager);
    }
    
    /**
     * Bean for NoteService.
     */
    @Bean
    public NoteService noteService(CampaignManager campaignManager,
                                   OpenAIEmbeddingService embeddingService,
                                   ArtifactGraphService artifactService,
                                   DatabaseConnectionManager dbConnectionManager) {
        return new NoteService(campaignManager, embeddingService, artifactService, dbConnectionManager);
    }
    
    /**
     * Bean for SemantickSearchService.
     */
    @Bean
    public SemantickSearchService semantickSearchService(DatabaseConnectionManager dbConnectionManager,
                                                         OpenAIEmbeddingService embeddingService) {
        return new SemantickSearchService(dbConnectionManager, embeddingService);
    }
}

