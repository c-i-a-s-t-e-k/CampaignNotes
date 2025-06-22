package CampaignNotes;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

import io.qdrant.client.QdrantClient;
import model.ArtifactCategory;
import model.Campain;

public class Admin {
    
    private static final String DB_PATH = "sqlite.db";
    
    /**
     * Main method to run admin operations
     */
    public static void main(String[] args) {
        System.out.println("=== ADMIN TOOL ===");
        
        Admin admin = new Admin();
        admin.showAdminMenu();
    }
    
    /**
     * Shows admin menu with available operations
     */
    public void showAdminMenu() {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        
        while (running) {
            System.out.println("\n=== ADMIN MENU ===");
            System.out.println("1. Database Cleanup (Delete all campaigns)");
            System.out.println("2. Manage Artifact Categories");
            System.out.println("3. Exit");
            System.out.print("Choose an option (1-3): ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    performFullCleanup();
                    break;
                case "2":
                    manageArtifactCategories();
                    break;
                case "3":
                    running = false;
                    System.out.println("Exiting admin tool.");
                    break;
                default:
                    System.err.println("Invalid option. Please choose 1-3.");
                    break;
            }
        }
    }
    
    /**
     * Manages artifact categories
     */
    private void manageArtifactCategories() {
        Scanner scanner = new Scanner(System.in);
        boolean managing = true;
        ArtifactCategoryService categoryService = new ArtifactCategoryService();
        
        while (managing) {
            System.out.println("\n=== ARTIFACT CATEGORIES MANAGEMENT ===");
            System.out.println("1. View all categories");
            System.out.println("2. View categories for campaign");
            System.out.println("3. Add category to campaign");
            System.out.println("4. Remove category from campaign");
            System.out.println("5. Create new category");
            System.out.println("6. Back to admin menu");
            System.out.print("Choose an option (1-6): ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    viewAllCategories(categoryService);
                    break;
                case "2":
                    viewCategoriesForCampaign(categoryService, scanner);
                    break;
                case "3":
                    addCategoryToCampaign(categoryService, scanner);
                    break;
                case "4":
                    removeCategoryFromCampaign(categoryService, scanner);
                    break;
                case "5":
                    createNewCategory(categoryService, scanner);
                    break;
                case "6":
                    managing = false;
                    break;
                default:
                    System.err.println("Invalid option. Please choose 1-6.");
                    break;
            }
        }
    }
    
    private void viewAllCategories(ArtifactCategoryService categoryService) {
        System.out.println("\n=== ALL ARTIFACT CATEGORIES ===");
        List<ArtifactCategory> categories = categoryService.getAllCategories();
        
        if (categories.isEmpty()) {
            System.out.println("No categories found.");
            return;
        }
        
        for (ArtifactCategory category : categories) {
            System.out.println("• " + category.getName() + ": " + category.getDescription() + 
                             " (Active: " + category.isActive() + ")");
        }
    }
    
    private void viewCategoriesForCampaign(ArtifactCategoryService categoryService, Scanner scanner) {
        System.out.print("Enter campaign UUID: ");
        String campaignUuid = scanner.nextLine().trim();
        
        if (campaignUuid.isEmpty()) {
            System.err.println("Campaign UUID cannot be empty.");
            return;
        }
        
        System.out.println("\n=== CATEGORIES FOR CAMPAIGN " + campaignUuid + " ===");
        Map<String, String> categories = categoryService.getCategoriesForCampaign(campaignUuid);
        
        if (categories.isEmpty()) {
            System.out.println("No categories assigned to this campaign.");
        } else {
            for (Map.Entry<String, String> entry : categories.entrySet()) {
                System.out.println("• " + entry.getKey() + ": " + entry.getValue());
            }
        }
    }
    
    private void addCategoryToCampaign(ArtifactCategoryService categoryService, Scanner scanner) {
        System.out.print("Enter campaign UUID: ");
        String campaignUuid = scanner.nextLine().trim();
        
        if (campaignUuid.isEmpty()) {
            System.err.println("Campaign UUID cannot be empty.");
            return;
        }
        
        System.out.print("Enter category name: ");
        String categoryName = scanner.nextLine().trim();
        
        if (categoryName.isEmpty()) {
            System.err.println("Category name cannot be empty.");
            return;
        }
        
        boolean success = categoryService.addCategoryToCampaign(campaignUuid, categoryName);
        if (success) {
            System.out.println("✅ Category added successfully.");
        } else {
            System.err.println("❌ Failed to add category.");
        }
    }
    
    private void removeCategoryFromCampaign(ArtifactCategoryService categoryService, Scanner scanner) {
        System.out.print("Enter campaign UUID: ");
        String campaignUuid = scanner.nextLine().trim();
        
        if (campaignUuid.isEmpty()) {
            System.err.println("Campaign UUID cannot be empty.");
            return;
        }
        
        System.out.print("Enter category name: ");
        String categoryName = scanner.nextLine().trim();
        
        if (categoryName.isEmpty()) {
            System.err.println("Category name cannot be empty.");
            return;
        }
        
        boolean success = categoryService.removeCategoryFromCampaign(campaignUuid, categoryName);
        if (success) {
            System.out.println("✅ Category removed successfully.");
        } else {
            System.err.println("❌ Failed to remove category or category was not assigned.");
        }
    }
    
    private void createNewCategory(ArtifactCategoryService categoryService, Scanner scanner) {
        System.out.print("Enter category name: ");
        String name = scanner.nextLine().trim();
        
        if (name.isEmpty()) {
            System.err.println("Category name cannot be empty.");
            return;
        }
        
        System.out.print("Enter category description: ");
        String description = scanner.nextLine().trim();
        
        if (description.isEmpty()) {
            System.err.println("Category description cannot be empty.");
            return;
        }
        
        ArtifactCategory category = new ArtifactCategory(name, description);
        boolean success = categoryService.createCategory(category);
        
        if (success) {
            System.out.println("✅ Category created successfully.");
        } else {
            System.err.println("❌ Failed to create category.");
        }
    }
    
    /**
     * Performs full cleanup of all databases after user confirmation
     */
    public void performFullCleanup() {
        try {
            // Initialize campaign manager to access databases
            CampaignManager campaignManager = new CampaignManager();
            
            // Check if databases are available
            if (!campaignManager.checkDatabasesAvailability()) {
                System.err.println("Błąd: Nie można połączyć się z bazami danych!");
                return;
            }
            
            // Get current data to show what will be deleted
            List<Campain> campaigns = campaignManager.getAllCampaigns();
            
            // Show what will be deleted
            displayDataToBeDeleted(campaigns);
            
            // Ask for confirmation
            if (!getUserConfirmation()) {
                System.out.println("Operacja anulowana przez użytkownika.");
                return;
            }
            
            // Perform cleanup
            System.out.println("\nRozpoczynanie czyszczenia baz danych...");
            
            boolean success = true;
            
            // Clean Qdrant collections
            success &= cleanupQdrantCollections(campaigns);
            
            // Clean SQLite campaigns table
            success &= cleanupSQLiteCampaigns();
            
            if (success) {
                System.out.println("\n✅ Czyszczenie zakończone pomyślnie!");
                System.out.println("Wszystkie kampanie zostały usunięte z baz danych.");
            } else {
                System.out.println("\n❌ Wystąpiły błędy podczas czyszczenia baz danych!");
                System.out.println("Sprawdź logi powyżej dla szczegółów.");
            }
            
        } catch (Exception e) {
            System.err.println("Błąd podczas czyszczenia baz danych: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Displays information about data that will be deleted
     */
    private void displayDataToBeDeleted(List<Campain> campaigns) {
        System.out.println("🔍 ANALIZA DANYCH DO USUNIĘCIA:");
        System.out.println("================================");
        
        if (campaigns.isEmpty()) {
            System.out.println("❗ Brak kampanii do usunięcia - bazy danych są już puste.");
            return;
        }
        
        System.out.println("📊 Znaleziono " + campaigns.size() + " kampanii do usunięcia:");
        System.out.println();
        
        for (int i = 0; i < campaigns.size(); i++) {
            Campain campaign = campaigns.get(i);
            System.out.printf("%d. Kampania: %s%n", i + 1, campaign.getName());
            System.out.printf("   UUID: %s%n", campaign.getUuid());
            System.out.printf("   Neo4j Label: %s%n", campaign.getNeo4jLabel());
            System.out.printf("   Qdrant Collection: %s%n", campaign.getQuadrantCollectionName());
            System.out.println();
        }
        
        System.out.println("🗑️  CO ZOSTANIE USUNIĘTE:");
        System.out.println("   • Wszystkie rekordy z tabeli 'campains' w SQLite");
        System.out.println("   • Wszystkie kolekcje w bazie Qdrant");
        System.out.println("   • Neo4j: Brak operacji (nie zaimplementowano jeszcze)");
        System.out.println();
    }
    
    /**
     * Gets user confirmation for the cleanup operation
     */
    private boolean getUserConfirmation() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("⚠️  OSTRZEŻENIE: Ta operacja jest NIEODWRACALNA!");
        System.out.println("Wszystkie dane kampanii zostaną TRWALE usunięte.");
        System.out.println();
        
        System.out.print("Czy na pewno chcesz kontynuować? Wpisz 'TAK' (wielkie litery) aby potwierdzić: ");
        String response = scanner.nextLine().trim();
        
        if (!"TAK".equals(response)) {
            return false;
        }
        
        System.out.println();
        System.out.print("Ostatnie potwierdzenie - wpisz 'USUN WSZYSTKO' aby kontynuować: ");
        String finalConfirmation = scanner.nextLine().trim();
        
        return "USUN WSZYSTKO".equals(finalConfirmation);
    }
    
    /**
     * Cleans up all Qdrant collections for the campaigns
     */
    private boolean cleanupQdrantCollections(List<Campain> campaigns) {
        System.out.println("🧹 Czyszczenie kolekcji Qdrant...");
        
        try {
            DataBaseLoader dbLoader = new DataBaseLoader();
            QdrantClient qdrantClient = dbLoader.getQdrantClient();
            
            if (qdrantClient == null) {
                System.err.println("❌ Nie można uzyskać klienta Qdrant!");
                return false;
            }
            
            boolean allSuccess = true;
            
            for (Campain campaign : campaigns) {
                try {
                    System.out.printf("   Usuwanie kolekcji: %s... ", campaign.getQuadrantCollectionName());
                    
                    // Try to delete the collection
                    qdrantClient.deleteCollectionAsync(campaign.getQuadrantCollectionName()).get();
                    
                    System.out.println("✅ Usunięto");
                    
                } catch (ExecutionException e) {
                    if (e.getCause() != null && e.getCause().getMessage().contains("Not found")) {
                        System.out.println("⚠️  Kolekcja nie istnieje (już usunięta)");
                    } else {
                        System.out.println("❌ Błąd: " + e.getMessage());
                        allSuccess = false;
                    }
                } catch (Exception e) {
                    System.out.println("❌ Błąd: " + e.getMessage());
                    allSuccess = false;
                }
            }
            
            // Close the client connection
            dbLoader.closeConnections();
            
            if (allSuccess) {
                System.out.println("✅ Wszystkie kolekcje Qdrant zostały usunięte");
            }
            
            return allSuccess;
            
        } catch (Exception e) {
            System.err.println("❌ Błąd podczas czyszczenia Qdrant: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Cleans up the SQLite campaigns table
     */
    private boolean cleanupSQLiteCampaigns() {
        System.out.println("🧹 Czyszczenie tabeli campaigns w SQLite...");
        
        String sql = "DELETE FROM campains";
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            int deletedRows = pstmt.executeUpdate();
            
            System.out.printf("✅ Usunięto %d rekordów z tabeli 'campains'%n", deletedRows);
            return true;
            
        } catch (SQLException e) {
            System.err.println("❌ Błąd podczas czyszczenia SQLite: " + e.getMessage());
            return false;
        }
    }
}
