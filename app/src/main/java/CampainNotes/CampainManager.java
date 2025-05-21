package CampainNotes;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.exceptions.ServiceUnavailableException;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CampainManager {

    final Set<String> CampainsIds = new HashSet<>();
    
    /**
     * Checks if both Neo4j and Quadrand databases are up and running.
     * @return true if both databases are available, false otherwise
     */
    public boolean checkDatabasesAvailability() {
        // Load environment variables
        Dotenv dotenv = null;
        try {
            dotenv = Dotenv.load();
        } catch (Exception e) {
            System.err.println("Error loading .env file: " + e.getMessage());
            return false;
        }
        
        // Get Neo4j connection details
        final String dbUri = dotenv.get("NEO4J_URI");
        final String dbUser = dotenv.get("NEO4J_USER");
        final String dbPassword = dotenv.get("NEO4J_PASSWORD");
        
        // Get Quadrand connection details
        final String quadrandUrl = dotenv.get("QUADRANT_URL");
        final int quadrandPort = Integer.parseInt(dotenv.get("QUADRANT_GRPC_PORT"));
        
        boolean neo4jAvailable = checkNeo4jAvailability(dbUri, dbUser, dbPassword);
        boolean quadrantAvailable = checkQuadrantAvailability(quadrandUrl, quadrandPort);
        
        return neo4jAvailable && quadrantAvailable;
    }
    
    /**
     * Checks if Neo4j database is available.
     * @param dbUri Neo4j database URI
     * @param dbUser Neo4j username
     * @param dbPassword Neo4j password
     * @return true if Neo4j is available, false otherwise
     */
    private boolean checkNeo4jAvailability(String dbUri, String dbUser, String dbPassword) {
        if (dbUri == null || dbUser == null || dbPassword == null) {
            System.err.println("Neo4j connection parameters are not properly defined");
            return false;
        }
        
        // Check Neo4j connection
        try (Driver driver = GraphDatabase.driver(dbUri, AuthTokens.basic(dbUser, dbPassword))) {
            driver.verifyConnectivity();
            System.out.println("Neo4j database is available");
            return true;
        } catch (ServiceUnavailableException e) {
            System.err.println("Neo4j database is unavailable: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Error connecting to Neo4j: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks if Quadrant database is available.
     * @param quadrantUrl URL of the Quadrant service
     * @param grpcPort GRPC port for Quadrant service
     * @return true if Quadrant is available, false otherwise
     */
    private boolean checkQuadrantAvailability(String quadrantUrl, int grpcPort) {
        if (quadrantUrl == null || quadrantUrl.isEmpty()) {
            System.err.println("Quadrant URL is not defined in environment variables");
            return false;
        }
        
        try {
            QdrantClient client = new QdrantClient(
                    QdrantGrpcClient.newBuilder(quadrantUrl, grpcPort, false).build());
            
            // Get the ListenableFuture for the collection list
            var future = client.listCollectionsAsync();
            
            // Wait for the result with a timeout (5 seconds)
            future.get(5, java.util.concurrent.TimeUnit.SECONDS);
            
            System.out.println("Quadrant database is available");
            return true;
        } catch (Exception e) {
            System.err.println("Error connecting to Quadrant: " + e.getMessage());
            return false;
        }
    }


}
