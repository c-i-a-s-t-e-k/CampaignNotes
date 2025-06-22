package model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Model representing an artifact category with its description and status.
 * Categories define the types of artifacts that can be extracted from campaign notes.
 */
public class ArtifactCategory implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String name;
    private String description;
    private boolean isActive;
    private LocalDateTime createdAt;
    
    /**
     * Default constructor
     */
    public ArtifactCategory() {
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
    }
    
    /**
     * Constructor with essential fields
     */
    public ArtifactCategory(String name, String description) {
        this();
        this.name = name;
        this.description = description;
    }
    
    /**
     * Constructor with all fields
     */
    public ArtifactCategory(String name, String description, boolean isActive) {
        this(name, description);
        this.isActive = isActive;
    }
    
    // Getters and setters
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    /**
     * Validates the artifact category according to business rules.
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return name != null && !name.trim().isEmpty() &&
               description != null && !description.trim().isEmpty();
    }
    
    @Override
    public String toString() {
        return String.format("ArtifactCategory[name='%s', description='%s', active=%b]", 
                name, description, isActive);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ArtifactCategory that = (ArtifactCategory) obj;
        return name != null ? name.equals(that.name) : that.name == null;
    }
    
    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
} 