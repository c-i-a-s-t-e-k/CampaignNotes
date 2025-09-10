package CampaignNotes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import model.ChatMessage;

/**
 * Tests for ChatMessage model class.
 * Verifies construction, validation, and utility methods.
 */
public class ChatMessageTest {
    
    @Test
    void shouldCreateChatMessage() {
        ChatMessage message = new ChatMessage("user", "Hello world!");
        
        assertEquals("user", message.getRole());
        assertEquals("Hello world!", message.getContent());
    }
    
    @Test
    void shouldTrimRole() {
        ChatMessage message = new ChatMessage("  system  ", "Test content");
        
        assertEquals("system", message.getRole());
        assertEquals("Test content", message.getContent());
    }
    
    @Test
    void shouldThrowForNullRole() {
        assertThrows(IllegalArgumentException.class, 
            () -> new ChatMessage(null, "Content"));
    }
    
    @Test
    void shouldThrowForEmptyRole() {
        assertThrows(IllegalArgumentException.class, 
            () -> new ChatMessage("", "Content"));
        
        assertThrows(IllegalArgumentException.class, 
            () -> new ChatMessage("   ", "Content"));
    }
    
    @Test
    void shouldThrowForNullContent() {
        assertThrows(IllegalArgumentException.class, 
            () -> new ChatMessage("user", null));
    }
    
    @Test
    void shouldAllowEmptyContent() {
        ChatMessage message = new ChatMessage("system", "");
        
        assertEquals("system", message.getRole());
        assertEquals("", message.getContent());
    }
    
    @Test
    void shouldCreateCopyWithNewContent() {
        ChatMessage original = new ChatMessage("user", "Original content");
        ChatMessage modified = original.withContent("New content");
        
        assertEquals("user", modified.getRole());
        assertEquals("New content", modified.getContent());
        
        // Original should remain unchanged
        assertEquals("Original content", original.getContent());
    }
    
    @Test
    void shouldImplementEquals() {
        ChatMessage message1 = new ChatMessage("user", "Hello");
        ChatMessage message2 = new ChatMessage("user", "Hello");
        ChatMessage message3 = new ChatMessage("system", "Hello");
        ChatMessage message4 = new ChatMessage("user", "World");
        
        assertEquals(message1, message2);
        assertNotEquals(message1, message3);
        assertNotEquals(message1, message4);
        assertNotEquals(message1, null);
        assertNotEquals(message1, "not a ChatMessage");
    }
    
    @Test
    void shouldImplementHashCode() {
        ChatMessage message1 = new ChatMessage("user", "Hello");
        ChatMessage message2 = new ChatMessage("user", "Hello");
        
        assertEquals(message1.hashCode(), message2.hashCode());
    }
    
    @Test
    void shouldProvideToStringRepresentation() {
        ChatMessage message = new ChatMessage("user", "Hello world!");
        String toString = message.toString();
        
        assertTrue(toString.contains("user"));
        assertTrue(toString.contains("Hello world!"));
    }
    
    @Test
    void shouldTruncateLongContentInToString() {
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            longContent.append("a");
        }
        
        ChatMessage message = new ChatMessage("user", longContent.toString());
        String toString = message.toString();
        
        assertTrue(toString.contains("..."));
        assertTrue(toString.length() < longContent.length() + 50); // Should be much shorter
    }
    
    @Test
    void shouldHandleSpecialCharactersInRole() {
        ChatMessage message = new ChatMessage("custom-role_123", "Content");
        
        assertEquals("custom-role_123", message.getRole());
    }
}
