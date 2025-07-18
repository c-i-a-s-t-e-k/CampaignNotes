/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package CampaignNotes;

import CampaignNotes.tracking.LangfuseClient;

import java.sql.DriverManager;
import java.sql.SQLException;

public class App {
    public String getGreeting() {
        return "My Hello World!";
    }

    public static void connect() {
        // connection string
        var url = "jdbc:sqlite:sqlite.db";

        try (var conn = DriverManager.getConnection(url)) {
            System.out.println("Connection to SQLite has been established.");
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public static void main(String[] args) {
        System.out.println(new App().getGreeting());
        connect();


        CampaignManager creator = new CampaignManager();
        if (creator.checkDatabasesAvailability()){
            System.out.println("Databases available.");
        }else {
            System.out.println("Databases not available.");
        }

        // Check Langfuse connection
        try {
            LangfuseClient langfuseClient = new LangfuseClient();
            if (langfuseClient.checkConnection()) {
                System.out.println("Langfuse connection available.");
            } else {
                System.out.println("Langfuse connection not available.");
            }
        } catch (Exception e) {
            System.out.println("Langfuse connection failed: " + e.getMessage());
        }
        
        // Start Terminal Interface
        System.out.println("\nStarting Terminal Interface...");
        TerminalInterface terminalInterface = new TerminalInterface();
        terminalInterface.start();


        
    }
}
