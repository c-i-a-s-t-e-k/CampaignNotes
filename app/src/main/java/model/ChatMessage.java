package model;

/**
 * Represents a single message in a chat prompt.
 * Contains the role (e.g., "system", "user", "assistant") and the message content.
 */
public class ChatMessage {
    
    private final String role;
    private final String content;
    
    /**
     * Constructor for ChatMessage.
     * 
     * @param role the role of the message sender (e.g., "system", "user", "assistant")
     * @param content the content of the message
     */
    public ChatMessage(String role, String content) {
        if (role == null || role.trim().isEmpty()) {
            throw new IllegalArgumentException("Role cannot be null or empty");
        }
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null");
        }
        
        this.role = role.trim();
        this.content = content;
    }
    
    /**
     * Gets the role of the message sender.
     * 
     * @return the role string
     */
    public String getRole() {
        return role;
    }
    
    /**
     * Gets the content of the message.
     * 
     * @return the message content
     */
    public String getContent() {
        return content;
    }
    
    /**
     * Creates a copy of this message with new content.
     * Useful for variable interpolation.
     * 
     * @param newContent the new content for the message
     * @return a new ChatMessage with the same role but new content
     */
    public ChatMessage withContent(String newContent) {
        return new ChatMessage(this.role, newContent);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ChatMessage that = (ChatMessage) obj;
        return role.equals(that.role) && content.equals(that.content);
    }
    
    @Override
    public int hashCode() {
        return java.util.Objects.hash(role, content);
    }
    
    @Override
    public String toString() {
        return "ChatMessage{" +
                "role='" + role + '\'' +
                ", content='" + (content.length() > 100 ? 
                    content.substring(0, 100) + "..." : content) + '\'' +
                '}';
    }
}
