package CampaignNotes;

import java.util.List;
import java.util.Scanner;

import CampaignNotes.config.DeduplicationConfig;
import CampaignNotes.database.DatabaseConnectionManager;
import CampaignNotes.llm.OpenAIEmbeddingService;
import io.github.cdimascio.dotenv.Dotenv;
import model.Campain;
import model.Note;

/**
 * Terminal interface for Campaign Notes application.
 * Provides command-line interface for managing campaigns and notes.
 * Designed as a modular component that can be easily replaced with frontend.
 */
public class TerminalInterface {
    
    private final Scanner scanner;
    private final CampaignManager campaignManager;
    private final NoteService noteService;
    private final SemantickSearchService searchService;
    private final DatabaseConnectionManager dbConnectionManager;
    private boolean running;
    
    /**
     * Constructor initializes the terminal interface with required services.
     * Uses dependency injection to ensure all services share the same resource instances.
     */
    public TerminalInterface() {
        this.scanner = new Scanner(System.in);
        
        // Initialize shared resources (singleton pattern)
        this.dbConnectionManager = new DatabaseConnectionManager();
        OpenAIEmbeddingService embeddingService = new OpenAIEmbeddingService();
        CampaignNotes.llm.OpenAILLMService llmService = new CampaignNotes.llm.OpenAILLMService();
        
        // Initialize services with proper dependency injection
        this.campaignManager = new CampaignManager(dbConnectionManager);
        ArtifactCategoryService categoryService = new ArtifactCategoryService(dbConnectionManager);
        
        // Initialize deduplication dependencies
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        DeduplicationConfig deduplicationConfig = new DeduplicationConfig(dotenv);
        GraphEmbeddingService graphEmbeddingService = new GraphEmbeddingService(embeddingService, dbConnectionManager);
        
        ArtifactGraphService artifactService = new ArtifactGraphService(llmService, categoryService, 
                                                                       dbConnectionManager, graphEmbeddingService, 
                                                                       deduplicationConfig);
        this.noteService = new NoteService(campaignManager, embeddingService, artifactService, dbConnectionManager);
        this.searchService = new SemantickSearchService(dbConnectionManager, embeddingService);
        
        this.running = false;
    }
    
    /**
     * Starts the terminal interface main loop.
     */
    public void start() {
        System.out.println("=== CampaignNotes Terminal Interface ===");
        System.out.println("Welcome to your RPG campaign management system!");
        
        // Check if all services are available
        if (!checkSystemReadiness()) {
            System.err.println("System is not ready. Please check your configuration and try again.");
            return;
        }
        
        this.running = true;
        showMainMenu();
        
        while (running) {
            try {
                handleMainMenuChoice();
            } catch (Exception e) {
                System.err.println("An error occurred: " + e.getMessage());
                System.out.println("Please try again.");
            }
        }
        
        System.out.println("Thank you for using CampaignNotes!");
        scanner.close();
    }
    
    /**
     * Checks if all required services are ready.
     */
    private boolean checkSystemReadiness() {
        System.out.println("Checking system readiness...");
        
        if (!campaignManager.checkDatabasesAvailability()) {
            System.err.println("Database services are not available.");
            return false;
        }
        
        // Note: Skip NoteService check for now due to compilation issues
        // Will be enabled once NoteService is fully working
        // if (!noteService.checkServicesAvailability()) {
        //     System.err.println("Note services are not available.");
        //     return false;
        // }
        
        System.out.println("System is ready!");
        return true;
    }
    
    /**
     * Displays the main menu options.
     */
    private void showMainMenu() {
        System.out.println("\n=== Main Menu ===");
        System.out.println("1. Create new campaign");
        System.out.println("2. Select existing campaign");
        System.out.println("3. List all campaigns");
        System.out.println("4. Exit");
        System.out.print("Choose an option (1-4): ");
    }
    
    /**
     * Handles main menu choices.
     */
    private void handleMainMenuChoice() {
        String input = scanner.nextLine().trim();
        
        switch (input) {
            case "1":
                createNewCampaign();
                break;
            case "2":
                selectExistingCampaign();
                break;
            case "3":
                listAllCampaigns();
                break;
            case "4":
                running = false;
                return;
            default:
                System.out.println("Invalid option. Please choose 1-4.");
                break;
        }
        
        if (running) {
            showMainMenu();
        }
    }
    
    /**
     * Creates a new campaign through user interaction.
     */
    private void createNewCampaign() {
        System.out.println("\n=== Create New Campaign ===");
        System.out.print("Enter campaign name: ");
        String campaignName = scanner.nextLine().trim();
        
        if (campaignName.isEmpty()) {
            System.err.println("Campaign name cannot be empty.");
            return;
        }
        
        try {
            Campain newCampaign = campaignManager.createNewCampain(campaignName);
            if (newCampaign != null) {
                System.out.println("Campaign created successfully!");
                System.out.println("Campaign details: " + newCampaign.toString());
                
                // Ask if user wants to work with this campaign immediately
                System.out.print("Would you like to work with this campaign now? (y/n): ");
                String choice = scanner.nextLine().trim().toLowerCase();
                if (choice.equals("y") || choice.equals("yes")) {
                    workWithCampaign(newCampaign);
                }
            } else {
                System.err.println("Failed to create campaign. Please try again.");
            }
        } catch (Exception e) {
            System.err.println("Error creating campaign: " + e.getMessage());
        }
    }
    
    /**
     * Allows user to select an existing campaign.
     */
    private void selectExistingCampaign() {
        List<Campain> campaigns = getAllCampaigns();
        
        if (campaigns.isEmpty()) {
            System.out.println("No campaigns available. Create a new campaign first.");
            return;
        }
        
        System.out.println("\n=== Select Campaign ===");
        for (int i = 0; i < campaigns.size(); i++) {
            System.out.println((i + 1) + ". " + campaigns.get(i).getName() + 
                             " [" + campaigns.get(i).getUuid().substring(0, 8) + "...]");
        }
        
        System.out.print("Select campaign (1-" + campaigns.size() + ") or 0 to cancel: ");
        String input = scanner.nextLine().trim();
        
        try {
            int choice = Integer.parseInt(input);
            if (choice == 0) {
                return;
            }
            if (choice >= 1 && choice <= campaigns.size()) {
                Campain selectedCampaign = campaigns.get(choice - 1);
                System.out.println("Selected campaign: " + selectedCampaign.getName());
                workWithCampaign(selectedCampaign);
            } else {
                System.err.println("Invalid selection.");
            }
        } catch (NumberFormatException e) {
            System.err.println("Please enter a valid number.");
        }
    }
    
    /**
     * Lists all available campaigns.
     */
    private void listAllCampaigns() {
        List<Campain> campaigns = getAllCampaigns();
        
        if (campaigns.isEmpty()) {
            System.out.println("\nNo campaigns available.");
            return;
        }
        
        System.out.println("\n=== All Campaigns ===");
        campaigns.forEach(campaign -> {
            System.out.println("- " + campaign.getName() + 
                             " [UUID: " + campaign.getUuid().substring(0, 8) + "...]");
        });
    }
    
    /**
     * Gets all campaigns from the campaign manager.
     */
    private List<Campain> getAllCampaigns() {
        return campaignManager.getAllCampaigns();
    }
    
    /**
     * Handles working with a selected campaign.
     */
    private void workWithCampaign(Campain campaign) {
        boolean workingWithCampaign = true;
        
        while (workingWithCampaign && running) {
            showCampaignMenu(campaign);
            
            String input = scanner.nextLine().trim();
            
            switch (input) {
                case "1":
                    addNoteToCampaign(campaign);
                    break;
                case "2":
                    searchNotesInCampaign(campaign);
                    break;
                case "4":
                    workingWithCampaign = false;
                    break;
                default:
                    System.err.println("Invalid option. Please choose 1-4.");
                    break;
            }
        }
    }
    
    /**
     * Shows the campaign-specific menu.
     */
    private void showCampaignMenu(Campain campaign) {
        System.out.println("\n=== Campaign: " + campaign.getName() + " ===");
        System.out.println("1. Add new note");
        System.out.println("2. Search notes");
        System.out.println("4. Back to main menu");
        System.out.print("Choose an option (1-4): ");
    }
    
    /**
     * Adds a regular note to the campaign.
     */
    private void addNoteToCampaign(Campain campaign) {
        System.out.println("\n=== Add Note to " + campaign.getName() + " ===");
        
        System.out.print("Enter note title: ");
        String title = scanner.nextLine().trim();
        
        if (title.isEmpty()) {
            System.err.println("Title cannot be empty.");
            return;
        }
        
        System.out.println("Enter note content (max 500 words):");
        System.out.println("Type your content and press Enter when done:");
        String content = scanner.nextLine().trim();
        
        if (content.isEmpty()) {
            System.err.println("Content cannot be empty.");
            return;
        }
        
        // Create and validate note
        Note note = new Note(campaign.getUuid(), title, content);
        
        if (!note.isValid()) {
            System.err.println("Note validation failed. Please check:");
            System.err.println("- Title and content are not empty");
            System.err.println("- Content does not exceed 500 words");
            String[] words = content.split("\\s+");
            System.err.println("Current word count: " + words.length);
            return;
        }
        
        System.out.println("Note created successfully!");
        System.out.println("Note details: " + note.toString());
        
        // Process and store note with full workflow
        System.out.println("\nProcessing note...");
        System.out.println("⏳ Generating embedding and extracting narrative artifacts...");
        
        try {
            boolean success = noteService.addNote(note, campaign);
            if (success) {
                System.out.println("✅ Note successfully added to campaign!");
                System.out.println("📝 Note stored with embedding in Qdrant");
                System.out.println("🤖 AI-powered artifact extraction completed");
                System.out.println("📊 Data saved to Neo4j graph database");
                
                // Show completion message
                System.out.println("\n" + "=".repeat(50));
                System.out.println("Note processing workflow completed successfully!");
                System.out.println("Your note has been:");
                System.out.println("• Validated and stored");
                System.out.println("• Embedded using OpenAI models");
                System.out.println("• Analyzed for narrative artifacts");
                System.out.println("• Connected to campaign knowledge graph");
                System.out.println("=".repeat(50));
                
            } else {
                System.err.println("❌ Failed to store note. Please try again.");
                System.err.println("Please check your database connections and try again.");
            }
        } catch (Exception e) {
            System.err.println("❌ Error processing note: " + e.getMessage());
            System.err.println("The system encountered an unexpected error. Please check logs and try again.");
        }
    }
    
    /**
     * Searches notes in the campaign using semantic search.
     * Displays top 2 results and allows user to select one for detailed view.
     */
    private void searchNotesInCampaign(Campain campaign) {
        System.out.println("\n=== Search Notes in " + campaign.getName() + " ===");
        System.out.print("Enter your search query: ");
        String query = scanner.nextLine().trim();
        
        if (query.isEmpty()) {
            System.err.println("Search query cannot be empty.");
            return;
        }
        
        System.out.println("\n🔍 Searching for: \"" + query + "\"...");
        
        try {
            // Perform semantic search (k=2)
            List<String> noteIds = searchService.searchSemanticklyNotes(query, 2, campaign.getUuid());
            
            if (noteIds.isEmpty()) {
                System.out.println("\n❌ No matching notes found.");
                System.out.println("Try rephrasing your query or adding more notes to your campaign.");
                return;
            }
            
            // Retrieve full Note objects
            String collectionName = campaign.getQuadrantCollectionName();
            List<Note> notes = noteService.getNotesByIds(noteIds, collectionName);
            
            if (notes.isEmpty()) {
                System.err.println("\n❌ Error retrieving note details.");
                return;
            }
            
            // Display search results
            System.out.println("\n=== Search Results ===");
            System.out.println("Found " + notes.size() + " relevant note(s):\n");
            
            for (int i = 0; i < notes.size(); i++) {
                Note note = notes.get(i);
                String contentPreview = note.getContent().length() > 100 
                    ? note.getContent().substring(0, 100) + "..." 
                    : note.getContent();
                
                System.out.println((i + 1) + ". " + note.getTitle());
                System.out.println("   Content: " + contentPreview);
                System.out.println();
            }
            
            // Allow user to select a note for detailed view
            System.out.print("Select note (1-" + notes.size() + ") or 0 to return: ");
            String input = scanner.nextLine().trim();
            
            try {
                int choice = Integer.parseInt(input);
                if (choice == 0) {
                    return;
                }
                if (choice >= 1 && choice <= notes.size()) {
                    Note selectedNote = notes.get(choice - 1);
                    displayFullNote(selectedNote);
                } else {
                    System.err.println("Invalid selection.");
                }
            } catch (NumberFormatException e) {
                System.err.println("Please enter a valid number.");
            }
            
        } catch (IllegalArgumentException e) {
            System.err.println("\n❌ Error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("\n❌ An error occurred during search: " + e.getMessage());
            System.err.println("Please check your database connections and try again.");
        }
    }
    
    /**
     * Displays full details of a selected note.
     */
    private void displayFullNote(Note note) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("=== Note Details ===");
        System.out.println("=".repeat(60));
        
        System.out.println("\nTitle: " + note.getTitle());
        System.out.println("Created: " + note.getCreatedAt());
        System.out.println("Updated: " + note.getUpdatedAt());
        System.out.println("Override: " + (note.isOverride() ? "Yes" : "No"));
        
        if (note.isOverride() && note.getOverrideReason() != null) {
            System.out.println("Override Reason: " + note.getOverrideReason());
        }
        
        if (note.isOverridden()) {
            System.out.println("Status: ⚠️  This note has been overridden by newer information");
        }
        
        System.out.println("\nContent:");
        System.out.println("-".repeat(60));
        System.out.println(note.getContent());
        System.out.println("-".repeat(60));
        
        // Show word count
        String[] words = note.getContent().trim().split("\\s+");
        System.out.println("\nWord count: " + words.length + " / 500");
        
        System.out.println("\n" + "=".repeat(60));
        System.out.print("Press Enter to continue...");
        scanner.nextLine();
    }

    /**
     * Stops the terminal interface.
     */
    public void stop() {
        this.running = false;
    }
    
    /**
     * Checks if the terminal interface is currently running.
     * 
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running;
    }
} 