# Dokumentacja Baz Danych - CampaignNotes

## Przegląd Architektury

Projekt CampaignNotes wykorzystuje architekturę trzech baz danych, gdzie każda pełni specyficzną rolę w systemie:

1. **SQLite** - Centralna baza relacyjna dla metadanych kampanii i kategorii artefaktów
2. **Neo4j** - Grafowa baza danych dla artefaktów i ich wzajemnych relacji
3. **Qdrant** - Wektorowa baza danych dla semantycznego wyszukiwania notatek

## 1. SQLite - Baza Relacyjna

### Opis
SQLite służy jako centralna baza metadanych, przechowująca informacje o kampaniach, kategoriach artefaktów oraz mapowania między nimi. Pełni rolę rejestru łączącego wszystkie trzy bazy danych.

### Lokalizacja
- Plik: `sqlite.db` w katalogu głównym aplikacji
- Zarządzanie: `SqliteRepository.java`

### Struktura Tabel

#### Tabela `users`
```sql
CREATE TABLE users (
    id TEXT PRIMARY KEY,
    email TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    is_active BOOLEAN DEFAULT 1,
    deleted_at INTEGER DEFAULT NULL,
    email_verified BOOLEAN DEFAULT 0,
    email_verification_token TEXT,
    email_verification_expires_at INTEGER,
    last_login_at INTEGER,
    is_admin BOOLEAN DEFAULT 0
);
```

**Pola:**
- `id` (TEXT, PRIMARY KEY) - Stabilny identyfikator użytkownika (UUID)
- `email` (TEXT, UNIQUE, NOT NULL) - Adres email użytkownika
- `password_hash` (TEXT, NOT NULL) - Hash hasła (aplikacja odpowiada za algorytm i sól)
- `created_at` (INTEGER, NOT NULL) - Czas utworzenia (Unix epoch, sekundy)
- `updated_at` (INTEGER, NOT NULL) - Czas ostatniej modyfikacji (Unix epoch)
- `is_active` (BOOLEAN, DEFAULT 1) - Flaga aktywności (soft delete)
- `deleted_at` (INTEGER, DEFAULT NULL) - Czas soft delete (Unix epoch) lub NULL
- `email_verified` (BOOLEAN, DEFAULT 0) - Czy email został zweryfikowany
- `email_verification_token` (TEXT) - Ostatni token weryfikacyjny
- `email_verification_expires_at` (INTEGER) - Wygaśnięcie tokenu (Unix epoch)
- `last_login_at` (INTEGER) - Czas ostatniego logowania (Unix epoch)
- `is_admin` (BOOLEAN, DEFAULT 0) - Rola administratora (dostęp do metryk AI)

#### Tabela `campains` (rozszerzona)
```sql
CREATE TABLE IF NOT EXISTS campains (
    uuid TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    neo4j_label TEXT NOT NULL,
    quadrant_collection_name TEXT NOT NULL,
    user_id TEXT NOT NULL,
    description TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    is_active BOOLEAN DEFAULT 1,
    deleted_at INTEGER DEFAULT NULL,
    settings TEXT,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

**Pola:**
- `uuid` (TEXT, PRIMARY KEY) - Identyfikator kampanii (UUID)
- `name` (TEXT, NOT NULL) - Nazwa kampanii wyświetlana użytkownikowi
- `neo4j_label` (TEXT, NOT NULL) - Etykieta izolacji danych w Neo4j (sanityzowana)
- `quadrant_collection_name` (TEXT, NOT NULL) - Nazwa kolekcji Qdrant dla kampanii
- `user_id` (TEXT, NOT NULL) - Właściciel kampanii (FK → `users.id`)
- `description` (TEXT) - Opcjonalny opis kampanii
- `created_at` (INTEGER, NOT NULL) - Czas utworzenia kampanii (Unix epoch)
- `updated_at` (INTEGER, NOT NULL) - Czas ostatniej modyfikacji (Unix epoch)
- `is_active` (BOOLEAN, DEFAULT 1) - Flaga aktywności (soft delete)
- `deleted_at` (INTEGER, DEFAULT NULL) - Czas soft delete (Unix epoch) lub NULL
- `settings` (TEXT) - Konfig kampanii w formacie JSON

#### Tabela `artifact_categories`
```sql
CREATE TABLE IF NOT EXISTS artifact_categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE NOT NULL,
    description TEXT NOT NULL,
    is_active BOOLEAN DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
```

**Pola:**
- `id` (INTEGER, PRIMARY KEY, AUTOINCREMENT) - Unikalny identyfikator kategorii
- `name` (TEXT, UNIQUE, NOT NULL) - Nazwa kategorii (np. "characters", "locations")
- `description` (TEXT, NOT NULL) - Opis kategorii
- `is_active` (BOOLEAN, DEFAULT 1) - Status aktywności kategorii
- `created_at` (TIMESTAMP, DEFAULT CURRENT_TIMESTAMP) - Data utworzenia

**Domyślne kategorie:**
- `characters` - "People, creatures, and sentient beings within the narrative"
- `locations` - "Places, regions, buildings, and geographical features"
- `items` - "Objects, weapons, artifacts, and physical possessions"
- `events` - "Important occurrences, battles, ceremonies, and plot developments"

#### Tabela `artifact_categories_to_campaigns`
```sql
CREATE TABLE IF NOT EXISTS artifact_categories_to_campaigns (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    campaign_uuid TEXT NOT NULL,
    category_name TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (campaign_uuid) REFERENCES campains(uuid) ON DELETE CASCADE,
    FOREIGN KEY (category_name) REFERENCES artifact_categories(name) ON DELETE CASCADE,
    UNIQUE(campaign_uuid, category_name)
)
```

**Pola:**
- `id` (INTEGER, PRIMARY KEY, AUTOINCREMENT) - Unikalny identyfikator mapowania
- `campaign_uuid` (TEXT, NOT NULL) - Referencja do kampanii
- `category_name` (TEXT, NOT NULL) - Referencja do kategorii
- `created_at` (TIMESTAMP, DEFAULT CURRENT_TIMESTAMP) - Data utworzenia mapowania

#### Tabela `campaign_notes`
```sql
CREATE TABLE campaign_notes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    campaign_uuid TEXT NOT NULL,
    note_uuid TEXT UNIQUE NOT NULL,
    title TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    qdrant_sync_status TEXT DEFAULT 'pending',
    qdrant_sync_error TEXT,
    qdrant_last_sync_at INTEGER,
    neo4j_sync_status TEXT DEFAULT 'pending',
    neo4j_sync_error TEXT,
    neo4j_last_sync_at INTEGER,
    is_override BOOLEAN DEFAULT 0,
    word_count INTEGER,
    is_active BOOLEAN DEFAULT 1,
    deleted_at INTEGER DEFAULT NULL,
    FOREIGN KEY (campaign_uuid) REFERENCES campains(uuid) ON DELETE CASCADE,
    CHECK (qdrant_sync_status IN ('pending', 'syncing', 'synced', 'error', 'retry')),
    CHECK (neo4j_sync_status IN ('pending', 'syncing', 'synced', 'error', 'retry'))
);
```

**Pola:**
- `id` (INTEGER, PRIMARY KEY, AUTOINCREMENT) - Techniczne ID rekordu
- `campaign_uuid` (TEXT, NOT NULL) - FK do kampanii (`campains.uuid`)
- `note_uuid` (TEXT, UNIQUE, NOT NULL) - Stabilny UUID notatki (1:1 z Qdrant point)
- `title` (TEXT, NOT NULL) - Tytuł notatki
- `created_at` (INTEGER, NOT NULL) - Czas utworzenia (Unix epoch)
- `updated_at` (INTEGER, NOT NULL) - Czas ostatniej modyfikacji (Unix epoch)
- `qdrant_sync_status` (TEXT, DEFAULT 'pending') - Status sync z Qdrant: `pending|syncing|synced|error|retry`
- `qdrant_sync_error` (TEXT) - Ostatni błąd sync z Qdrant (jeśli wystąpił)
- `qdrant_last_sync_at` (INTEGER) - Czas ostatniej próby/udanej synchronizacji z Qdrant
- `neo4j_sync_status` (TEXT, DEFAULT 'pending') - Status sync z Neo4j: `pending|syncing|synced|error|retry`
- `neo4j_sync_error` (TEXT) - Ostatni błąd sync z Neo4j (jeśli wystąpił)
- `neo4j_last_sync_at` (INTEGER) - Czas ostatniej próby/udanej synchronizacji z Neo4j
- `is_override` (BOOLEAN, DEFAULT 0) - Czy notatka pełni rolę override wobec innej
- `word_count` (INTEGER) - Liczba słów (walidacja limitu 500 w aplikacji)
- `is_active` (BOOLEAN, DEFAULT 1) - Flaga aktywności (soft delete)
- `deleted_at` (INTEGER, DEFAULT NULL) - Czas soft delete (Unix epoch) lub NULL

#### Tabela `artifact_notes`
```sql
CREATE TABLE artifact_notes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    artifact_uuid TEXT NOT NULL,
    note_uuid TEXT NOT NULL,
    campaign_uuid TEXT NOT NULL,
    confidence_score REAL,
    created_at INTEGER NOT NULL,
    created_by_ai BOOLEAN DEFAULT 1,
    confirmed_by_user BOOLEAN DEFAULT 0,
    is_active BOOLEAN DEFAULT 1,
    deleted_at INTEGER DEFAULT NULL,
    FOREIGN KEY (campaign_uuid) REFERENCES campains(uuid) ON DELETE CASCADE,
    FOREIGN KEY (note_uuid) REFERENCES campaign_notes(note_uuid) ON DELETE CASCADE,
    UNIQUE(artifact_uuid, note_uuid)
);
```

**Pola:**
- `id` (INTEGER, PRIMARY KEY, AUTOINCREMENT) - Techniczne ID rekordu
- `artifact_uuid` (TEXT, NOT NULL) - UUID artefaktu (węzeł w Neo4j)
- `note_uuid` (TEXT, NOT NULL) - UUID notatki (FK → `campaign_notes.note_uuid`)
- `campaign_uuid` (TEXT, NOT NULL) - UUID kampanii (FK → `campains.uuid`)
- `confidence_score` (REAL) - Pewność identyfikacji (0..1)
- `created_at` (INTEGER, NOT NULL) - Czas powiązania (Unix epoch)
- `created_by_ai` (BOOLEAN, DEFAULT 1) - Czy powiązanie utworzyła AI
- `confirmed_by_user` (BOOLEAN, DEFAULT 0) - Czy powiązanie potwierdził użytkownik
- `is_active` (BOOLEAN, DEFAULT 1) - Flaga aktywności (soft delete)
- `deleted_at` (INTEGER, DEFAULT NULL) - Czas soft delete (Unix epoch) lub NULL

#### Tabela `note_overrides`
```sql
CREATE TABLE note_overrides (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    past_time_note_uuid TEXT NOT NULL,
    override_note_uuid TEXT NOT NULL,
    campaign_uuid TEXT NOT NULL,
    override_reason TEXT,
    created_at INTEGER NOT NULL,
    is_active BOOLEAN DEFAULT 1,
    deleted_at INTEGER DEFAULT NULL,
    FOREIGN KEY (campaign_uuid) REFERENCES campains(uuid) ON DELETE CASCADE,
    FOREIGN KEY (past_time_note_uuid) REFERENCES campaign_notes(note_uuid),
    FOREIGN KEY (override_note_uuid) REFERENCES campaign_notes(note_uuid),
    UNIQUE(past_time_note_uuid, override_note_uuid)
);
```

**Pola:**
- `id` (INTEGER, PRIMARY KEY, AUTOINCREMENT) - Techniczne ID rekordu
- `past_time_note_uuid` (TEXT, NOT NULL) - UUID notatki źródłowej (nadpisywanej)
- `override_note_uuid` (TEXT, NOT NULL) - UUID notatki nadpisującej
- `campaign_uuid` (TEXT, NOT NULL) - UUID kampanii (FK → `campains.uuid`)
- `override_reason` (TEXT) - Uzasadnienie override
- `created_at` (INTEGER, NOT NULL) - Czas utworzenia relacji override (Unix epoch)
- `is_active` (BOOLEAN, DEFAULT 1) - Flaga aktywności (soft delete)
- `deleted_at` (INTEGER, DEFAULT NULL) - Czas soft delete (Unix epoch) lub NULL

#### Tabela `campaign_terminology`
```sql
CREATE TABLE campaign_terminology (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    campaign_uuid TEXT NOT NULL,
    term TEXT NOT NULL,
    explanation TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    created_by_user_id TEXT NOT NULL,
    is_active BOOLEAN DEFAULT 1,
    deleted_at INTEGER DEFAULT NULL,
    FOREIGN KEY (campaign_uuid) REFERENCES campains(uuid) ON DELETE CASCADE,
    FOREIGN KEY (created_by_user_id) REFERENCES users(id),
    UNIQUE(campaign_uuid, term)
);
```

**Pola:**
- `id` (INTEGER, PRIMARY KEY, AUTOINCREMENT) - Techniczne ID rekordu
- `campaign_uuid` (TEXT, NOT NULL) - UUID kampanii (FK → `campains.uuid`)
- `term` (TEXT, NOT NULL) - Pojęcie/termin; unikalny w obrębie kampanii
- `explanation` (TEXT, NOT NULL) - Wyjaśnienie/definicja terminu
- `created_at` (INTEGER, NOT NULL) - Czas dodania terminu (Unix epoch)
- `created_by_user_id` (TEXT, NOT NULL) - Autor terminu (FK → `users.id`)
- `is_active` (BOOLEAN, DEFAULT 1) - Flaga aktywności (soft delete)
- `deleted_at` (INTEGER, DEFAULT NULL) - Czas soft delete (Unix epoch) lub NULL

#### Tabela `password_reset_tokens`
```sql
CREATE TABLE password_reset_tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    token TEXT UNIQUE NOT NULL,
    expires_at INTEGER NOT NULL,
    used_at INTEGER DEFAULT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

**Pola:**
- `id` (INTEGER, PRIMARY KEY, AUTOINCREMENT) - Techniczne ID rekordu
- `user_id` (TEXT, NOT NULL) - Użytkownik (FK → `users.id`)
- `token` (TEXT, UNIQUE, NOT NULL) - Jednorazowy token resetu hasła
- `expires_at` (INTEGER, NOT NULL) - Czas wygaśnięcia tokenu (Unix epoch)
- `used_at` (INTEGER, DEFAULT NULL) - Czas użycia tokenu lub NULL
- `created_at` (INTEGER, NOT NULL) - Czas utworzenia rekordu (Unix epoch)

#### Tabela `scheduled_deletions`
```sql
CREATE TABLE scheduled_deletions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    table_name TEXT NOT NULL,
    record_id TEXT NOT NULL,
    delete_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    processed BOOLEAN DEFAULT 0
);
```

**Pola:**
- `id` (INTEGER, PRIMARY KEY, AUTOINCREMENT) - Techniczne ID rekordu
- `table_name` (TEXT, NOT NULL) - Nazwa tabeli, której dotyczy rekord do usunięcia
- `record_id` (TEXT, NOT NULL) - Identyfikator rekordu (typ zgodny z tabelą źródłową)
- `delete_at` (INTEGER, NOT NULL) - Termin hard delete (Unix epoch)
- `created_at` (INTEGER, NOT NULL) - Czas zaplanowania (Unix epoch)
- `processed` (BOOLEAN, DEFAULT 0) - Czy zadanie zostało przetworzone

### Kluczowe Operacje
- Zarządzanie użytkownikami i kampaniami (CRUD, soft delete)
- Zarządzanie kategoriami artefaktów i mapowaniami do kampanii
- System notatek kampanii z override'ami i terminologią
- Śledzenie i retry statusów synchronizacji z Qdrant/Neo4j
- Ładowanie identyfikatorów dla innych baz (Neo4j/Qdrant)

### Relacje Między Tabelami (SQLite)
- 1:N: `users` → `campains`; `campains` → `campaign_notes`/`campaign_terminology`/`artifact_categories_to_campaigns`/`artifact_notes`/`note_overrides`
- 1:N: `users` → `password_reset_tokens`; `users` → `campaign_terminology`
- 1:N: `campaign_notes` → `artifact_notes`
- N:M: `campains` ↔ `artifact_categories` (przez `artifact_categories_to_campaigns`)
- N:M: `artifacts` ↔ `campaign_notes` (przez `artifact_notes`, artefakty w Neo4j)
- N:M: `campaign_notes` ↔ `campaign_notes` (przez `note_overrides`)

### Indeksy (skrót)
```sql
CREATE INDEX idx_campains_user_id ON campains(user_id);
CREATE INDEX idx_campaign_notes_campaign_uuid ON campaign_notes(campaign_uuid);
CREATE INDEX idx_campaign_notes_note_uuid ON campaign_notes(note_uuid);
CREATE INDEX idx_artifact_notes_campaign_uuid ON artifact_notes(campaign_uuid);
CREATE INDEX idx_artifact_notes_note_uuid ON artifact_notes(note_uuid);
CREATE INDEX idx_artifact_notes_artifact_uuid ON artifact_notes(artifact_uuid);
CREATE INDEX idx_note_overrides_campaign_uuid ON note_overrides(campaign_uuid);
CREATE INDEX idx_note_overrides_past_time ON note_overrides(past_time_note_uuid);
CREATE INDEX idx_note_overrides_override ON note_overrides(override_note_uuid);
CREATE INDEX idx_terminology_campaign_uuid ON campaign_terminology(campaign_uuid);
CREATE INDEX idx_password_reset_user_id ON password_reset_tokens(user_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_email_verification_token ON users(email_verification_token);
CREATE INDEX idx_password_reset_token ON password_reset_tokens(token);
CREATE INDEX idx_campaign_notes_sync_status ON campaign_notes(qdrant_sync_status, neo4j_sync_status);
CREATE INDEX idx_artifact_notes_confirmed ON artifact_notes(confirmed_by_user);
CREATE INDEX idx_terminology_term ON campaign_terminology(term);
CREATE INDEX idx_users_active ON users(is_active, deleted_at);
CREATE INDEX idx_campains_active ON campains(is_active, deleted_at);
CREATE INDEX idx_campaign_notes_active ON campaign_notes(is_active, deleted_at);
CREATE INDEX idx_artifact_notes_active ON artifact_notes(is_active, deleted_at);
CREATE INDEX idx_note_overrides_active ON note_overrides(is_active, deleted_at);
CREATE INDEX idx_terminology_active ON campaign_terminology(is_active, deleted_at);
CREATE INDEX idx_users_created_at ON users(created_at);
CREATE INDEX idx_campains_created_at ON campains(created_at);
CREATE INDEX idx_campaign_notes_created_at ON campaign_notes(created_at);
CREATE INDEX idx_scheduled_deletions_delete_at ON scheduled_deletions(delete_at, processed);
CREATE INDEX idx_artifact_notes_campaign_confirmed ON artifact_notes(campaign_uuid, confirmed_by_user);
CREATE INDEX idx_terminology_campaign_term ON campaign_terminology(campaign_uuid, term);
```

### Zasady i Konfiguracja SQLite (skrót)
```sql
PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA cache_size = 10000;
PRAGMA temp_store = memory;
PRAGMA mmap_size = 268435456; -- 256MB
PRAGMA optimize;
```

#### Triggery (aktualizacja znaczników czasu i harmonogram usunięć)
```sql
CREATE TRIGGER users_updated_at AFTER UPDATE ON users
BEGIN UPDATE users SET updated_at = strftime('%s','now') WHERE id = NEW.id; END;

CREATE TRIGGER campains_updated_at AFTER UPDATE ON campains
BEGIN UPDATE campains SET updated_at = strftime('%s','now') WHERE uuid = NEW.uuid; END;

CREATE TRIGGER campaign_notes_updated_at AFTER UPDATE ON campaign_notes
BEGIN UPDATE campaign_notes SET updated_at = strftime('%s','now') WHERE note_uuid = NEW.note_uuid; END;

CREATE TRIGGER schedule_user_deletion AFTER UPDATE OF deleted_at ON users
WHEN NEW.deleted_at IS NOT NULL AND OLD.deleted_at IS NULL
BEGIN INSERT INTO scheduled_deletions (table_name, record_id, delete_at, created_at)
VALUES ('users', NEW.id, NEW.deleted_at + 2592000, strftime('%s','now')); END;

CREATE TRIGGER schedule_campaign_deletion AFTER UPDATE OF deleted_at ON campains
WHEN NEW.deleted_at IS NOT NULL AND OLD.deleted_at IS NULL
BEGIN INSERT INTO scheduled_deletions (table_name, record_id, delete_at, created_at)
VALUES ('campains', NEW.uuid, NEW.deleted_at + 2592000, strftime('%s','now')); END;
```

## 2. Neo4j - Grafowa Baza Danych

### Opis
Neo4j przechowuje artefakty kampanii (postacie, lokacje, przedmioty, wydarzenia) oraz ich wzajemne relacje w strukturze grafowej. Umożliwia modelowanie złożonych połączeń między elementami narracji.

### Konfiguracja Połączenia
- **Zmienne środowiskowe:**
  - `NEO4J_URI` - URI bazy danych Neo4j
  - `NEO4J_USER` - Nazwa użytkownika
  - `NEO4J_PASSWORD` - Hasło
- **Zarządzanie:** `Neo4jRepository.java`

### Struktura Danych

#### Węzły (Nodes)
Każdy artefakt jest reprezentowany jako węzeł z następującymi właściwościami:

**Etykiety węzłów:**
- `{CampaignLabel}_Artifact` - gdzie `{CampaignLabel}` to sanityzowana etykieta kampanii

**Właściwości węzłów:**
- `id` (String) - Unikalny identyfikator artefaktu
- `name` (String) - Nazwa artefaktu
- `type` (String) - Typ artefaktu (characters, locations, items, events)
- `campaignUuid` (String) - UUID kampanii
- `note_ids` (List<String>) - Lista identyfikatorów notatek źródłowych (może zawierać wiele notatek)
- `description` (String) - Opis artefaktu
- `createdAt` (LocalDateTime) - Data utworzenia

#### Relacje (Relationships)
Relacje między artefaktami są reprezentowane jako krawędzie grafu:

**Właściwości relacji:**
- `id` (String) - Unikalny identyfikator relacji
- `label` (String) - Etykieta relacji (sanityzowana)
- `description` (String) - Opis relacji
- `reasoning` (String) - Uzasadnienie relacji
- `note_ids` (List<String>) - Lista identyfikatorów notatek źródłowych (może zawierać wiele notatek)
- `campaignUuid` (String) - UUID kampanii
- `createdAt` (LocalDateTime) - Data utworzenia

Powiązania z SQLite/Qdrant:
- `artifact_uuid` w SQLite (`artifact_notes`) wskazuje na węzeł artefaktu w Neo4j
- Synchronizacja jest śledzona w `campaign_notes` (statusy `neo4j_sync_status`)
- Właściwość `note_ids` w Neo4j może zawierać wiele identyfikatorów notatek, co odpowiada relacji N:M w tabeli `artifact_notes` w SQLite

### Izolacja Danych
Każda kampania ma własną etykietę węzłów, co zapewnia izolację danych między różnymi kampaniami w tej samej instancji Neo4j.

### Sanityzacja Etykiet
Etykiety Neo4j są sanityzowane przez usunięcie spacji, myślników i znaków specjalnych, zastępując je podkreślnikami.

## 3. Qdrant - Wektorowa Baza Danych

### Opis
Qdrant przechowuje notatki kampanii w formie wektorowej, umożliwiając semantyczne wyszukiwanie i analizę treści.

### Konfiguracja Połączenia
- **Zmienne środowiskowe:**
  - `QDRANT_URL` - URL serwera Qdrant
  - `QDRANT_GRPC_PORT` - Port gRPC
- **Zarządzanie:** `QdrantRepository.java`

### Struktura Danych

#### Kolekcje
Każda kampania ma dedykowaną kolekcję identyfikowaną przez `quadrant_collection_name` z tabeli `campains`.

#### Punkty (Points)
Każda notatka jest przechowywana jako punkt wektorowy:

**Identyfikator punktu:** UUID notatki (generowany na podstawie zawartości)

**Wektor:** 1536-wymiarowy embedding wygenerowany przez OpenAI

**Payload (metadane):**
```java
{
    "id": "note-uuid",
    "campaignUuid": "campaign-uuid", 
    "title": "tytuł notatki",
    "content": "treść notatki",
    "createdAt": "2024-01-01T12:00:00",
    "updatedAt": "2024-01-01T12:00:00",
    "isOverride": false,
    "overrideReason": null,
    "isOverridden": false,
    "overriddenByNoteIds": []
}
```

**Pola:**
- `id` (String) - UUID notatki (v5 generowany na podstawie treści)
- `campaignUuid` (String) - Identyfikator kampanii do której należy notatka
- `title` (String) - Tytuł notatki
- `content` (String) - Treść notatki (max 500 słów)
- `createdAt` (String) - Data i czas utworzenia notatki
- `updatedAt` (String) - Data i czas ostatniej modyfikacji
- `isOverride` (Boolean) - Czy notatka nadpisuje inną notatkę
- `overrideReason` (String) - Powód nadpisania (null jeśli nie ma)
- `isOverridden` (Boolean) - Czy notatka została nadpisana przez inną
- `overriddenByNoteIds` (Array) - Lista UUID notatek, które nadpisały tę notatkę

Powiązania z SQLite/Neo4j:
- 1:1 z `campaign_notes.note_uuid` (SQLite) → `Qdrant.point_id`
- Statusy synchronizacji z Qdrant śledzone w `campaign_notes.qdrant_sync_status`

## Zarządzanie Połączeniami

### DatabaseConnectionManager
Centralny menedżer zarządzający wszystkimi połączeniami z bazami danych:

```java
public class DatabaseConnectionManager {
    private final SqliteRepository sqliteRepository;
    private final Neo4jRepository neo4jRepository; 
    private final QdrantRepository qdrantRepository;
}
```

### Sprawdzanie Dostępności
System sprawdza dostępność wszystkich baz przed rozpoczęciem operacji:
- **Neo4j:** `driver.verifyConnectivity()`
- **Qdrant:** `client.listCollectionsAsync()`
- **SQLite:** Zawsze dostępne (lokalna baza)

### Zamykanie Połączeń
Koordynowane zamykanie wszystkich połączeń przy wyłączaniu aplikacji.

### Walidacja Danych
- Walidacja modeli przed zapisem
- Sanityzacja etykiet Neo4j
- Sprawdzanie limitów treści notatek (500 słów)

### Obsługa Błędów
- Graceful degradation przy niedostępności baz
- Rollback operacji w przypadku błędów
- Logowanie wszystkich operacji bazodanowych
