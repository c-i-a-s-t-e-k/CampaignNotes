package CampaignNotes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Configuration for Dotenv environment variable loading.
 * Provides a singleton Dotenv bean for dependency injection.
 */
@Configuration
public class DotenvConfig {

    /**
     * Creates a Dotenv bean for loading environment variables from .env file.
     * Uses ignoreIfMissing() to allow application to run even if .env file is not present.
     * 
     * @return configured Dotenv instance
     */
    @Bean
    public Dotenv dotenv() {
        return Dotenv.configure().ignoreIfMissing().load();
    }
}

