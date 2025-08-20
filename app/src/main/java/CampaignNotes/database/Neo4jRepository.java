package CampaignNotes.database;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.exceptions.ServiceUnavailableException;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Repository responsible for managing the Neo4j driver lifecycle
 * and providing connectivity checks.
 */
public class Neo4jRepository {
    private final Dotenv dotenv;
    private Driver neo4jDriver;

    public Neo4jRepository(Dotenv dotenv) {
        this.dotenv = dotenv;
    }

    public Driver getDriver() {
        if (neo4jDriver == null) {
            if (dotenv == null) {
                System.err.println("Environment variables (Dotenv) not loaded");
                return null;
            }
            String dbUri = dotenv.get("NEO4J_URI");
            String dbUser = dotenv.get("NEO4J_USER");
            String dbPassword = dotenv.get("NEO4J_PASSWORD");

            if (dbUri == null || dbUser == null || dbPassword == null) {
                System.err.println("Neo4j connection parameters are not properly defined");
                return null;
            }

            try {
                neo4jDriver = GraphDatabase.driver(dbUri, AuthTokens.basic(dbUser, dbPassword));
                neo4jDriver.verifyConnectivity();
                System.out.println("Neo4j driver initialized successfully");
            } catch (Exception e) {
                System.err.println("Failed to initialize Neo4j driver: " + e.getMessage());
                return null;
            }
        }
        return neo4jDriver;
    }

    public boolean checkAvailability() {
        try {
            Driver driver = getDriver();
            if (driver != null) {
                driver.verifyConnectivity();
                System.out.println("Neo4j database is available");
                return true;
            }
            return false;
        } catch (ServiceUnavailableException e) {
            System.err.println("Neo4j database is unavailable: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Error connecting to Neo4j: " + e.getMessage());
            return false;
        }
    }

    public void close() {
        if (neo4jDriver != null) {
            try {
                neo4jDriver.close();
                System.out.println("Neo4j connection closed");
            } catch (Exception e) {
                System.err.println("Error closing Neo4j connection: " + e.getMessage());
            }
        }
    }
}


