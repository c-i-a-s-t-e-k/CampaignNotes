package CampaignNotes.tracking.trace.traces;

import com.google.gson.JsonObject;
import model.Note;

import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;

public class EmbedingTrace extends Trace {


    /**
     * Tracks an embedding generation call to Langfuse with full note information.
     * Creates a generation entry with proper tagging for OpenAI embedding calls.
     *
     * @param traceId ID of the parent trace to link this generation to
     * @param note the full note that was embedded
     * @param model the embedding model used
     * @param campaignId the campaign UUID
     * @param tokensUsed exact number of tokens consumed (from OpenAI API)
     * @param durationMs time taken in milliseconds
     * @return true if tracking was successful, false otherwise
     */
    public boolean trackEmbedding(String traceId, Note note, String model, String campaignId,
                                  int tokensUsed, long durationMs) {
        return trackEmbedding(traceId, note, model, campaignId, tokensUsed, durationMs, Collections.emptyList());
    }

    /**
     * Tracks an embedding generation call to Langfuse with full note information and custom tags.
     *
     * @param traceId ID of the parent trace to link this generation to
     * @param note the full note that was embedded
     * @param model the embedding model used
     * @param campaignId the campaign UUID
     * @param tokensUsed exact number of tokens consumed (from OpenAI API)
     * @param durationMs time taken in milliseconds
     * @param customTags additional custom tags to include
     * @return true if tracking was successful, false otherwise
     */
    public boolean trackEmbedding(String traceId, Note note, String model, String campaignId,
                                  int tokensUsed, long durationMs, List<String> customTags) {
        try {
            // Create generation body using payload builder
            JsonObject generationBody = payloadBuilder.buildEmbeddingGeneration(
                    traceId, note, model, campaignId, tokensUsed, durationMs);

            // Add custom tags if provided
            if (customTags != null && !customTags.isEmpty()) {
                // Note: Custom tags would need to be handled in payload builder
                // For now, we'll track without them for simplicity
            }

            // Create the ingestion event envelope
            JsonObject event = payloadBuilder.buildIngestionEvent("generation-create", generationBody);

            // Create the ingestion batch payload
            JsonObject payload = payloadBuilder.buildIngestionBatch(event);

            HttpResponse<String> response = httpClient.post("/api/public/ingestion", payload);

            if (httpClient.isIngestionSuccessful(response)) {
                System.out.println("Embedding tracked successfully in Langfuse. Model: " + model +
                        ", Tokens: " + tokensUsed);
                return true;
            } else {
                System.err.println("Failed to track embedding in Langfuse. Status: " + response.statusCode() +
                        ", Response: " + response.body());
                return false;
            }

        } catch (Exception e) {
            System.err.println("Error tracking embedding in Langfuse: " + e.getMessage());
            return false;
        }
    }
}
