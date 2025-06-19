package model;

/**
 * Class to hold model pricing information retrieved from Langfuse.
 */
public class ModelPricing {
    private final String modelName;
    private final double inputTokenPrice;
    private final double outputTokenPrice;
    private final double totalTokenPrice;
    
    public ModelPricing(String modelName, double inputTokenPrice, double outputTokenPrice, double totalTokenPrice) {
        this.modelName = modelName;
        this.inputTokenPrice = inputTokenPrice;
        this.outputTokenPrice = outputTokenPrice;
        this.totalTokenPrice = totalTokenPrice;
    }
    
    public String getModelName() { return modelName; }
    public double getInputTokenPrice() { return inputTokenPrice; }
    public double getOutputTokenPrice() { return outputTokenPrice; }
    public double getTotalTokenPrice() { return totalTokenPrice; }
    
    public double calculateCost(int tokens) {
        // For embeddings, all tokens are input tokens
        return (inputTokenPrice > 0 ? inputTokenPrice : totalTokenPrice) * tokens / 1000.0;
    }
} 