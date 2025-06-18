package CampaignNotes;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Service for generating embeddings using OpenAI's text-embedding-3-large model.
 * Handles communication with OpenAI API for converting text to vector embeddings.
 */
public class OpenAIEmbeddingService {
    
    private static final String EMBEDDING_MODEL = "text-embedding-3-large";
    private static final int EMBEDDING_DIMENSION = 3072; // text-embedding-3-large dimension
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/embeddings";
    
    private final HttpClient httpClient;
    private final String apiKey;
    private final Gson gson;
    
    /**
     * Constructor that initializes the OpenAI service with API key from environment.
     */
    public OpenAIEmbeddingService() {
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            this.apiKey = dotenv.get("OPENAI_API_KEY");
            
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalStateException("OPENAI_API_KEY must be set in environment variables");
            }
            
            // Initialize HTTP client with timeout settings
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            
            this.gson = new Gson();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize OpenAIEmbeddingService: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generates embedding vector for the given text using text-embedding-3-large model.
     * 
     * @param text the text to generate embedding for
     * @return List of Double representing the embedding vector
     * @throws RuntimeException if embedding generation fails
     */
    public List<Double> generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }
        
        return generateEmbeddings(List.of(text)).get(0);
    }
    
    /**
     * Generates embeddings for multiple texts in a single API call.
     * More efficient than multiple single calls.
     * 
     * @param texts list of texts to generate embeddings for
     * @return List of embedding vectors, one for each input text
     * @throws RuntimeException if embedding generation fails
     */
    public List<List<Double>> generateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("Texts list cannot be null or empty");
        }
        
        // Filter out null or empty texts
        List<String> validTexts = texts.stream()
                .filter(text -> text != null && !text.trim().isEmpty())
                .toList();
        
        if (validTexts.isEmpty()) {
            throw new IllegalArgumentException("No valid texts provided");
        }
        
        try {
            // Construct request payload
            JsonObject requestPayload = new JsonObject();
            requestPayload.addProperty("model", EMBEDDING_MODEL);
            
            JsonArray inputArray = new JsonArray();
            validTexts.forEach(inputArray::add);
            requestPayload.add("input", inputArray);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_API_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestPayload)))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new RuntimeException("OpenAI API call failed with status: " + response.statusCode() + 
                                         ", body: " + response.body());
            }
            
            // Parse response
            JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
            JsonArray dataArray = responseJson.getAsJsonArray("data");
            
            if (dataArray.size() != validTexts.size()) {
                throw new RuntimeException("Mismatch between input texts and embedding results");
            }
            
            return dataArray.asList().stream()
                    .map(element -> {
                        JsonObject dataObject = element.getAsJsonObject();
                        JsonArray embeddingArray = dataObject.getAsJsonArray("embedding");
                        return gson.fromJson(embeddingArray, new TypeToken<List<Double>>(){}.getType());
                    })
                    .map(list -> (List<Double>) list)
                    .toList();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate embeddings: " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the embedding model being used.
     * 
     * @return the embedding model name
     */
    public String getEmbeddingModel() {
        return EMBEDDING_MODEL;
    }
    
    /**
     * Gets the dimension of embeddings generated by this service.
     * 
     * @return the embedding dimension
     */
    public int getEmbeddingDimension() {
        return EMBEDDING_DIMENSION;
    }
    
    /**
     * Tests if the OpenAI service is accessible.
     * 
     * @return true if service is accessible, false otherwise
     */
    public boolean testConnection() {
        try {
            generateEmbedding("test");
            return true;
        } catch (Exception e) {
            System.err.println("OpenAI service connection test failed: " + e.getMessage());
            return false;
        }
    }
} 