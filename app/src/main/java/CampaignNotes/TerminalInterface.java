package CampaignNotes;

import java.util.List;
import java.util.Scanner;

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
    private boolean running;
    
    /**
     * Constructor initializes the terminal interface with required services.
     */
    public TerminalInterface() {
        this.scanner = new Scanner(System.in);
        this.campaignManager = new CampaignManager();
        this.noteService = new NoteService();
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
                    addOverrideNoteToCampaign(campaign);
                    break;
                case "3":
                    // Future: View campaign notes
                    System.out.println("Feature not yet implemented: View notes");
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
        System.out.println("2. Add override note");
        System.out.println("3. View notes (coming soon)");
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
        
        // Note: Actual storage to Qdrant would happen here when NoteService is fully working
        System.out.println("Note would be processed and stored with embedding.");
        System.out.println("(Storage implementation pending NoteService completion)");
        
        // Future implementation:
        // boolean success = noteService.addNote(note, campaign);
        // if (success) {
        //     System.out.println("Note successfully added to campaign and stored with embedding!");
        // } else {
        //     System.err.println("Failed to store note. Please try again.");
        // }
    }
    
    /**
     * Adds an override note to the campaign.
     */
    private void addOverrideNoteToCampaign(Campain campaign) {
        System.out.println("\n=== Add Override Note to " + campaign.getName() + " ===");
        
        System.out.print("Enter note title: ");
        String title = scanner.nextLine().trim();
        
        if (title.isEmpty()) {
            System.err.println("Title cannot be empty.");
            return;
        }
        
        System.out.println("Enter note content (max 500 words):");
        String content = scanner.nextLine().trim();
        
        if (content.isEmpty()) {
            System.err.println("Content cannot be empty.");
            return;
        }
        
        System.out.print("Enter override reason: ");
        String overrideReason = scanner.nextLine().trim();
        
        if (overrideReason.isEmpty()) {
            System.err.println("Override reason cannot be empty.");
            return;
        }
        
        // Create and validate override note
        Note note = new Note(campaign.getUuid(), title, content, overrideReason);
        
        if (!note.isValid()) {
            System.err.println("Note validation failed. Please check:");
            System.err.println("- Title, content, and override reason are not empty");
            System.err.println("- Content does not exceed 500 words");
            String[] words = content.split("\\s+");
            System.err.println("Current word count: " + words.length);
            return;
        }
        
        System.out.println("Override note created successfully!");
        System.out.println("Note details: " + note.toString());
        
        // Note: Actual storage would happen here when NoteService is fully working
        System.out.println("Override note would be processed and stored with embedding.");
        System.out.println("(Storage implementation pending NoteService completion)");
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