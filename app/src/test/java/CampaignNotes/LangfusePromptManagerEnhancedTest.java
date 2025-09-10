package CampaignNotes;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import model.ChatMessage;
import model.PromptContent;

/**
 * Enhanced tests for PromptContent and ChatMessage models.
 * Tests type-safe functionality, preprocessing, and enhanced variable interpolation.
 * These tests focus on the model classes rather than the full LangfusePromptManager integration.
 */
public class LangfusePromptManagerEnhancedTest {
    
    @Test
    void shouldCreateAndManipulateTextPromptContent() {
        // Test text prompt content creation and manipulation
        JsonObject mockRawData = createMockTextPrompt("Hello {{name}}!");
        String textContent = "Hello {{name}}!";
        PromptContent content = new PromptContent(PromptContent.PromptType.TEXT, textContent, mockRawData);
        
        assertNotNull(content);
        assertTrue(content.isText());
        assertFalse(content.isChat());
        assertEquals("Hello {{name}}!", content.asText());
        assertEquals(PromptContent.PromptType.TEXT, content.getType());
        assertEquals(mockRawData, content.getRawData());
    }
    
    @Test
    void shouldCreateAndManipulateChatPromptContent() {
        // Test chat prompt content creation and manipulation
        JsonObject mockRawData = createMockChatPrompt();
        List<ChatMessage> messages = Arrays.asList(
            new ChatMessage("system", "You are a helpful assistant."),
            new ChatMessage("user", "Hello {{name}}!")
        );
        
        PromptContent content = new PromptContent(PromptContent.PromptType.CHAT, messages, mockRawData);
        
        assertNotNull(content);
        assertTrue(content.isChat());
        assertFalse(content.isText());
        assertEquals(PromptContent.PromptType.CHAT, content.getType());
        
        List<ChatMessage> retrievedMessages = content.asChatMessages();
        assertEquals(2, retrievedMessages.size());
        assertEquals("system", retrievedMessages.get(0).getRole());
        assertEquals("You are a helpful assistant.", retrievedMessages.get(0).getContent());
        assertEquals("user", retrievedMessages.get(1).getRole());
        assertEquals("Hello {{name}}!", retrievedMessages.get(1).getContent());
    }
    
    @Test
    void shouldConvertChatToFormattedText() {
        // Test conversion of chat messages to formatted text
        List<ChatMessage> messages = Arrays.asList(
            new ChatMessage("system", "You are a helpful assistant."),
            new ChatMessage("user", "What is AI?"),
            new ChatMessage("assistant", "AI stands for Artificial Intelligence.")
        );
        
        PromptContent content = new PromptContent(PromptContent.PromptType.CHAT, messages, new JsonObject());
        String expectedText = "[SYSTEM]: You are a helpful assistant.\n" +
                             "[USER]: What is AI?\n" +
                             "[ASSISTANT]: AI stands for Artificial Intelligence.";
        
        assertEquals(expectedText, content.asText());
    }
    
    @Test
    void shouldThrowWhenConvertingTextToChatMessages() {
        // Test that text prompts can't be converted to chat messages
        String textContent = "Simple text prompt";
        PromptContent content = new PromptContent(PromptContent.PromptType.TEXT, textContent, new JsonObject());
        
        assertThrows(IllegalStateException.class, content::asChatMessages);
    }
    
    @Test
    void shouldHandleComplexChatMessagesWithVariables() {
        // Test complex chat structure with variables
        List<ChatMessage> messages = Arrays.asList(
            new ChatMessage("system", "You are {{assistant_type}} helping {{user_name}}."),
            new ChatMessage("user", "Hello {{assistant_name}}, I need help with {{topic}}."),
            new ChatMessage("assistant", "Hello {{user_name}}! I'd be happy to help with {{topic}}.")
        );
        
        PromptContent content = new PromptContent(PromptContent.PromptType.CHAT, messages, new JsonObject());
        
        // Verify structure
        List<ChatMessage> retrievedMessages = content.asChatMessages();
        assertEquals(3, retrievedMessages.size());
        
        // Verify variable placeholders are preserved
        assertTrue(retrievedMessages.get(0).getContent().contains("{{assistant_type}}"));
        assertTrue(retrievedMessages.get(1).getContent().contains("{{topic}}"));
        assertTrue(retrievedMessages.get(2).getContent().contains("{{user_name}}"));
    }
    
    @Test
    void shouldCreateChatMessageWithNewContent() {
        // Test ChatMessage withContent method
        ChatMessage original = new ChatMessage("user", "Original {{variable}}");
        ChatMessage modified = original.withContent("Modified content");
        
        assertEquals("user", modified.getRole());
        assertEquals("Modified content", modified.getContent());
        
        // Original should remain unchanged
        assertEquals("Original {{variable}}", original.getContent());
    }
    
    @Test
    void shouldProvideProperToStringForPromptContent() {
        // Test toString representations
        String textContent = "Test content";
        PromptContent textPrompt = new PromptContent(PromptContent.PromptType.TEXT, textContent, new JsonObject());
        
        String textToString = textPrompt.toString();
        assertTrue(textToString.contains("TEXT"));
        assertTrue(textToString.contains("contentLength=12"));
        
        List<ChatMessage> messages = Arrays.asList(
            new ChatMessage("system", "Test"),
            new ChatMessage("user", "Hello")
        );
        PromptContent chatPrompt = new PromptContent(PromptContent.PromptType.CHAT, messages, new JsonObject());
        
        String chatToString = chatPrompt.toString();
        assertTrue(chatToString.contains("CHAT"));
        assertTrue(chatToString.contains("contentLength=2"));
    }
    
    // Helper methods for creating mock data
    
    private JsonObject createMockTextPrompt(String content) {
        JsonObject promptData = new JsonObject();
        promptData.addProperty("type", "text");
        promptData.addProperty("prompt", content);
        return promptData;
    }
    
    private JsonObject createMockChatPrompt() {
        JsonObject promptData = new JsonObject();
        promptData.addProperty("type", "chat");
        
        JsonArray messages = new JsonArray();
        
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", "You are a helpful assistant.");
        messages.add(systemMessage);
        
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", "Hello {{name}}!");
        messages.add(userMessage);
        
        promptData.add("prompt", messages);
        return promptData;
    }
}
