package CampaignNotes.database;

import java.util.Map;

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

    /**
     * Deletes all nodes and relationships from Neo4j for a given campaign UUID.
     * Performs a hard delete by removing all nodes with matching campaign_uuid property.
     * 
     * @param campaignUuid The UUID of the campaign whose data should be deleted
     * @return true if deletion was successful, false otherwise
     */
    public boolean deleteHardCampaignSubgraphById(String campaignUuid) {
        try {
            Driver driver = getDriver();
            if (driver == null) {
                System.err.println("Neo4j driver not available");
                return false;
            }

            try (var session = driver.session()) {
                return session.executeWrite(tx -> {
                    try {
                        // Delete all nodes with matching campaign_uuid and their relationships
                        String cypher = "MATCH (a) WHERE a.campaign_uuid = $campaignUuid DETACH DELETE a";
                        
                        var result = tx.run(cypher, Map.of("campaignUuid", campaignUuid));
                        var summary = result.consume();
                        
                        int nodesDeleted = summary.counters().nodesDeleted();
                        System.out.println("Deleted " + nodesDeleted + " nodes from Neo4j for campaign UUID: " + campaignUuid);
                        
                        return nodesDeleted >= 0; // Return true even if no nodes were found
                    } catch (Exception e) {
                        System.err.println("Error deleting campaign subgraph from Neo4j: " + e.getMessage());
                        e.printStackTrace();
                        return false;
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Error deleting campaign subgraph from Neo4j: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void close() {
        if (neo4jDriver != null) {
            try {
                neo4jDriver.close();
            } catch (Exception e) {
                System.err.println("Error closing Neo4j connection: " + e.getMessage());
            }
        }
    }
}


