package CampaignNotes.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.github.cdimascio.dotenv.Dotenv;
import model.LLMResponse;

/**
 * Service for communicating with Ollama API for Qwen3 LLM operations.
 * Supports both qwen3 and qwen3-thinking models.
 */
public class Qwen3OllamaLLMService implements LLMService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(Qwen3OllamaLLMService.class);
    
    private final String ollamaHost;
    private final HttpClient httpClient;
    private final Gson gson;
    
    /**
     * Constructor that initializes the Qwen3 Ollama LLM Service with configuration from environment variables.
     */
    public Qwen3OllamaLLMService() {
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            
            this.ollamaHost = dotenv.get("OLLAMA_HOST");
            
            if (ollamaHost == null || ollamaHost.isEmpty()) {
                throw new IllegalStateException("OLLAMA_HOST must be set in environment variables");
            }
            
            // Initialize HTTP client
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            
            this.gson = new Gson();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Qwen3OllamaLLMService: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a response with retry logic.
     * 
     * @param model the model to use (qwen3 or qwen3-thinking)
     * @param systemPrompt the system prompt
     * @param inputPrompt the user input prompt
     * @param maxRetries maximum number of retries
     * @return LLMResponse with the generated content and metadata
     */
    @Override
    public LLMResponse generateWithRetry(String model, String systemPrompt, String inputPrompt, int maxRetries) {
        LLMResponse lastResponse = null;
        
        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            lastResponse = generateWithModel(model, systemPrompt, inputPrompt);
            
            if (lastResponse.isSuccessful()) {
                return lastResponse;
            }
            
            if (attempt <= maxRetries) {
                System.err.println("LLM generation attempt " + attempt + " failed: " + lastResponse.getErrorMessage() + 
                                 ". Retrying...");
                
                // Wait before retry (exponential backoff)
                try {
                    Thread.sleep(1000 * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        System.err.println("LLM generation failed after " + (maxRetries + 1) + " attempts");
        return lastResponse;
    }

    /**
     * Internal method to generate response with a specific model.
     * 
     * @param model the model name (qwen3 or qwen3-thinking)
     * @param systemPrompt the system prompt
     * @param inputPrompt the user input prompt
     * @return LLMResponse with the generated content and metadata
     */
    private LLMResponse generateWithModel(String model, String systemPrompt, String inputPrompt) {
        long startTime = System.currentTimeMillis();
        LOGGER.info("[LLM] Starting {} generation", model);
        
        try {
            // Validate model
            boolean isThinkingModel = "qwen3-thinking".equals(model);
            if (!"qwen3".equals(model) && !isThinkingModel) {
                String errorMessage = "Unsupported model: " + model + ". Only 'qwen3' and 'qwen3-thinking' are supported.";
                return new LLMResponse(errorMessage);
            }
            
            String chatEndpoint = ollamaHost + "/api/chat";
            
            // Create request payload
            JsonObject payload = new JsonObject();
            payload.addProperty("model", "qwen3"); // Always use qwen3 in request
            payload.addProperty("stream", false);
            payload.addProperty("think", isThinkingModel); // true for qwen3-thinking, false for qwen3
            
            // Create messages array with separate system and user messages
            JsonArray messages = new JsonArray();
            
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", systemPrompt);
            messages.add(systemMessage);
            
            JsonObject userMessage = new JsonObject();
            userMessage.addProperty("role", "user");
            userMessage.addProperty("content", inputPrompt);
            messages.add(userMessage);
            
            payload.add("messages", messages);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chatEndpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(2))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - startTime;
            LOGGER.info("[LLM] {} generation completed in {}ms", model, duration);
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
                
                // Extract response content
                if (!responseJson.has("message")) {
                    return new LLMResponse("No message returned from Ollama API");
                }
                
                JsonObject message = responseJson.getAsJsonObject("message");
                if (!message.has("content")) {
                    return new LLMResponse("No content in message from Ollama API");
                }
                
                String content = message.get("content").getAsString();
                
                // Extract token usage
                int promptTokens = 0;
                int completionTokens = 0;
                if (responseJson.has("prompt_eval_count")) {
                    promptTokens = responseJson.get("prompt_eval_count").getAsInt();
                }
                if (responseJson.has("eval_count")) {
                    completionTokens = responseJson.get("eval_count").getAsInt();
                }
                
                // Extract duration (convert from nanoseconds to milliseconds)
                long durationMs = duration; // Use measured duration as fallback
                if (responseJson.has("total_duration")) {
                    long totalDurationNs = responseJson.get("total_duration").getAsLong();
                    durationMs = totalDurationNs / 1_000_000; // Convert nanoseconds to milliseconds
                }
                
                // Extract model from response
                String responseModel = model; // Use requested model as fallback
                if (responseJson.has("model")) {
                    responseModel = responseJson.get("model").getAsString();
                }
                
                return new LLMResponse(content, promptTokens, completionTokens, responseModel, durationMs);
                
            } else {
                String errorMessage = "Ollama API error. Status: " + response.statusCode() + 
                                    ", Response: " + response.body();
                System.err.println(errorMessage);
                return new LLMResponse(errorMessage);
            }
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            String errorMessage = "Error communicating with Ollama API: " + e.getMessage();
            System.err.println(errorMessage);
            
            LLMResponse errorResponse = new LLMResponse(errorMessage);
            errorResponse.setDurationMs(duration);
            errorResponse.setModel(model);
            return errorResponse;
        }
    }
    
    /**
     * Checks if the Ollama API is available and accessible.
     * 
     * @return true if the API is available, false otherwise
     */
    @Override
    public boolean checkApiAvailability() {
        try {
            String tagsEndpoint = ollamaHost + "/api/tags";
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tagsEndpoint))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            } else {
                System.err.println("Ollama API check failed. Status: " + response.statusCode());
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("Error checking Ollama API availability: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets the configured Ollama API URL.
     * 
     * @return the API URL
     */
    @Override
    public String getApiUrl() {
        return ollamaHost;
    }
}
