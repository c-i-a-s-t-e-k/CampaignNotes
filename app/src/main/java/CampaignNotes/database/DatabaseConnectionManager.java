package CampaignNotes.database;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Central manager for environment configuration and database repositories.
 * Provides access to SQLite, Neo4j, and Qdrant repositories and ensures
 * coordinated shutdown of resources.
 */
public class DatabaseConnectionManager {
    private final Dotenv dotenv;
    private final SqliteRepository sqliteRepository;
    private final Neo4jRepository neo4jRepository;
    private final QdrantRepository qdrantRepository;

    public DatabaseConnectionManager() {
        Dotenv loaded;
        try {
            loaded = Dotenv.configure().directory("./").load();
        } catch (Exception e) {
            System.err.println("Error loading .env file: " + e.getMessage() + " working directory: " + System.getProperty("user.dir"));
            loaded = null;
        }
        this.dotenv = loaded;
        this.sqliteRepository = new SqliteRepository("sqlite.db");
        this.neo4jRepository = new Neo4jRepository(this.dotenv);
        this.qdrantRepository = new QdrantRepository(this.dotenv);
        
        // Initialize database schema
        initializeDatabase();
    }
    
    /**
     * Initializes the database schema by creating tables and default data.
     */
    private void initializeDatabase() {
        try {
            sqliteRepository.ensureUsersTableExists();
            sqliteRepository.ensureCampaignsTableExists();
            sqliteRepository.ensureArtifactTablesExist();
            sqliteRepository.insertDefaultArtifactCategories();
            sqliteRepository.createDefaultUserIfNeeded();
        } catch (Exception e) {
            System.err.println("Error initializing database: " + e.getMessage());
        }
    }

    public SqliteRepository getSqliteRepository() {
        return sqliteRepository;
    }

    public Neo4jRepository getNeo4jRepository() {
        return neo4jRepository;
    }

    public QdrantRepository getQdrantRepository() {
        return qdrantRepository;
    }

    public boolean checkDatabasesAvailability() {
        boolean neo4jAvailable = neo4jRepository.checkAvailability();
        boolean qdrantAvailable = qdrantRepository.checkAvailability();
        return neo4jAvailable && qdrantAvailable;
    }

    public void closeAll() {
        try {
            neo4jRepository.close();
        } catch (Exception e) {
            System.err.println("Error closing Neo4j connection: " + e.getMessage());
        }
        try {
            qdrantRepository.close();
        } catch (Exception e) {
            System.err.println("Error closing Qdrant client: " + e.getMessage());
        }
    }
}


