# Bazy Danych – Skrót (CampaignNotes)

## Przegląd
- **Architektura 3 baz**:
  - **SQLite** – metadane kampanii i kategorie
  - **Neo4j** – graf artefaktów i relacji
  - **Qdrant** – wektorowe wyszukiwanie i deduplikacja (notatki, artefakty, relacje)

## SQLite (metadane)
- **Plik**: `sqlite.db` (katalog główny)
- **Repo**: `CampaignNotes/database/SqliteRepository.java`
- **Tabele (skrót)**:
  - `users(id, email UNIQUE, password_hash, created_at, updated_at, is_active, deleted_at, email_verified, ...)`
  - `campains(uuid, name, neo4j_label, quadrant_collection_name, user_id → users.id, description, created_at, updated_at, is_active, deleted_at, settings JSON)`
  - `artifact_categories(id, name UNIQUE, description, is_active, created_at)`
  - `artifact_categories_to_campaigns(id, campaign_uuid → campains.uuid, category_name → artifact_categories.name, created_at, UNIQUE(campaign_uuid, category_name))`
  - `campaign_notes(id, campaign_uuid → campains.uuid, note_uuid UNIQUE, title, created_at, updated_at, qdrant_sync_status, neo4j_sync_status, is_override, word_count, is_active, deleted_at)`
  - `artifact_notes(id, artifact_uuid ↔ Neo4j.node_id, note_uuid → campaign_notes.note_uuid, campaign_uuid → campains.uuid, confidence_score, created_at, created_by_ai, confirmed_by_user, is_active, deleted_at, UNIQUE(artifact_uuid, note_uuid))`
  - `note_overrides(id, past_time_note_uuid → campaign_notes.note_uuid, override_note_uuid → campaign_notes.note_uuid, campaign_uuid → campains.uuid, override_reason, created_at, is_active, deleted_at, UNIQUE(past_time_note_uuid, override_note_uuid))`
  - `campaign_terminology(id, campaign_uuid → campains.uuid, term UNIQUE per campaign, explanation, created_at, created_by_user_id → users.id, is_active, deleted_at)`
  - `password_reset_tokens(id, user_id → users.id, token UNIQUE, expires_at, used_at, created_at)`
  - `scheduled_deletions(id, table_name, record_id, delete_at, created_at, processed)`

- **Indeksy (ważniejsze)**: FK, soft delete, statusy sync (`idx_campaign_notes_sync_status`), email (`idx_users_email`), term (`idx_terminology_term`).
- **PRAGMA**: `foreign_keys=ON`, `journal_mode=WAL`, `synchronous=NORMAL`, `cache_size=10000`, `temp_store=memory`, `mmap_size=268435456`, `optimize`.
- **Triggery**: automatyczne `updated_at` (users, campains, campaign_notes) oraz zaplanowane usunięcia po soft delete (users, campains → `scheduled_deletions`).

## Neo4j (graf artefaktów)
- **Env**: `NEO4J_URI`, `NEO4J_USER`, `NEO4J_PASSWORD`
- **Repo**: `CampaignNotes/database/Neo4jRepository.java`
- **Izolacja danych**: etykieta węzła `{CampaignLabel}_Artifact` (sanityzowana)
- **Węzły – properties**: `id`, `name`, `type`, `campaignUuid`, `note_ids` (List<String>), `description`, `createdAt`
- **Relacje – properties**: `id`, `label`, `description`, `reasoning`, `note_ids` (List<String>), `campaignUuid`, `createdAt`
- **Integracje**: `artifact_notes.artifact_uuid` (SQLite) ↔ węzły w Neo4j; statusy sync w `campaign_notes.neo4j_sync_status`.

## Qdrant (wektory: notatki, artefakty, relacje)
- **Env**: `QUADRANT_URL`, `QUADRANT_GRPC_PORT`
- **Repo**: `CampaignNotes/database/QdrantRepository.java`
- **Kolekcje**: jedna na kampanię (wszystkie typy razem); nazwa z `campains.quadrant_collection_name`
- **Punkt**: `id` (numeric hash), `vector` (embedding 1536-D OpenAI), `payload` (zależny od typu)
- **Typy punktów**:
  - **`type=note`**: `note_id`, `title`, `content` (≤500 słów), `campaign_uuid`, `created_at`, `updated_at`, `is_override`, `is_overridden`, `type`, `override_reason?` – semantyczne wyszukiwanie
  - **`type=artifact`**: `artifact_id`, `name`, `artifact_type`, `description`, `campaign_uuid`, `created_at`, `type` – deduplikacja (Phase 1)
  - **`type=relation`**: `relationship_id`, `source`, `target`, `label`, `description`, `reasoning`, `campaign_uuid`, `created_at`, `type` – deduplikacja (Phase 1)
- **Integracje**: notatki 1:1 z `campaign_notes.note_uuid` (SQLite); artefakty/relacje ↔ Neo4j; statusy sync w `campaign_notes.qdrant_sync_status`.

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
