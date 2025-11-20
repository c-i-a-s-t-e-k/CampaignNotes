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
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

import CampaignNotes.tracking.LangfuseClient;
import io.github.cdimascio.dotenv.Dotenv;
import model.LLMResponse;

/**
 * Service for communicating with OpenAI API for LLM operations.
 * Currently uses HTTP calls but has SDK client prepared for future migration.
 * Provides functionality to generate responses using o3-mini model.
 */
public class OpenAILLMService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAILLMService.class);
    
    private final String openaiApiKey;
    private final String openaiApiUrl;
    private final OpenAIClient client; // SDK client for future use
    private final HttpClient httpClient;
    private final Gson gson;
    private final LangfuseClient langfuseClient;
    // public static final Integer MAX_TOKENS = 10068; // two times one output with huge input note ~500 words in Polish
    
    /**
     * Constructor that initializes the OpenAI LLM Service with configuration from environment variables.
     */
    public OpenAILLMService() {
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            
            this.openaiApiKey = dotenv.get("OPENAI_API_KEY");
            this.openaiApiUrl = dotenv.get("OPENAI_API_URL", "https://api.openai.com/v1");
            
            if (openaiApiKey == null || openaiApiKey.isEmpty()) {
                throw new IllegalStateException("OPENAI_API_KEY must be set in environment variables");
            }
            
            // Initialize OpenAI SDK client for future migration
            this.client = OpenAIOkHttpClient.builder()
                    .apiKey(openaiApiKey)
                    .baseUrl(openaiApiUrl)
                    .timeout(Duration.ofMinutes(2))
                    .build();
            
            // Initialize HTTP client for current implementation
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            
            this.gson = new Gson();
            this.langfuseClient = new LangfuseClient();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize OpenAILLMService: " + e.getMessage(), e);
        }
    }
    
    /**
     * Alternative constructor for dependency injection
     */
    public OpenAILLMService(LangfuseClient langfuseClient) {
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            
            this.openaiApiKey = dotenv.get("OPENAI_API_KEY");
            this.openaiApiUrl = dotenv.get("OPENAI_API_URL", "https://api.openai.com/v1");
            
            if (openaiApiKey == null || openaiApiKey.isEmpty()) {
                throw new IllegalStateException("OPENAI_API_KEY must be set in environment variables");
            }
            
            // Initialize OpenAI SDK client for future migration
            this.client = OpenAIOkHttpClient.builder()
                    .apiKey(openaiApiKey)
                    .baseUrl(openaiApiUrl)
                    .timeout(Duration.ofMinutes(2))
                    .build();
            
            // Initialize HTTP client for current implementation
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            
            this.gson = new Gson();
            this.langfuseClient = langfuseClient;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize OpenAILLMService: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a response with retry logic.
     * 
     * @param model the model to use (e.g., "o3-mini")
     * @param systemPrompt the system prompt
     * @param inputPrompt the user input prompt
     * @param maxRetries maximum number of retries
     * @return LLMResponse with the generated content and metadata
     */
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
     * @param model the OpenAI model name
     * @param systemPrompt the system prompt
     * @param inputPrompt the user input prompt
     * @return LLMResponse with the generated content and metadata
     */
    private LLMResponse generateWithModel(String model, String systemPrompt, String inputPrompt) {
        long startTime = System.currentTimeMillis();
        LOGGER.info("[LLM] Starting {} generation", model);
        
        try {
            String chatEndpoint = openaiApiUrl + "/chat/completions";
            
            // Create request payload
            JsonObject payload = new JsonObject();
            payload.addProperty("model", model);
            // payload.addProperty("max_completion_tokens", MAX_TOKENS);
//            payload.addProperty("temperature", 0.3);
            
            // Create messages array
            JsonArray messages = new JsonArray();
            
            // Combine system and user prompts into a single user message for instruction-following models
            String combinedPrompt = systemPrompt + "\n\n" + inputPrompt;
            
            JsonObject userMessage = new JsonObject();
            userMessage.addProperty("role", "user");
            userMessage.addProperty("content", combinedPrompt);
            messages.add(userMessage);
            
            payload.add("messages", messages);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chatEndpoint))
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(2)) // o1 models can be slow
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - startTime;
            LOGGER.info("[LLM] {} generation completed in {}ms", model, duration);
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
                
                // Extract response content
                JsonArray choices = responseJson.getAsJsonArray("choices");
                if (choices.size() == 0) {
                    return new LLMResponse("No choices returned from OpenAI API");
                }
                
                JsonObject firstChoice = choices.get(0).getAsJsonObject();
                JsonObject message = firstChoice.getAsJsonObject("message");
                String content = message.get("content").getAsString();
                
                // Extract token usage
                int totalTokens = 0;
                int promptTokens = 0;
                int completionTokens = 0;
                if (responseJson.has("usage")) {
                    JsonObject usage = responseJson.getAsJsonObject("usage");
                    if (usage.has("total_tokens")) {
                        totalTokens = usage.get("total_tokens").getAsInt();
                    }
                    if (usage.has("prompt_tokens")) {
                        promptTokens = usage.get("prompt_tokens").getAsInt();
                    }
                    if (usage.has("completion_tokens")) {
                        completionTokens = usage.get("completion_tokens").getAsInt();
                    }
                }
                
                if (promptTokens > 0 || completionTokens > 0) {
                    return new LLMResponse(content, promptTokens, completionTokens, model, duration);
                }
                return new LLMResponse(content, totalTokens, model, duration);
                
            } else {
                String errorMessage = "OpenAI API error. Status: " + response.statusCode() + 
                                    ", Response: " + response.body();
                System.err.println(errorMessage);
                return new LLMResponse(errorMessage);
            }
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            String errorMessage = "Error communicating with OpenAI API: " + e.getMessage();
            System.err.println(errorMessage);
            
            LLMResponse errorResponse = new LLMResponse(errorMessage);
            errorResponse.setDurationMs(duration);
            errorResponse.setModel(model);
            return errorResponse;
        }
    }
    
    /**
     * Checks if the OpenAI API is available and accessible.
     * 
     * @return true if the API is available, false otherwise
     */
    public boolean checkApiAvailability() {
        try {
            String modelsEndpoint = openaiApiUrl + "/models";
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(modelsEndpoint))
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            } else {
                System.err.println("OpenAI API check failed. Status: " + response.statusCode());
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("Error checking OpenAI API availability: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets the configured OpenAI API URL.
     * 
     * @return the API URL
     */
    public String getApiUrl() {
        return openaiApiUrl;
    }
    
    /**
     * Gets the initialized SDK client for potential future use.
     * Currently not used in main implementation.
     * 
     * @return the OpenAI SDK client
     */
    protected OpenAIClient getSdkClient() {
        return client;
    }
} 