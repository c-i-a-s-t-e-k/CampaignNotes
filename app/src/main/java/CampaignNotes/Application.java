package CampaignNotes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

import CampaignNotes.config.DeduplicationConfig;
import CampaignNotes.database.DatabaseConnectionManager;
import CampaignNotes.deduplication.DeduplicationCoordinator;
import CampaignNotes.llm.OpenAIEmbeddingService;
import CampaignNotes.llm.OpenAILLMService;
import CampaignNotes.tracking.otel.OpenTelemetryConfig;
import io.github.cdimascio.dotenv.Dotenv;

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
     * Bean for DeduplicationConfig.
     */
    @Bean
    public DeduplicationConfig deduplicationConfig() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        return new DeduplicationConfig(dotenv);
    }
    
    /**
     * Bean for GraphEmbeddingService.
     */
    @Bean
    public GraphEmbeddingService graphEmbeddingService(OpenAIEmbeddingService embeddingService,
                                                       DatabaseConnectionManager dbConnectionManager) {
        return new GraphEmbeddingService(embeddingService, dbConnectionManager);
    }
    
    /**
     * Bean for ArtifactGraphService.
     */
    @Bean
    public ArtifactGraphService artifactGraphService(OpenAILLMService llmService, 
                                                     ArtifactCategoryService categoryService,
                                                     DatabaseConnectionManager dbConnectionManager,
                                                     GraphEmbeddingService graphEmbeddingService,
                                                     DeduplicationConfig deduplicationConfig) {
        return new ArtifactGraphService(llmService, categoryService, dbConnectionManager,
                                       graphEmbeddingService, deduplicationConfig);
    }
    
    /**
     * Bean for DeduplicationSessionManager.
     */
    @Bean
    public DeduplicationSessionManager deduplicationSessionManager() {
        return DeduplicationSessionManager.getInstance();
    }
    
    /**
     * Bean for ArtifactMergeService.
     */
    @Bean
    public ArtifactMergeService artifactMergeService(DatabaseConnectionManager dbConnectionManager,
                                                     GraphEmbeddingService graphEmbeddingService) {
        return new ArtifactMergeService(dbConnectionManager, graphEmbeddingService);
    }
    
    /**
     * Bean for DeduplicationCoordinator.
     * Note: Uses @Lazy for NoteService to break circular dependency.
     */
    @Bean
    public CampaignNotes.deduplication.DeduplicationCoordinator deduplicationCoordinator(
            CampaignNotes.deduplication.CandidateFinder candidateFinder,
            CampaignNotes.deduplication.DeduplicationLLMService dedupLLMService,
            GraphEmbeddingService graphEmbeddingService,
            DeduplicationConfig config,
            @Lazy NoteService noteService) {
        return new CampaignNotes.deduplication.DeduplicationCoordinator(
            candidateFinder, dedupLLMService, graphEmbeddingService, config, noteService);
    }
    
    /**
     * Bean for CandidateFinder.
     */
    @Bean
    public CampaignNotes.deduplication.CandidateFinder candidateFinder(
            DatabaseConnectionManager dbConnectionManager,
            DeduplicationConfig config) {
        return new CampaignNotes.deduplication.CandidateFinder(dbConnectionManager, config);
    }
    
    /**
     * Bean for DeduplicationLLMService.
     */
    @Bean
    public CampaignNotes.deduplication.DeduplicationLLMService deduplicationLLMService(
            OpenAILLMService llmService,
            CampaignNotes.tracking.LangfuseClient langfuseClient) {
        return new CampaignNotes.deduplication.DeduplicationLLMService(llmService, langfuseClient);
    }
    
    /**
     * Bean for LangfuseClient.
     */
    @Bean
    public CampaignNotes.tracking.LangfuseClient langfuseClient() {
        return CampaignNotes.tracking.LangfuseClient.getInstance();
    }
    
    /**
     * Bean for NoteService.
     */
    @Bean
    public NoteService noteService(CampaignManager campaignManager,
                                   OpenAIEmbeddingService embeddingService,
                                   ArtifactGraphService artifactService,
                                   DatabaseConnectionManager dbConnectionManager,
                                   DeduplicationCoordinator deduplicationCoordinator,
                                   DeduplicationSessionManager sessionManager,
                                   ArtifactMergeService mergeService,
                                   DeduplicationConfig deduplicationConfig) {
        return new NoteService(campaignManager, embeddingService, artifactService, dbConnectionManager,
                             deduplicationCoordinator, sessionManager, mergeService, deduplicationConfig);
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

