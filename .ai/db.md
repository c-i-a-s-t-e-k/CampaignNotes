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

#### Tabela `campains`
```sql
CREATE TABLE IF NOT EXISTS campains (
    uuid TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    neo4j_label TEXT NOT NULL,
    quadrant_collection_name TEXT NOT NULL
)
```

**Pola:**
- `uuid` (TEXT, PRIMARY KEY) - Unikalny identyfikator kampanii
- `name` (TEXT, NOT NULL) - Nazwa kampanii
- `neo4j_label` (TEXT, NOT NULL) - Etykieta używana w bazie Neo4j dla izolacji danych kampanii
- `quadrant_collection_name` (TEXT, NOT NULL) - Nazwa kolekcji w bazie Qdrant

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

### Kluczowe Operacje
- Zarządzanie kampaniami (CRUD)
- Zarządzanie kategoriami artefaktów
- Mapowanie kategorii do kampanii
- Ładowanie identyfikatorów kampanii dla innych baz danych

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
- `noteId` (String) - Identyfikator notatki źródłowej
- `description` (String) - Opis artefaktu
- `createdAt` (LocalDateTime) - Data utworzenia

#### Relacje (Relationships)
Relacje między artefaktami są reprezentowane jako krawędzie grafu:

**Właściwości relacji:**
- `id` (String) - Unikalny identyfikator relacji
- `label` (String) - Etykieta relacji (sanityzowana)
- `description` (String) - Opis relacji
- `reasoning` (String) - Uzasadnienie relacji
- `noteId` (String) - Identyfikator notatki źródłowej
- `campaignUuid` (String) - UUID kampanii
- `createdAt` (LocalDateTime) - Data utworzenia

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
