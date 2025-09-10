package model;

import java.util.List;

import com.google.gson.JsonObject;

/**
 * Represents processed prompt content with type information.
 * Supports both text and chat prompt types while preserving the original structure.
 * 
 * This class provides type-safe access to prompt content and allows for different
 * output formats from the same cached data.
 */
public class PromptContent {
    
    private final PromptType type;
    private final Object content;
    private final JsonObject rawData;
    
    /**
     * Enum defining supported prompt types.
     */
    public enum PromptType {
        TEXT, CHAT
    }
    
    /**
     * Constructor for PromptContent.
     * 
     * @param type the type of prompt (TEXT or CHAT)
     * @param content the processed content (String for text, List<ChatMessage> for chat)
     * @param rawData the original JSON data from API
     */
    public PromptContent(PromptType type, Object content, JsonObject rawData) {
        this.type = type;
        this.content = content;
        this.rawData = rawData;
    }
    
    /**
     * Checks if this prompt is of chat type.
     * 
     * @return true if this is a chat prompt
     */
    public boolean isChat() {
        return type == PromptType.CHAT;
    }
    
    /**
     * Checks if this prompt is of text type.
     * 
     * @return true if this is a text prompt
     */
    public boolean isText() {
        return type == PromptType.TEXT;
    }
    
    /**
     * Gets the prompt type.
     * 
     * @return the PromptType enum value
     */
    public PromptType getType() {
        return type;
    }
    
    /**
     * Returns the content as text.
     * For chat prompts, converts to formatted string with [ROLE]: content format.
     * For text prompts, returns the string directly.
     * 
     * @return string representation of the prompt content
     */
    public String asText() {
        if (type == PromptType.TEXT) {
            return (String) content;
        } else if (type == PromptType.CHAT) {
            @SuppressWarnings("unchecked")
            List<ChatMessage> messages = (List<ChatMessage>) content;
            StringBuilder result = new StringBuilder();
            
            for (int i = 0; i < messages.size(); i++) {
                ChatMessage message = messages.get(i);
                if (i > 0) {
                    result.append("\n");
                }
                result.append("[").append(message.getRole().toUpperCase()).append("]: ")
                      .append(message.getContent());
            }
            
            return result.toString();
        }
        
        throw new IllegalStateException("Unknown prompt type: " + type);
    }
    
    /**
     * Returns the content as a list of chat messages.
     * Only valid for chat prompts.
     * 
     * @return list of ChatMessage objects
     * @throws IllegalStateException if called on a text prompt
     */
    @SuppressWarnings("unchecked")
    public List<ChatMessage> asChatMessages() {
        if (type != PromptType.CHAT) {
            throw new IllegalStateException("Cannot convert text prompt to chat messages");
        }
        return (List<ChatMessage>) content;
    }
    
    /**
     * Gets the raw JSON data from the API response.
     * 
     * @return JsonObject containing original API response
     */
    public JsonObject getRawData() {
        return rawData;
    }
    
    /**
     * Gets the processed content object.
     * This is either a String (for text) or List<ChatMessage> (for chat).
     * 
     * @return the processed content object
     */
    public Object getContent() {
        return content;
    }
    
    @Override
    public String toString() {
        return "PromptContent{" +
                "type=" + type +
                ", contentLength=" + (content != null ? 
                    (type == PromptType.TEXT ? ((String) content).length() : 
                     ((List<?>) content).size()) : 0) +
                '}';
    }
}
