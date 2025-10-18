package CampaignNotes;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import model.ArtifactCategory;

/**
 * Service for managing artifact categories in SQLite database.
 * Provides CRUD operations for artifact categories and their campaign associations.
 */
public class ArtifactCategoryService {
    
    private static final String DB_PATH = "sqlite.db";
    
    /**
     * Gets all artifact categories available for a specific campaign.
     * 
     * @param campaignUuid the UUID of the campaign
     * @return Map of category name to description
     */
    public Map<String, String> getCategoriesForCampaign(String campaignUuid) {
        Map<String, String> categories = new HashMap<>();
        String sql = """
            SELECT ac.name, ac.description 
            FROM artifact_categories ac
            JOIN artifact_categories_to_campaigns actc ON ac.name = actc.category_name
            WHERE actc.campaign_uuid = ? AND ac.is_active = 1
            ORDER BY ac.name
            """;
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, campaignUuid);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String description = rs.getString("description");
                    categories.put(name, description);
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error retrieving categories for campaign: " + e.getMessage());
        }
        
        return categories;
    }
    
    /**
     * Adds a category to a campaign.
     * 
     * @param campaignUuid the UUID of the campaign
     * @param categoryName the name of the category to add
     * @return true if added successfully, false otherwise
     */
    public boolean addCategoryToCampaign(String campaignUuid, String categoryName) {
        // First check if the category exists
        if (!categoryExists(categoryName)) {
            System.err.println("Category does not exist: " + categoryName);
            return false;
        }
        
        // Check if the association already exists
        if (isCategoryAssignedToCampaign(campaignUuid, categoryName)) {
            return true; // Consider this a success since the desired state is achieved
        }
        
        String sql = "INSERT INTO artifact_categories_to_campaigns (campaign_uuid, category_name) VALUES (?, ?)";
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, campaignUuid);
            pstmt.setString(2, categoryName);
            
            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows > 0) {
                return true;
            }
            
        } catch (SQLException e) {
            System.err.println("Error adding category to campaign: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Removes a category from a campaign.
     * 
     * @param campaignUuid the UUID of the campaign
     * @param categoryName the name of the category to remove
     * @return true if removed successfully, false otherwise
     */
    public boolean removeCategoryFromCampaign(String campaignUuid, String categoryName) {
        String sql = "DELETE FROM artifact_categories_to_campaigns WHERE campaign_uuid = ? AND category_name = ?";
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, campaignUuid);
            pstmt.setString(2, categoryName);
            
            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows > 0) {
                return true;
            } else {
                return false;
            }
            
        } catch (SQLException e) {
            System.err.println("Error removing category from campaign: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets all available artifact categories.
     * 
     * @return List of all artifact categories
     */
    public List<ArtifactCategory> getAllCategories() {
        List<ArtifactCategory> categories = new ArrayList<>();
        String sql = "SELECT name, description, is_active, created_at FROM artifact_categories ORDER BY name";
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                ArtifactCategory category = new ArtifactCategory();
                category.setName(rs.getString("name"));
                category.setDescription(rs.getString("description"));
                category.setActive(rs.getBoolean("is_active"));
                
                // Parse created_at timestamp
                String createdAtStr = rs.getString("created_at");
                if (createdAtStr != null) {
                    try {
                        category.setCreatedAt(LocalDateTime.parse(createdAtStr.replace(" ", "T")));
                    } catch (Exception e) {
                        // If parsing fails, use current time
                        category.setCreatedAt(LocalDateTime.now());
                    }
                }
                
                categories.add(category);
            }
            
        } catch (SQLException e) {
            System.err.println("Error retrieving all categories: " + e.getMessage());
        }
        
        return categories;
    }
    
    /**
     * Creates a new artifact category.
     * 
     * @param category the category to create
     * @return true if created successfully, false otherwise
     */
    public boolean createCategory(ArtifactCategory category) {
        if (!category.isValid()) {
            System.err.println("Invalid category data");
            return false;
        }
        
        String sql = "INSERT INTO artifact_categories (name, description, is_active) VALUES (?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, category.getName());
            pstmt.setString(2, category.getDescription());
            pstmt.setBoolean(3, category.isActive());
            
            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows > 0) {
                return true;
            }
            
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                System.err.println("Category already exists: " + category.getName());
            } else {
                System.err.println("Error creating category: " + e.getMessage());
            }
        }
        
        return false;
    }
    
    /**
     * Updates an existing artifact category.
     * 
     * @param category the category with updated data
     * @return true if updated successfully, false otherwise
     */
    public boolean updateCategory(ArtifactCategory category) {
        if (!category.isValid()) {
            System.err.println("Invalid category data");
            return false;
        }
        
        String sql = "UPDATE artifact_categories SET description = ?, is_active = ? WHERE name = ?";
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, category.getDescription());
            pstmt.setBoolean(2, category.isActive());
            pstmt.setString(3, category.getName());
            
            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows > 0) {
                return true;
            } else {
                System.err.println("Category not found: " + category.getName());
                return false;
            }
            
        } catch (SQLException e) {
            System.err.println("Error updating category: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Assigns default categories to a new campaign.
     * 
     * @param campaignUuid the UUID of the campaign
     * @return true if all default categories were assigned successfully
     */
    public boolean assignDefaultCategoriesToCampaign(String campaignUuid) {
        String[] defaultCategories = {"characters", "locations", "items", "events"};
        boolean allSuccessful = true;
        
        for (String categoryName : defaultCategories) {
            if (!addCategoryToCampaign(campaignUuid, categoryName)) {
                allSuccessful = false;
                System.err.println("Failed to assign default category: " + categoryName);
            }
        }
        
        return allSuccessful;
    }
    
    /**
     * Checks if a specific category exists.
     * 
     * @param categoryName the name of the category to check
     * @return true if the category exists and is active, false otherwise
     */
    private boolean categoryExists(String categoryName) {
        String sql = "SELECT COUNT(*) FROM artifact_categories WHERE name = ? AND is_active = 1";
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, categoryName);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error checking category existence: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Checks if a category is already assigned to a campaign.
     * 
     * @param campaignUuid the UUID of the campaign
     * @param categoryName the name of the category
     * @return true if the category is assigned to the campaign, false otherwise
     */
    private boolean isCategoryAssignedToCampaign(String campaignUuid, String categoryName) {
        String sql = "SELECT COUNT(*) FROM artifact_categories_to_campaigns WHERE campaign_uuid = ? AND category_name = ?";
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, campaignUuid);
            pstmt.setString(2, categoryName);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Error checking category assignment: " + e.getMessage());
        }
        
        return false;
    }
} 