package CampaignNotes.assistant;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Validator for Cypher queries to ensure they are read-only.
 * Provides security checks to prevent data modification operations.
 */
@Component
public class CypherValidator {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CypherValidator.class);
    
    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
        "CREATE", "MERGE", "DELETE", "SET", "REMOVE", "DROP",
        "DETACH DELETE", "CREATE INDEX", "CREATE CONSTRAINT"
    );
    
    /**
     * Validates if a Cypher query is read-only.
     * 
     * @param cypherQuery the Cypher query to validate
     * @return ValidationResult with validation status and error message if invalid
     */
    public ValidationResult validate(String cypherQuery) {
        if (cypherQuery == null || cypherQuery.trim().isEmpty()) {
            LOGGER.warn("Validation failed: Cypher query is null or empty");
            return ValidationResult.invalid("Cypher query cannot be null or empty");
        }
        
        String upperQuery = cypherQuery.toUpperCase();
        
        // Check for forbidden keywords
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (upperQuery.contains(keyword)) {
                LOGGER.warn("Validation failed: Query contains forbidden keyword: {}", keyword);
                return ValidationResult.invalid("Query contains forbidden keyword: " + keyword);
            }
        }
        
        // Verify query contains MATCH and RETURN
        if (!upperQuery.contains("MATCH")) {
            LOGGER.warn("Validation failed: Query must contain MATCH clause");
            return ValidationResult.invalid("Query must contain MATCH clause");
        }
        
        if (!upperQuery.contains("RETURN")) {
            LOGGER.warn("Validation failed: Query must contain RETURN clause");
            return ValidationResult.invalid("Query must contain RETURN clause");
        }
        
        LOGGER.debug("Cypher query validation passed");
        return ValidationResult.valid();
    }
    
    /**
     * Result of Cypher query validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String error;
        
        private ValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, error);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getError() {
            return error;
        }
    }
}

