package model;

import java.io.Serializable;

/**
 * Wrapper class for responses from OpenAI LLM API calls.
 * Contains the response content along with metadata about the request.
 */
public class LLMResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String content;
    private int tokensUsed;
    private int inputTokens;
    private int outputTokens;
    private String model;
    private long durationMs;
    private boolean successful;
    private String errorMessage;
    
    /**
     * Default constructor
     */
    public LLMResponse() {
        this.successful = false;
        this.tokensUsed = 0;
        this.durationMs = 0;
    }
    
    /**
     * Constructor for successful response
     */
    public LLMResponse(String content, int tokensUsed, String model, long durationMs) {
        this();
        this.content = content;
        this.tokensUsed = tokensUsed;
        this.model = model;
        this.durationMs = durationMs;
        this.successful = true;
    }
    
    /**
     * Constructor for successful response with separate input/output tokens
     */
    public LLMResponse(String content, int inputTokens, int outputTokens, String model, long durationMs) {
        this();
        this.content = content;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.tokensUsed = inputTokens + outputTokens;
        this.model = model;
        this.durationMs = durationMs;
        this.successful = true;
    }
    
    /**
     * Constructor for error response
     */
    public LLMResponse(String errorMessage) {
        this();
        this.errorMessage = errorMessage;
        this.successful = false;
    }
    
    // Getters and setters
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public int getTokensUsed() {
        return tokensUsed;
    }
    
    public void setTokensUsed(int tokensUsed) {
        this.tokensUsed = tokensUsed;
    }
    
    public int getInputTokens() {
        return inputTokens;
    }
    
    public void setInputTokens(int inputTokens) {
        this.inputTokens = inputTokens;
    }
    
    public int getOutputTokens() {
        return outputTokens;
    }
    
    public void setOutputTokens(int outputTokens) {
        this.outputTokens = outputTokens;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public long getDurationMs() {
        return durationMs;
    }
    
    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
    
    public boolean isSuccessful() {
        return successful;
    }
    
    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.successful = false;
    }
    
    /**
     * Calculates cost per 1000 tokens (assuming pricing is available)
     * @param costPer1kTokens cost per 1000 tokens
     * @return calculated cost for this response
     */
    public double calculateCost(double costPer1kTokens) {
        return (tokensUsed / 1000.0) * costPer1kTokens;
    }
    
    @Override
    public String toString() {
        if (successful) {
            return String.format("LLMResponse[model='%s', tokens=%d, duration=%dms, content='%s']", 
                    model, tokensUsed, durationMs, 
                    content != null && content.length() > 50 ? content.substring(0, 50) + "..." : content);
        } else {
            return String.format("LLMResponse[ERROR: %s]", errorMessage);
        }
    }
} 