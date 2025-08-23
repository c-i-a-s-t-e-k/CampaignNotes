package model;

public class Campain {
    private String uuid;
    private String name;
    private String neo4jLabel;
    private String quadrantCollectionName;
    
    // New required fields for extended schema
    private String userId;
    private String description;
    private long createdAt;
    private long updatedAt;
    private boolean isActive = true;
    private Long deletedAt;
    private String settings;
    
    public String getUuid() {
        return uuid;
    }
    
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getNeo4jLabel() {
        return neo4jLabel;
    }
    
    public void setNeo4jLabel(String neo4jLabel) {
        this.neo4jLabel = neo4jLabel;
    }
    
    public String getQuadrantCollectionName() {
        return quadrantCollectionName;
    }
    
    public void setQuadrantCollectionName(String quadrantCollectionName) {
        this.quadrantCollectionName = quadrantCollectionName;
    }
    
    // Getters and setters for new fields
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
    
    public long getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }
    
    public Long getDeletedAt() {
        return deletedAt;
    }
    
    public void setDeletedAt(Long deletedAt) {
        this.deletedAt = deletedAt;
    }
    
    public String getSettings() {
        return settings;
    }
    
    public void setSettings(String settings) {
        this.settings = settings;
    }
    
    public Campain() {}
    
    public Campain(String uuid, String name, String neo4jLabel, String quadrantCollectionName) {
        this.uuid = uuid;
        this.name = name;
        this.neo4jLabel = neo4jLabel;
        this.quadrantCollectionName = quadrantCollectionName;
        // Set default values for new fields
        this.createdAt = System.currentTimeMillis() / 1000L; // Unix timestamp
        this.updatedAt = this.createdAt;
        this.isActive = true;
    }
    
    public Campain(String uuid, String name, String neo4jLabel, String quadrantCollectionName, 
                   String userId, String description) {
        this(uuid, name, neo4jLabel, quadrantCollectionName);
        this.userId = userId;
        this.description = description;
    }
    
    @Override
    public String toString() {
        return "[" + uuid + "] - " + name + " - " + neo4jLabel + " - " + quadrantCollectionName;
    }
}
