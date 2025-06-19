package model;

import java.util.List;

/**
 * Wrapper class for embedding results containing both the vector and usage information.
 */
public class EmbeddingResult {
    private final List<Double> embedding;
    private final int tokensUsed;
    
    public EmbeddingResult(List<Double> embedding, int tokensUsed) {
        this.embedding = embedding;
        this.tokensUsed = tokensUsed;
    }
    
    public List<Double> getEmbedding() {
        return embedding;
    }
    
    public int getTokensUsed() {
        return tokensUsed;
    }
} 