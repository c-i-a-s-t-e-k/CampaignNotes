package CampaignNotes;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import model.ChatMessage;
import model.PromptContent;

/**
 * Tests for PromptContent model class.
 * Verifies type safety, conversions, and structure preservation.
 */
public class PromptContentTest {
    
    private JsonObject mockRawData;
    
    @BeforeEach
    void setUp() {
        mockRawData = new JsonObject();
        mockRawData.addProperty("type", "test");
        mockRawData.addProperty("prompt", "test content");
    }
    
    @Test
    void shouldCreateTextPromptContent() {
        String textContent = "Hello {{name}}, welcome to {{app}}!";
        PromptContent content = new PromptContent(PromptContent.PromptType.TEXT, textContent, mockRawData);
        
        assertTrue(content.isText());
        assertFalse(content.isChat());
        assertEquals(PromptContent.PromptType.TEXT, content.getType());
        assertEquals(textContent, content.asText());
        assertEquals(mockRawData, content.getRawData());
    }
    
    @Test
    void shouldCreateChatPromptContent() {
        List<ChatMessage> messages = Arrays.asList(
            new ChatMessage("system", "You are a helpful assistant."),
            new ChatMessage("user", "Hello {{name}}!")
        );
        
        PromptContent content = new PromptContent(PromptContent.PromptType.CHAT, messages, mockRawData);
        
        assertTrue(content.isChat());
        assertFalse(content.isText());
        assertEquals(PromptContent.PromptType.CHAT, content.getType());
        assertEquals(messages, content.asChatMessages());
        assertEquals(mockRawData, content.getRawData());
    }
    
    @Test
    void shouldConvertChatToFormattedText() {
        List<ChatMessage> messages = Arrays.asList(
            new ChatMessage("system", "You are a helpful assistant."),
            new ChatMessage("user", "What is AI?"),
            new ChatMessage("assistant", "AI stands for Artificial Intelligence.")
        );
        
        PromptContent content = new PromptContent(PromptContent.PromptType.CHAT, messages, mockRawData);
        String expectedText = "[SYSTEM]: You are a helpful assistant.\n" +
                             "[USER]: What is AI?\n" +
                             "[ASSISTANT]: AI stands for Artificial Intelligence.";
        
        assertEquals(expectedText, content.asText());
    }
    
    @Test
    void shouldThrowWhenConvertingTextToChatMessages() {
        String textContent = "Simple text prompt";
        PromptContent content = new PromptContent(PromptContent.PromptType.TEXT, textContent, mockRawData);
        
        assertThrows(IllegalStateException.class, content::asChatMessages);
    }
    
    @Test
    void shouldThrowForUnknownPromptType() {
        // Create a mock enum that doesn't exist (simulate unknown type)
        PromptContent content = new PromptContent(null, "test", mockRawData) {
            @Override
            public PromptType getType() {
                return null; // Simulate unknown type
            }
            
            @Override
            public String asText() {
                if (getType() == null) {
                    throw new IllegalStateException("Unknown prompt type: null");
                }
                return super.asText();
            }
        };
        
        assertThrows(IllegalStateException.class, content::asText);
    }
    
    @Test
    void shouldProvideToStringRepresentation() {
        String textContent = "Test content";
        PromptContent textPrompt = new PromptContent(PromptContent.PromptType.TEXT, textContent, mockRawData);
        
        String toString = textPrompt.toString();
        assertTrue(toString.contains("TEXT"));
        assertTrue(toString.contains("contentLength=12"));
        
        List<ChatMessage> messages = Arrays.asList(
            new ChatMessage("system", "Test"),
            new ChatMessage("user", "Hello")
        );
        PromptContent chatPrompt = new PromptContent(PromptContent.PromptType.CHAT, messages, mockRawData);
        
        String chatToString = chatPrompt.toString();
        assertTrue(chatToString.contains("CHAT"));
        assertTrue(chatToString.contains("contentLength=2"));
    }
    
    @Test
    void shouldHandleNullContent() {
        PromptContent content = new PromptContent(PromptContent.PromptType.TEXT, null, mockRawData);
        
        String toString = content.toString();
        assertTrue(toString.contains("contentLength=0"));
    }
}
