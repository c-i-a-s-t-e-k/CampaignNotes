package model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Model representing a campaign note.
 * Each note belongs to a specific campaign and contains content that can be embedded and stored in Qdrant.
 */
public class Note {
    private static final UUID NAMESPACE_UUID = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
    
    private String id;
    private String campaignUuid;
    private String title;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean isOverride;
    private String overrideReason;
    private boolean isOverridden;
    private List<String> overriddenByNoteIds;
    
    /**
     * Default constructor
     */
    public Note() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.isOverride = false;
        this.isOverridden = false;
        this.overriddenByNoteIds = new ArrayList<>();
    }
    
    /**
     * Constructor with basic fields
     */
    public Note(String campaignUuid, String title, String content) {
        this();
        this.campaignUuid = campaignUuid;
        this.title = title;
        this.content = content;
        this.id = generateContentBasedUUID(title, content);
    }
    
    /**
     * Constructor for override notes
     */
    public Note(String campaignUuid, String title, String content, String overrideReason) {
        this(campaignUuid, title, content);
        this.isOverride = true;
        this.overrideReason = overrideReason;
    }
    
    /**
     * Generates a UUID v5 based on the note's title and content.
     * This ensures that notes with identical content get the same UUID, preventing duplicates.
     * @param title the note title
     * @param content the note content
     * @return UUID v5 string
     */
    private String generateContentBasedUUID(String title, String content) {
        try {
            // Combine title and content for hashing
            String combinedContent = (title != null ? title : "") + "\n\n" + (content != null ? content : "");
            
            // Create SHA-1 hash of the combined content
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest(combinedContent.getBytes(StandardCharsets.UTF_8));
            
            // Convert namespace UUID to bytes
            byte[] namespaceBytes = toBytes(NAMESPACE_UUID);
            
            // Combine namespace and hash
            byte[] combined = new byte[namespaceBytes.length + hash.length];
            System.arraycopy(namespaceBytes, 0, combined, 0, namespaceBytes.length);
            System.arraycopy(hash, 0, combined, namespaceBytes.length, hash.length);
            
            // Hash the combined bytes
            byte[] finalHash = sha1.digest(combined);
            
            // Set version (5) and variant bits according to RFC 4122
            finalHash[6] &= 0x0F;  // Clear version bits
            finalHash[6] |= 0x50;  // Set version to 5
            finalHash[8] &= 0x3F;  // Clear variant bits
            finalHash[8] |= 0x80;  // Set variant to 10
            
            // Convert to UUID
            return fromBytes(finalHash).toString();
            
        } catch (NoSuchAlgorithmException e) {
            // Fallback to random UUID if SHA-1 is not available
            System.err.println("SHA-1 not available, falling back to random UUID: " + e.getMessage());
            return UUID.randomUUID().toString();
        }
    }
    
    /**
     * Converts UUID to byte array
     */
    private byte[] toBytes(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        byte[] buffer = new byte[16];
        
        for (int i = 0; i < 8; i++) {
            buffer[i] = (byte) (msb >>> 8 * (7 - i));
        }
        for (int i = 8; i < 16; i++) {
            buffer[i] = (byte) (lsb >>> 8 * (7 - i));
        }
        
        return buffer;
    }
    
    /**
     * Converts byte array to UUID
     */
    private UUID fromBytes(byte[] bytes) {
        long msb = 0;
        long lsb = 0;
        
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xFF);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (bytes[i] & 0xFF);
        }
        
        return new UUID(msb, lsb);
    }
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getCampaignUuid() {
        return campaignUuid;
    }
    
    public void setCampaignUuid(String campaignUuid) {
        this.campaignUuid = campaignUuid;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
        this.updatedAt = LocalDateTime.now();
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public boolean isOverride() {
        return isOverride;
    }
    
    public void setOverride(boolean override) {
        this.isOverride = override;
    }
    
    public String getOverrideReason() {
        return overrideReason;
    }
    
    public void setOverrideReason(String overrideReason) {
        this.overrideReason = overrideReason;
    }
    
    public boolean isOverridden() {
        return isOverridden;
    }
    
    public void setOverridden(boolean overridden) {
        this.isOverridden = overridden;
    }
    
    public List<String> getOverriddenByNoteIds() {
        return new ArrayList<>(overriddenByNoteIds);
    }
    
    public void addOverriddenByNoteId(String noteId) {
        if (!overriddenByNoteIds.contains(noteId)) {
            overriddenByNoteIds.add(noteId);
            this.isOverridden = true;
            this.updatedAt = LocalDateTime.now();
        }
    }
    
    public void removeOverriddenByNoteId(String noteId) {
        overriddenByNoteIds.remove(noteId);
        if (overriddenByNoteIds.isEmpty()) {
            this.isOverridden = false;
        }
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Validates the note content according to business rules.
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        // Check word count limit (500 words as per PRD)
        String[] words = content.trim().split("\\s+");
        if (words.length > 500) {
            return false;
        }
        
        return title != null && !title.trim().isEmpty() && 
               campaignUuid != null && !campaignUuid.trim().isEmpty();
    }
    
    /**
     * Gets the full text content for embedding (title + content)
     * @return combined text for embedding
     */
    public String getFullTextForEmbedding() {
        return title + "\n\n" + content;
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s - %s (%s words, override: %b, overridden: %b)", 
            id, title, 
            content.length() > 50 ? content.substring(0, 50) + "..." : content,
            content.trim().split("\\s+").length,
            isOverride, isOverridden);
    }
} 