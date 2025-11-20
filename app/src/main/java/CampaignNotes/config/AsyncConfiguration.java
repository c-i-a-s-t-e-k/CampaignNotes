package CampaignNotes.config;

import java.util.concurrent.Executor;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration for async processing and caching.
 * 
 * Enables @Async methods for background note processing and
 * configures Caffeine in-memory cache for tracking note processing status.
 */
@Configuration
@EnableAsync
@EnableCaching
public class AsyncConfiguration {
    
    /**
     * Configures thread pool for async note processing.
     * 
     * - corePoolSize: 5 threads (baseline for concurrent processing)
     * - maxPoolSize: 10 threads (handle peak loads)
     * - queueCapacity: 25 tasks (queue size when all threads busy)
     * 
     * @return configured ThreadPoolTaskExecutor
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("note-async-");
        executor.setAwaitTerminationSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
    
    // Cache configuration is handled by Spring Boot auto-configuration
    // when spring-boot-starter-cache and caffeine are on classpath.
    // Cache settings can be configured in application.properties:
    // spring.cache.cache-names=noteProcessingStatus
    // spring.cache.caffeine.spec=maximumSize=1000,expireAfterWrite=10m
}

