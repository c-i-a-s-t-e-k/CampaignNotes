# Bazy Danych – Skrót (CampaignNotes)

## Przegląd
- **Architektura 3 baz**:
  - **SQLite** – metadane kampanii i kategorie
  - **Neo4j** – graf artefaktów i relacji
  - **Qdrant** – wektorowe wyszukiwanie notatek

## SQLite (metadane)
- **Plik**: `sqlite.db` (katalog główny)
- **Repo**: `CampaignNotes/database/SqliteRepository.java`
- **Tabele**:
  - `campains(uuid, name, neo4j_label, quadrant_collection_name)`
  - `artifact_categories(id, name UNIQUE, description, is_active, created_at)`
  - `artifact_categories_to_campaigns(id, campaign_uuid → campains.uuid, category_name → artifact_categories.name, created_at, UNIQUE(campaign_uuid, category_name))`

## Neo4j (graf artefaktów)
- **Env**: `NEO4J_URI`, `NEO4J_USER`, `NEO4J_PASSWORD`
- **Repo**: `CampaignNotes/database/Neo4jRepository.java`
- **Izolacja danych**: etykieta węzła `{CampaignLabel}_Artifact` (sanityzowana)
- **Węzły – properties**: `id`, `name`, `type`, `campaignUuid`, `noteId`, `description`, `createdAt`
- **Relacje – properties**: `id`, `label`, `description`, `reasoning`, `noteId`, `campaignUuid`, `createdAt`

## Qdrant (wektory notatek)
- **Env**: `QDRANT_URL`, `QDRANT_GRPC_PORT`
- **Repo**: `CampaignNotes/database/QdrantRepository.java`
- **Kolekcje**: jedna na kampanię; nazwa z `campains.quadrant_collection_name`
- **Punkt**:
  - `id`: UUID notatki (v5 z treści)
  - `vector`: embedding 1536-D (OpenAI)
  - `payload`: `id`, `campaignUuid`, `title`, `content` (≤ 500 słów), `createdAt`, `updatedAt`, `isOverride`, `overrideReason`, `isOverridden`, `overriddenByNoteIds[]`

## Połączenia i operacje
- **Manager**: `CampaignNotes/database/DatabaseConnectionManager.java`
```java
public class DatabaseConnectionManager {
    private final SqliteRepository sqliteRepository;
    private final Neo4jRepository neo4jRepository;
    private final QdrantRepository qdrantRepository;
}
```
- **Dostępność**:
  - Neo4j: `driver.verifyConnectivity()`
  - Qdrant: `client.listCollectionsAsync()`
  - SQLite: lokalna (zawsze dostępna)
- **Zamykanie**: koordynowane zamykanie klientów baz
- **Walidacja**: sanityzacja etykiet Neo4j; limit treści notatek 500 słów
- **Obsługa błędów**: graceful degradation, rollback, logowanie operacji
