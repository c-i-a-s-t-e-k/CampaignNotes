package CampaignNotes.database;

import io.github.cdimascio.dotenv.Dotenv;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;

/**
 * Repository responsible for managing the Qdrant client lifecycle
 * and providing connectivity checks.
 */
public class QdrantRepository {
    private final Dotenv dotenv;
    private QdrantClient qdrantClient;

    public QdrantRepository(Dotenv dotenv) {
        this.dotenv = dotenv;
    }

    public QdrantClient getClient() {
        if (qdrantClient == null) {
            if (dotenv == null) {
                System.err.println("Environment variables (Dotenv) not loaded");
                return null;
            }

            String qdrantUrl = dotenv.get("QDRANT_URL");
            String qdrantPortStr = dotenv.get("QDRANT_GRPC_PORT");

            if (qdrantUrl == null || qdrantUrl.isEmpty() || qdrantPortStr == null) {
                System.err.println("Qdrant connection parameters are not properly defined");
                return null;
            }

            try {
                int qdrantPort = Integer.parseInt(qdrantPortStr);
                qdrantClient = new QdrantClient(
                        QdrantGrpcClient.newBuilder(qdrantUrl, qdrantPort, false).build());
            } catch (Exception e) {
                System.err.println("Failed to initialize Qdrant client: " + e.getMessage());
                return null;
            }
        }
        return qdrantClient;
    }

    public boolean checkAvailability() {
        try {
            QdrantClient client = getClient();
            if (client != null) {
                var future = client.listCollectionsAsync();
                future.get(5, java.util.concurrent.TimeUnit.SECONDS);
                return true;
            }
            return false;
        } catch (Exception e) {
            System.err.println("Error connecting to Qdrant: " + e.getMessage());
            return false;
        }
    }

    public void close() {
        if (qdrantClient != null) {
            qdrantClient.close();
        }
    }
}


