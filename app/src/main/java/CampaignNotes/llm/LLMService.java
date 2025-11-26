package CampaignNotes.llm;

import model.LLMResponse;

public interface LLMService {
    public LLMResponse generateWithRetry(String model, String systemPrompt, String inputPrompt, int maxRetries);
    public boolean checkApiAvailability();
    public String getApiUrl();

}
