package CampaignNotes.llm;

import model.LLMResponse;

public class HeadLLMService implements LLMService {
    
    private final OpenAILLMService openAILLMService;
    private final Qwen3OllamaLLMService qwen3OllamaLLMService;
    
    /**
     * Constructor that initializes both LLM service instances.
     */
    public HeadLLMService() {
        this.openAILLMService = new OpenAILLMService();
        this.qwen3OllamaLLMService = new Qwen3OllamaLLMService();
    }
    
    @Override
    public LLMResponse generateWithRetry(String model, String systemPrompt, String inputPrompt, int maxRetries) {
        if (model.equals("qwen3") || model.equals("qwen3-thinking")) {
            return qwen3OllamaLLMService.generateWithRetry(model, systemPrompt, inputPrompt, maxRetries);
        } else if (model.equals("o3-mini") || model.equals("o1") || model.equals("gpt-4o") || model.equals("gpt-3.5-turbo")) {
            return openAILLMService.generateWithRetry(model, systemPrompt, inputPrompt, maxRetries);
        } else {
            return new LLMResponse("Model not supported");
        }
    }

    @Override
    public boolean checkApiAvailability() {
        return openAILLMService.checkApiAvailability() && qwen3OllamaLLMService.checkApiAvailability();
    }

    @Override
    public String getApiUrl() {
        return null;
    }
}
