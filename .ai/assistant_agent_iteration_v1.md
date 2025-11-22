# System Notatek - Iteracja Agentowa v1.0
## Raport Specyfikacji i Architektury

**Data:** 2025-11-22  
**Wersja:** 1.0  
**Status:** Specyfikacja do implementacji

---

## 1. Executive Summary

### 1.1 Cel Iteracji

Transformacja obecnego prostego systemu wyszukiwania semantycznego w **stateless assistant agent** zdolny do:
- Analizy zapytań użytkownika w języku naturalnym
- Autonomicznego decydowania o źródłach danych (vector search, graph queries)
- Generowania zapytań Cypher do bazy grafowej Neo4j
- Prezentacji wyników w formie tekstowej i graficznej
- Obsługi błędów i sytuacji wykraczających poza zakres

### 1.2 Kluczowe Zasady Projektowe

- **Read-Only**: Agent nie modyfikuje danych, tylko je odczytuje
- **Stateless**: Brak historii konwersacji, jedna sesja = jedno zapytanie
- **Multi-Source**: Integracja danych z Qdrant (notes, artifacts, relations) i Neo4j (graph)
- **Tracked**: Pełny tracking w Langfuse/OpenTelemetry
- **Cached**: Query result caching (5 min TTL)

### 1.3 Różnice względem Obecnego Stanu

| Aspekt | Obecny Stan | Nowa Iteracja |
|--------|-------------|---------------|
| **Interfejs** | SearchPanel (proste pole input) | AssistantPanel (delegacja do grafu) |
| **Funkcjonalność** | Tylko semantic search notatek | Multi-source + Cypher generation |
| **Workflow** | Query → Vector Search → Results | Query → Planning → Multi-step → Synthesis |
| **Prezentacja** | Lista 3 notatek | Tekst + Graf (delegowany) + Citations |
| **Inteligencja** | Brak (direct embedding search) | LLM decision making |

---

## 2. Obecny Stan Systemu - Analiza

### 2.1 Backend Architecture

**Istniejące Komponenty (do wykorzystania):**

```
CampaignNotes/
├── SemantickSearchService         # Vector search w Qdrant
├── OpenAILLMService               # LLM calls z retry
├── OpenAIEmbeddingService         # Embedding generation
├── ArtifactGraphService           # Operacje na grafie (do rozszerzenia)
├── ArtifactCategoryService        # Kategorie artefaktów
├── tracking/
│   ├── LangfuseClient             # Prompt management
│   ├── otel/OTelTraceManager      # Trace creation
│   ├── otel/OTelGenerationObservation  # LLM tracking
│   └── otel/OTelEmbeddingObservation   # Embedding tracking
└── database/
    ├── Neo4jRepository            # Neo4j driver + read-only support
    ├── QdrantRepository           # Qdrant client
    └── SqliteRepository           # Metadata storage
```

**Obecny Endpoint Wyszukiwania:**
- `POST /api/campaigns/{campaignUuid}/search`
- Controller: `SearchController`
- Service: `SemantickSearchService.searchSemanticklyNotes()`
- Zwraca: `List<SearchResultDTO>`

### 2.2 Frontend Architecture

**Istniejące Komponenty:**

```typescript
frontend/src/
├── components/
│   ├── SearchPanel.tsx            # DO ZASTĄPIENIA → AssistantPanel
│   ├── GraphCanvas.tsx            # DO WYKORZYSTANIA (delegacja)
│   └── SearchResults.tsx          # DO MODYFIKACJI
├── stores/
│   ├── uiStore.ts                 # Stan UI (do rozszerzenia)
│   ├── campaignStore.ts           # Wybrany campaign
│   └── graphStore.ts              # Stan grafu (do delegacji)
└── api/
    └── search.ts                  # DO ROZSZERZENIA o assistant API
```

**Obecny Workflow:**
1. User wpisuje query w SearchPanel
2. Submit → `POST /api/campaigns/{uuid}/search`
3. Backend zwraca top 3 notatki
4. Frontend wyświetla w SearchResults

### 2.3 Źródła Danych

**Qdrant Collections (per campaign):**

```yaml
Points Types:
  - type: "note"
    fields: [note_id, title, content, campaign_uuid, created_at, is_override]
    embedding: 1536-dim (text-embedding-3-small)
    use_case: Semantic search w notatkach
    
  - type: "artifact"
    fields: [artifact_id, name, artifact_type, description, campaign_uuid]
    embedding: 1536-dim (name + description)
    use_case: Deduplikacja + Assistant artifact search
    
  - type: "relation"
    fields: [relationship_id, source, target, label, description, reasoning]
    embedding: 1536-dim (source + label + target + description)
    use_case: Deduplikacja + Assistant relation search
```

**Neo4j Graph Structure:**

```cypher
// Nodes
(:CampaignLabel_Artifact {
  id: UUID,
  name: String,
  type: String,              # example: characters|locations|items|events
  campaignUuid: String,
  note_ids: [String],
  description: String,
  createdAt: DateTime
})

// Relationships
-[:RELATIONSHIP_LABEL {
  id: UUID,
  label: String,
  description: String,
  reasoning: String,
  note_ids: [String],
  campaignUuid: String,
  createdAt: DateTime
}]->
```

**SQLite Metadata:**
- `campains`: name, description, neo4j_label, quadrant_collection_name
- `artifact_categories`: categories per campaign
- `campaign_notes`: metadata notatek (bez content - w Qdrant)

---

## 3. Architektura Systemu Agentowego

### 3.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    FRONTEND (React)                         │
├─────────────────────────────────────────────────────────────┤
│  AssistantPanel  →  API  →  GraphCanvas (delegacja)        │
│                           └→ SourcesList                     │
└─────────────────────────────────────────────────────────────┘
                              ↓ HTTP
┌─────────────────────────────────────────────────────────────┐
│              BACKEND (Spring Boot + Java)                   │
├─────────────────────────────────────────────────────────────┤
│  AssistantController                                        │
│           ↓                                                  │
│  AssistantOrchestrator (main logic)                         │
│     ├─→ PlanningService (LLM decision)                      │
│     ├─→ DataCollectorService (multi-source)                 │
│     │      ├─→ VectorSearchService (Qdrant)                 │
│     │      └─→ GraphQueryService (Neo4j read-only)          │
│     └─→ SynthesisService (LLM response generation)          │
│                                                              │
│  + QueryResultCache (5 min TTL)                             │
│  + CypherValidator (read-only check)                        │
└─────────────────────────────────────────────────────────────┘
                    ↓                ↓              ↓
         ┌──────────┴────────┐   ┌──┴────┐   ┌────┴──────┐
         │   Qdrant          │   │ Neo4j │   │  SQLite   │
         │ (vector search)   │   │(graph)│   │(metadata) │
         └───────────────────┘   └───────┘   └───────────┘
                    ↓
         ┌──────────┴────────────────┐
         │  Langfuse (tracking)      │
         │  + OpenTelemetry          │
         └───────────────────────────┘
```

### 3.2 Decision Tree - Agent Actions

```
User Query
    ↓
Planning LLM (decision making)
    ↓
┌───────────────────────────────────────────────────────┐
│  Action Decision (one of):                            │
├───────────────────────────────────────────────────────┤
│  1. search_notes                                      │
│     → Vector search (type=note) → Synthesis          │
│                                                       │
│  2. search_artifacts_then_graph                       │
│     → Vector search (type=artifact)                   │
│     → Extract artifact_id from top result             │
│     → Generate Cypher query (with artifact_id)        │
│     → Execute Neo4j query                             │
│     → Return GraphDTO → delegate to GraphCanvas       │
│                                                       │
│  3. search_relations_then_graph                       │
│     → Vector search (type=relation)                   │
│     → Extract relationship_id                         │
│     → Generate Cypher query (with relationship_id)    │
│     → Execute Neo4j query                             │
│     → Return GraphDTO                                 │
│                                                       │
│  4. combined_search                                   │
│     → Parallel: notes + artifacts + relations         │
│     → Synthesis LLM (aggregate results)               │
│     → Optional: GraphDTO if graph data present        │
│                                                       │
│  5. clarification_needed                              │
│     → Return message asking for clarification         │
│                                                       │
│  6. out_of_scope                                      │
│     → Return polite rejection message                 │
└───────────────────────────────────────────────────────┘
```

### 3.3 Workflow Sequence Diagram

```
User         Frontend      Backend           LLM         Qdrant      Neo4j
 │              │             │               │            │           │
 │─ Zapytanie ─>│             │               │            │           │
 │              │─ POST ─────>│               │            │           │
 │              │  /assistant │               │            │           │
 │              │             │               │            │           │
 │              │             │─ Check Cache ─┤            │           │
 │              │             │<─ Cache miss ─┘            │           │
 │              │             │                            │           │
 │              │             │─ Planning ─────>│          │           │
 │              │             │<─ Action: "search_artifacts_then_graph"│
 │              │             │                            │           │
 │              │             │─ Generate embedding ────────>│         │
 │              │             │<─ Vector [1536] ────────────┘│         │
 │              │             │                            │           │
 │              │             │─ Vector search (type=artifact)─>│      │
 │              │             │<─ artifact_id, name ────────┘  │      │
 │              │             │                            │           │
 │              │             │─ Generate Cypher ──>│      │           │
 │              │             │<─ MATCH query ─────┘│      │           │
 │              │             │                            │           │
 │              │             │─ Validate Cypher ──┤       │           │
 │              │             │   (read-only check)│       │           │
 │              │             │                            │           │
 │              │             │─ Execute query ─────────────────────>│
 │              │             │<─ Graph data (nodes+edges) ─────────┘│
 │              │             │                            │           │
 │              │             │─ Synthesis ────────>│      │           │
 │              │             │   (with graph data) │      │           │
 │              │             │<─ Text response ────┘      │           │
 │              │             │                            │           │
 │              │             │─ Save to cache ────┤       │           │
 │              │             │                            │           │
 │              │<─ Response ─┤                            │           │
 │              │  {text, graphData, sources}              │           │
 │              │             │                            │           │
 │<─ Display ──┤             │                            │           │
 │   text +     │             │                            │           │
 │   delegate   │─ Update ───>│                            │           │
 │   graph      │  GraphCanvas│                            │           │
```

---

## 4. API Specification

### 4.1 Assistant Query Endpoint

**Request:**
```http
POST /api/campaigns/{campaignUuid}/assistant/query
Content-Type: application/json

{
  "query": "Jakie są relacje Adama?"
}
```

**Response (Success - Text Only):**
```json
{
  "responseType": "text",
  "textResponse": "Adam ma 3 relacje w kampanii:\n1. Zna Ewę [Notatka: Sesja 5]\n2. Walczy z Diabłem [Notatka: Sesja 3]\n3. Mieszka w Tawernie [Notatka: Sesja 7]",
  "graphData": null,
  "sources": [
    {
      "noteId": "uuid-123",
      "noteTitle": "Sesja 5 - Pierwsze spotkanie"
    },
    {
      "noteId": "uuid-456",
      "noteTitle": "Sesja 3 - Bitwa"
    }
  ],
  "executedActions": ["search_artifacts_then_graph"],
  "debugInfo": null
}
```

**Response (Success - Text + Graph):**
```json
{
  "responseType": "text_and_graph",
  "textResponse": "Adam jest postacią centralną w kampanii...",
  "graphData": {
    "nodes": [
      {
        "id": "uuid-adam",
        "name": "Adam",
        "type": "characters",
        "description": "Główny bohater",
        "campaignUuid": "campaign-123",
        "noteIds": ["note-1", "note-2"]
      },
      {
        "id": "uuid-ewa",
        "name": "Ewa",
        "type": "characters",
        "description": "Towarzyszka Adama",
        "campaignUuid": "campaign-123",
        "noteIds": ["note-5"]
      }
    ],
    "edges": [
      {
        "id": "rel-123",
        "source": "uuid-adam",
        "target": "uuid-ewa",
        "label": "KNOWS",
        "description": "Poznali się podczas bitwy",
        "reasoning": "Wspólne przeżycia w sesji 5",
        "noteIds": ["note-5"]
      }
    ]
  },
  "sources": [...],
  "executedActions": ["search_artifacts_then_graph", "cypher_query"],
  "debugInfo": null
}
```

**Response (Error - Invalid Cypher):**
```json
{
  "responseType": "error",
  "errorType": "invalid_cypher",
  "textResponse": "Przepraszam, wystąpił błąd podczas generowania zapytania do bazy grafowej. Szczegóły techniczne: [validation error]. Czy możesz przeformułować pytanie?",
  "graphData": null,
  "sources": [],
  "executedActions": ["search_artifacts_then_graph"],
  "debugInfo": {
    "generatedCypher": "MATCH (a:Campaign_Artifact) WHERE ...",
    "validationError": "Query contains forbidden keyword: DELETE"
  }
}
```

**Response (Clarification Needed):**
```json
{
  "responseType": "clarification_needed",
  "errorType": null,
  "textResponse": "Twoje pytanie jest zbyt ogólne. Czy chodzi Ci o:\n1. Wszystkie postacie w kampanii?\n2. Relacje konkretnej postaci?\n3. Coś innego?",
  "graphData": null,
  "sources": [],
  "executedActions": ["planning"],
  "debugInfo": null
}
```

**Response (Out of Scope):**
```json
{
  "responseType": "out_of_scope",
  "errorType": "out_of_scope",
  "textResponse": "Przepraszam, ale to pytanie wykracza poza moje kompetencje. Mogę pomóc Ci tylko w zakresie danych z tej kampanii (postacie, lokacje, przedmioty, wydarzenia i ich relacje).",
  "graphData": null,
  "sources": [],
  "executedActions": ["planning"],
  "debugInfo": null
}
```

### 4.2 DTO Definitions

**Java Backend:**

```java
// Request
public class AssistantQueryRequest {
    @NotBlank(message = "Query cannot be empty")
    @Size(max = 500, message = "Query too long")
    private String query;
}

// Response
public class AssistantResponse {
    private String responseType;  // text|graph|text_and_graph|error|clarification_needed|out_of_scope
    private String errorType;     // null lub invalid_cypher|out_of_scope|...
    private String textResponse;
    private GraphDTO graphData;   // może być null
    private List<SourceReference> sources;
    private List<String> executedActions;
    private Map<String, Object> debugInfo;  // null w production
}

// Source reference
public class SourceReference {
    private String noteId;
    private String noteTitle;
}

// Planning result (internal)
class PlanningResult {
    private String action;  // search_notes|search_artifacts_then_graph|...
    private String reasoning;
    private Map<String, Object> parameters;
}
```

**TypeScript Frontend:**

```typescript
// Request
interface AssistantQueryRequest {
  query: string;
}

// Response
interface AssistantResponse {
  responseType: 'text' | 'graph' | 'text_and_graph' | 'error' | 'clarification_needed' | 'out_of_scope';
  errorType?: string;
  textResponse: string;
  graphData?: Graph;  // from existing types/graph.ts
  sources: SourceReference[];
  executedActions: string[];
  debugInfo?: Record<string, any>;
}

// Source reference
interface SourceReference {
  noteId: string;
  noteTitle: string;
}
```

---

## 5. LLM Workflow - Szczegóły

### 5.1 Prompty w Langfuse

**Wszystkie prompty będą zarządzane przez Langfuse Prompt Management.**

#### Prompt 1: Planning Decision

**Nazwa w Langfuse:** `assistant-planning-v1`

**Zmienne:**
- `{{query}}` - zapytanie użytkownika
- `{{campaignName}}` - nazwa kampanii
- `{{campaignDescription}}` - opis kampanii (może być null)
- `{{categories}}` - JSON z kategoriami artefaktów (characters, locations, items, events)

**System Prompt:**
```
Jesteś asystentem AI dla systemu zarządzania kampaniami RPG. Twoim zadaniem jest analiza zapytania użytkownika i podjęcie decyzji o najlepszej akcji.

DOSTĘPNE ŹRÓDŁA DANYCH:
1. Notatki kampanii (semantic search) - treść sesji RPG
2. Artefakty (postacie, lokacje, przedmioty, wydarzenia) - w bazie wektorowej i grafowej
3. Relacje między artefaktami - w bazie wektorowej i grafowej

DOSTĘPNE AKCJE:
- search_notes: wyszukaj semantycznie w notatkach kampanii
- search_artifacts_then_graph: znajdź artefakt, potem pokaż jego relacje na grafie
- search_relations_then_graph: znajdź relację, potem pokaż związany subgraf
- combined_search: użyj wielu źródeł jednocześnie
- clarification_needed: zapytanie zbyt ogólne/niejasne
- out_of_scope: pytanie wykracza poza zakres systemu

KAMPANIA:
- Nazwa: {{campaignName}}
- Opis: {{campaignDescription}}
- Kategorie artefaktów: {{categories}}

REGUŁY:
- Jeśli pytanie dotyczy konkretnej postaci/lokacji/przedmiotu - użyj search_artifacts_then_graph
- Jeśli pytanie dotyczy relacji między elementami - użyj search_relations_then_graph lub combined_search
- Jeśli pytanie jest ogólne ("co się wydarzyło?") - użyj search_notes
- Jeśli pytanie jest zbyt ogólne/wieloznaczne - użyj clarification_needed
- Jeśli pytanie nie dotyczy kampanii (np. pytanie o zasady gry) - użyj out_of_scope

ODPOWIEDŹ:
Zwróć JSON z polami:
{
  "action": "nazwa_akcji",
  "reasoning": "krótkie uzasadnienie",
  "parameters": {
    "artifact_search_query": "query dla vector search (jeśli dotyczy)",
    "expected_cypher_scope": "relationships|full_subgraph|node_details"
  }
}
```

**User Prompt:**
```
Zapytanie użytkownika: {{query}}

Przeanalizuj to zapytanie i zdecyduj o akcji.
```

#### Prompt 2: Cypher Generation

**Nazwa w Langfuse:** `assistant-cypher-generation`

**Zmienne:**
- `{{originalQuery}}` - oryginalne zapytanie użytkownika
- `{{artifactId}}` - UUID znalezionego artefaktu
- `{{artifactName}}` - nazwa artefaktu
- `{{artifactType}}` - typ (characters|locations|items|events)
- `{{campaignLabel}}` - sanityzowana etykieta kampanii dla Neo4j
- `{{campaignUuid}}` - UUID kampanii
- `{{scope}}` - relationships|full_subgraph|node_details

**System Prompt:**
```
Jesteś ekspertem w generowaniu zapytań Cypher dla bazy Neo4j.

STRUKTURA GRAFU:
- Węzły: ({{campaignLabel}}_Artifact)
  Properties: id (UUID), name (String), type (String), campaignUuid (String), note_ids (List<String>), description (String), createdAt (DateTime)
  
- Relacje: dowolne typy, sanityzowane labels
  Properties: id (UUID), label (String), description (String), reasoning (String), note_ids (List<String>), campaignUuid (String), createdAt (DateTime)

ZNALEZIONY ARTEFAKT:
- ID: {{artifactId}}
- Nazwa: {{artifactName}}
- Typ: {{artifactType}}

ZAKRES ZAPYTANIA: {{scope}}

REGUŁY:
1. TYLKO READ-ONLY queries (MATCH, RETURN, WITH, WHERE)
2. NIGDY nie używaj: CREATE, MERGE, DELETE, SET, REMOVE, DROP
3. Zawsze filtruj po campaignUuid = "{{campaignUuid}}"
4. Dla scope="relationships": zwróć bezpośrednie relacje artefaktu + powiązane węzły (1 hop)
5. Dla scope="full_subgraph": zwróć pełny subgraf (2 hops)
6. Dla scope="node_details": zwróć tylko szczegóły węzła
7. Zwracaj ZAWSZE: nodes (as list) i relationships (as list)

PRZYKŁAD (relationships):
MATCH (a:{{campaignLabel}}_Artifact {id: "{{artifactId}}", campaignUuid: "{{campaignUuid}}"})
OPTIONAL MATCH (a)-[r]-(b:{{campaignLabel}}_Artifact)
WHERE b.campaignUuid = "{{campaignUuid}}"
RETURN a, collect(distinct r) as relationships, collect(distinct b) as connectedNodes

ODPOWIEDŹ:
Zwróć TYLKO query Cypher w JSONI'e bez dodatkowych komentarzy.

\```json
{
  "reasoning": "Clear explanation of why you constructed the query as you did, including scope decisions and security considerations",
  "cypher_query": "The actual Cypher query string"
}
\```
```

**User Prompt:**
```
Oryginalne zapytanie: {{originalQuery}}
Dodatkowe instrukcje dla modelu: {{additionalInstructions}}
Wygeneruj odpowiednie zapytanie Cypher.
```

#### Prompt 3: Response Synthesis

**Nazwa w Langfuse:** `assistant-synthesis`

**Zmienne:**
- `{{originalQuery}}` - zapytanie użytkownika
- `{{action}}` - wykonana akcja
- `{{vectorResults}}` - wyniki z vector search (JSON)
- `{{graphResults}}` - wyniki z Neo4j (JSON) - opcjonalne
- `{{campaignName}}` - nazwa kampanii

**System Prompt:**
```
Jesteś pomocnym asystentem dla gracza RPG. Twoim zadaniem jest stworzenie spójnej odpowiedzi na podstawie zebranych danych.

KAMPANIA: {{campaignName}}

ZAPYTANIE UŻYTKOWNIKA: {{originalQuery}}

WYKONANA AKCJA: {{action}}

ZEBRANE DANE:
{{#if vectorResults}}
Wyniki wyszukiwania wektorowego:
{{vectorResults}}
{{/if}}

{{#if graphResults}}
Wyniki z grafu:
{{graphResults}}
{{/if}}

ZADANIE:
Stwórz naturalną odpowiedź w języku polskim, która:
1. Odpowiada bezpośrednio na pytanie użytkownika
2. Zawiera inline citations [Notatka: Tytuł] dla każdej informacji
3. Jest zwięzła (max 300 słów)
4. Jest pomocna i przyjazna
5. Nie wymyśla informacji spoza dostarczonych danych

JEŚLI dane zawierają graf: krótko opisz znalezione relacje, graf będzie pokazany osobno.

JEŚLI brak wyników: poinformuj grzecznie i zasugeruj doprecyzowanie zapytania.
```

**User Prompt:**
```
Wygeneruj odpowiedź.
```

### 5.2 Model Configuration

```java
// Planning & Synthesis
private static final String PLANNING_MODEL = "o3-mini";
private static final String SYNTHESIS_MODEL = "o3-mini";

// Cypher generation - może być tańszy model w przyszłości
private static final String CYPHER_MODEL = "gpt3.5-turbo";
```

### 5.3 Retry Strategy

```java
// Dla wszystkich LLM calls
private static final int MAX_RETRIES = 2;
private static final int RETRY_DELAY_MS = 1000;  // exponential backoff

// Timeout per LLM call
private static final Duration LLM_TIMEOUT = Duration.ofSeconds(30);
```

---

## 6. Backend Implementation Details

### 6.1 New Service Classes

```
app/src/main/java/CampaignNotes/assistant/
├── AssistantOrchestrator.java          # Main coordinator
├── PlanningService.java                # LLM decision making
├── DataCollectorService.java           # Multi-source data gathering
├── VectorSearchService.java            # Qdrant operations (refactor)
├── GraphQueryService.java              # Neo4j read-only queries
├── CypherGenerationService.java        # LLM → Cypher
├── CypherValidator.java                # Security checks
├── SynthesisService.java               # Final response LLM
└── QueryResultCache.java               # Spring Cache

app/src/main/java/CampaignNotes/controller/
└── AssistantController.java            # REST endpoint

app/src/main/java/CampaignNotes/dto/
├── AssistantQueryRequest.java
├── AssistantResponse.java
├── SourceReference.java
└── assistant/
    ├── PlanningResult.java
    └── DataCollectionResult.java
```

### 6.2 Key Service Interactions

**AssistantOrchestrator:**
```java
@Service
public class AssistantOrchestrator {
    
    private final PlanningService planningService;
    private final DataCollectorService dataCollector;
    private final CypherGenerationService cypherGenerator;
    private final GraphQueryService graphQueryService;
    private final SynthesisService synthesisService;
    private final OTelTraceManager traceManager;
    
    @Cacheable(value = "assistantQueryCache", 
               key = "#campaignUuid + ':' + #query",
               unless = "#result.responseType == 'error'")
    public AssistantResponse processQuery(String campaignUuid, String query) {
        
        try (OTelTrace trace = traceManager.createTrace(
            "assistant-query", campaignUuid, null, null, query)) {
            
            // 1. Planning phase
            PlanningResult plan = planningService.decideAction(campaignUuid, query, trace);
            
            // 2. Handle special cases
            if (plan.getAction().equals("clarification_needed") || 
                plan.getAction().equals("out_of_scope")) {
                return buildSpecialResponse(plan);
            }
            
            // 3. Data collection phase
            DataCollectionResult data = dataCollector.collectData(
                campaignUuid, query, plan, trace);
            
            // 4. Cypher generation & execution (if needed)
            if (plan.requiresGraphQuery()) {
                GraphDTO graphData = executeGraphQuery(
                    campaignUuid, query, data, plan, trace);
                data.setGraphData(graphData);
            }
            
            // 5. Synthesis phase
            AssistantResponse response = synthesisService.synthesizeResponse(
                campaignUuid, query, plan, data, trace);
            
            trace.setSuccess();
            return response;
            
        } catch (Exception e) {
            return buildErrorResponse(e);
        }
    }
}
```

**CypherValidator:**
```java
@Component
public class CypherValidator {
    
    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
        "CREATE", "MERGE", "DELETE", "SET", "REMOVE", "DROP",
        "DETACH DELETE", "CREATE INDEX", "CREATE CONSTRAINT"
    );
    
    public ValidationResult validate(String cypherQuery) {
        String upperQuery = cypherQuery.toUpperCase();
        
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (upperQuery.contains(keyword)) {
                return ValidationResult.invalid(
                    "Query contains forbidden keyword: " + keyword);
            }
        }
        
        // Additional checks: must contain MATCH/RETURN
        if (!upperQuery.contains("MATCH") || !upperQuery.contains("RETURN")) {
            return ValidationResult.invalid(
                "Query must contain MATCH and RETURN clauses");
        }
        
        return ValidationResult.valid();
    }
}
```

**GraphQueryService (Read-Only):**
```java
@Service
public class GraphQueryService {
    
    private final Neo4jRepository neo4jRepository;
    private final CypherValidator validator;
    
    public GraphDTO executeReadOnlyQuery(String cypherQuery, 
                                         Map<String, Object> parameters) {
        
        // 1. Validate
        ValidationResult validation = validator.validate(cypherQuery);
        if (!validation.isValid()) {
            throw new InvalidCypherException(validation.getError());
        }
        
        // 2. Execute in read-only transaction
        Driver driver = neo4jRepository.getDriver();
        
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(cypherQuery, parameters);
                return parseResultToGraphDTO(result);
            });
        }
    }
    
    private GraphDTO parseResultToGraphDTO(Result result) {
        // Parse Neo4j Result into GraphDTO (nodes + edges)
        // ...
    }
}
```

### 6.3 Caching Configuration

```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("assistantQueryCache");
    }
    
    // Alternative: Redis for distributed cache
    // @Bean
    // public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
    //     RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
    //         .entryTtl(Duration.ofMinutes(5));
    //     return RedisCacheManager.builder(factory)
    //         .cacheDefaults(config)
    //         .build();
    // }
}

// Cache invalidation on note creation
@Service
public class NoteService {
    
    @Autowired
    private CacheManager cacheManager;
    
    @CacheEvict(value = "assistantQueryCache", allEntries = true)
    public Note createNote(...) {
        // Note creation logic
        // Cache is automatically invalidated after this method
    }
}
```

---

## 7. Frontend Implementation Details

### 7.1 New Components

```
frontend/src/
├── components/
│   ├── AssistantPanel.tsx         # NEW - replaces SearchPanel
│   ├── AssistantResponse.tsx      # NEW - display response
│   └── SourcesList.tsx            # NEW - clickable sources
├── stores/
│   └── assistantStore.ts          # NEW - assistant state
├── api/
│   └── assistant.ts               # NEW - API calls
└── types/
    └── assistant.ts               # NEW - TypeScript types
```

### 7.2 AssistantPanel Component

```typescript
// components/AssistantPanel.tsx
import React, { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { submitAssistantQuery } from '../api/assistant';
import { useAssistantStore } from '../stores/assistantStore';
import { useCampaignStore } from '../stores/campaignStore';
import { useGraphStore } from '../stores/graphStore';
import { Input } from './ui/input';
import { Button } from './ui/button';
import { Card } from './ui/card';
import { Loader2, Send } from 'lucide-react';
import AssistantResponse from './AssistantResponse';
import toast from 'react-hot-toast';

const AssistantPanel: React.FC = () => {
  const { selectedCampaign } = useCampaignStore();
  const { currentQuery, setCurrentQuery, setResponse, clearResponse } = useAssistantStore();
  const { setGraphData } = useGraphStore();

  const queryMutation = useMutation({
    mutationFn: (query: string) => {
      if (!selectedCampaign) {
        throw new Error('No campaign selected');
      }
      return submitAssistantQuery(selectedCampaign.uuid, { query });
    },
    onSuccess: (response) => {
      setResponse(response);
      
      // Delegate graph rendering if present
      if (response.graphData) {
        setGraphData(response.graphData);
      }
      
      if (response.responseType === 'error') {
        toast.error('Wystąpił błąd podczas przetwarzania zapytania');
      }
    },
    onError: (error) => {
      toast.error('Błąd komunikacji z serwerem');
      console.error(error);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!selectedCampaign) {
      toast.error('Wybierz kampanię');
      return;
    }

    if (!currentQuery.trim()) {
      toast.error('Wpisz pytanie');
      return;
    }

    queryMutation.mutate(currentQuery);
  };

  if (!selectedCampaign) {
    return (
      <Card className="p-6">
        <p className="text-sm text-muted-foreground text-center">
          Wybierz kampanię aby zadać pytanie
        </p>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <Card className="p-4">
        <h3 className="text-lg font-semibold mb-4">Asystent Kampanii</h3>
        <form onSubmit={handleSubmit} className="space-y-3">
          <div className="flex gap-2">
            <Input
              placeholder="Zadaj pytanie o kampanię..."
              value={currentQuery}
              onChange={(e) => setCurrentQuery(e.target.value)}
              disabled={queryMutation.isPending}
            />
            <Button
              type="submit"
              disabled={queryMutation.isPending || !currentQuery.trim()}
            >
              {queryMutation.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Send className="h-4 w-4" />
              )}
            </Button>
          </div>
        </form>
      </Card>

      {queryMutation.isPending && (
        <Card className="p-4">
          <div className="flex items-center gap-2">
            <Loader2 className="h-4 w-4 animate-spin" />
            <span className="text-sm text-muted-foreground">Loading...</span>
          </div>
        </Card>
      )}

      {queryMutation.data && <AssistantResponse response={queryMutation.data} />}
    </div>
  );
};

export default AssistantPanel;
```

### 7.3 Assistant Store

```typescript
// stores/assistantStore.ts
import { create } from 'zustand';
import { AssistantResponse } from '../types/assistant';

interface AssistantStore {
  currentQuery: string;
  currentResponse: AssistantResponse | null;
  isLoading: boolean;
  error: string | null;
  
  // Actions
  setCurrentQuery: (query: string) => void;
  setResponse: (response: AssistantResponse) => void;
  clearResponse: () => void;
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
}

export const useAssistantStore = create<AssistantStore>((set) => ({
  currentQuery: '',
  currentResponse: null,
  isLoading: false,
  error: null,
  
  setCurrentQuery: (query) => set({ currentQuery: query }),
  setResponse: (response) => set({ currentResponse: response }),
  clearResponse: () => set({ currentResponse: null, currentQuery: '', error: null }),
  setLoading: (loading) => set({ isLoading: loading }),
  setError: (error) => set({ error }),
}));

// Note: Store czyści się przy zmianie kampanii
// To będzie obsłużone w campaignStore via useEffect
```

### 7.4 Campaign Change Handler

```typescript
// stores/campaignStore.ts (modify)
import { useAssistantStore } from './assistantStore';

export const useCampaignStore = create<CampaignStore>((set) => ({
  // ... existing code ...
  
  setSelectedCampaign: (campaign) => {
    set({ selectedCampaign: campaign });
    
    // Clear assistant state on campaign change
    useAssistantStore.getState().clearResponse();
  },
}));
```

### 7.5 API Client

```typescript
// api/assistant.ts
import { axiosInstance } from './axios';
import { AssistantQueryRequest, AssistantResponse } from '../types/assistant';

export const submitAssistantQuery = async (
  campaignUuid: string,
  request: AssistantQueryRequest
): Promise<AssistantResponse> => {
  const { data } = await axiosInstance.post<AssistantResponse>(
    `/api/campaigns/${campaignUuid}/assistant/query`,
    request
  );
  return data;
};
```

---

## 8. Tracking & Observability

### 8.1 OpenTelemetry Trace Structure

```
Trace: "assistant-query"
  Attributes:
    - campaign.id
    - system: "campaign-notes"
    - langfuse.trace.name: "assistant-query"
    - input: user query
    - user.id (if available)
  
  Span 1: "planning-decision" (generation)
    - gen_ai.system: "openai"
    - gen_ai.request.model: "o3-mini"
    - gen_ai.prompt: planning prompt
    - gen_ai.response: JSON decision
    - gen_ai.usage.input_tokens
    - gen_ai.usage.output_tokens
    - custom.action_decided
    - custom.reasoning
  
  Span 2: "vector-search-artifacts" (retrieval)
    - db.system: "qdrant"
    - db.operation: "search"
    - custom.query_embedding_tokens
    - custom.filter: "type=artifact"
    - custom.limit: 5
    - custom.results_count
  
  Span 3: "cypher-generation" (generation)
    - gen_ai.system: "openai"
    - gen_ai.request.model: "o3-mini"
    - custom.artifact_id
    - custom.scope
    - gen_ai.response: Cypher query
  
  Span 4: "neo4j-query-execution" (query)
    - db.system: "neo4j"
    - db.operation: "read"
    - db.statement: Cypher query
    - custom.nodes_returned
    - custom.edges_returned
  
  Span 5: "response-synthesis" (generation)
    - gen_ai.system: "openai"
    - gen_ai.request.model: "o3-mini"
    - custom.sources_count
    - custom.graph_included: true/false
    - gen_ai.response: final text
  
  Final Trace Attributes:
    - output: response JSON
    - latency_ms
    - success: true/false
```

### 8.2 Cost Tracking

```java
// Each LLM call reports to Langfuse with:
- Model used
- Input tokens
- Output tokens
- Cost calculation (from ModelPricing)

// Langfuse dashboard will show:
- Cost per query
- Cost per campaign
- Cost per action type
- Total daily/monthly costs
```

---

## 9. Security & Validation

### 9.1 Input Validation

```java
// Controller level
@PostMapping("/query")
public ResponseEntity<AssistantResponse> query(
    @PathVariable String campaignUuid,
    @Valid @RequestBody AssistantQueryRequest request) {
    
    // Spring validation:
    // - query: @NotBlank, @Size(max=200)
    // - campaignUuid: validated in service
}

// Service level
- Campaign ownership validation
- Campaign existence check
- Query sanitization (remove dangerous characters)
```

### 9.2 Cypher Security

```java
// 3-level protection:
1. Regex validation (forbidden keywords)
2. Neo4j read-only transaction (session.executeRead)
3. Timeout protection (30s max)

// Additional: parameter binding (prevent injection)
Map<String, Object> params = Map.of(
    "campaignUuid", campaignUuid,
    "artifactId", artifactId
);
tx.run(cypher, params);  // Never string concatenation!
```

### 9.3 Rate Limiting (Future)

```java
// TODO for production:
@RateLimiter(name = "assistantApi", fallbackMethod = "rateLimitFallback")
public AssistantResponse processQuery(...) {
    // ...
}

// Suggested limits:
// - 10 queries per minute per user
// - 100 queries per hour per campaign
```

---

## 10. Testing Strategy

### 10.1 Unit Tests

```java
// PlanningServiceTest.java
@Test
void shouldDecideSearchArtifactsForCharacterQuery() {
    String query = "Jakie są relacje Adama?";
    PlanningResult result = planningService.decideAction(campaignUuid, query, trace);
    assertEquals("search_artifacts_then_graph", result.getAction());
}

// CypherValidatorTest.java
@Test
void shouldRejectDeleteQuery() {
    String cypher = "MATCH (a) DELETE a";
    ValidationResult result = validator.validate(cypher);
    assertFalse(result.isValid());
}

// GraphQueryServiceTest.java (with embedded Neo4j)
@Test
void shouldExecuteReadOnlyQuery() {
    String cypher = "MATCH (a) WHERE a.id = $id RETURN a";
    GraphDTO result = service.executeReadOnlyQuery(cypher, params);
    assertNotNull(result);
}
```

### 10.2 Integration Tests

```java
// AssistantControllerIntegrationTest.java
@SpringBootTest
@AutoConfigureMockMvc
class AssistantControllerIntegrationTest {
    
    @Test
    void shouldReturnTextResponseForNoteSearch() {
        // Mock LLM responses
        // Execute request
        // Verify response structure
    }
    
    @Test
    void shouldReturnGraphForArtifactQuery() {
        // Setup: create campaign with artifacts in Neo4j
        // Mock LLM responses
        // Execute request
        // Verify GraphDTO is returned
    }
}
```

### 10.3 E2E Tests (Frontend)

```typescript
// AssistantPanel.test.tsx
describe('AssistantPanel', () => {
  it('should submit query and display response', async () => {
    render(<AssistantPanel />);
    
    const input = screen.getByPlaceholderText('Zadaj pytanie...');
    await userEvent.type(input, 'Jakie są relacje Adama?');
    
    const button = screen.getByRole('button', { name: /send/i });
    await userEvent.click(button);
    
    await waitFor(() => {
      expect(screen.getByText(/Adam ma 3 relacje/)).toBeInTheDocument();
    });
  });
});
```

---

## 11. Implementation Roadmap

### 11.1 Phase 1: Backend Core (Week 1)

**Priorytet 1 - Podstawowa Infrastruktura:**
- [ ] Utworzenie pakietu `assistant/` z pustymi klasami serwisów
- [ ] `AssistantController` + `AssistantQueryRequest/Response` DTOs
- [ ] `CypherValidator` z testami unit
- [ ] `QueryResultCache` configuration

**Priorytet 2 - LLM Integration:**
- [ ] `PlanningService` z integracją Langfuse prompts
- [ ] Utworzenie 3 promptów w Langfuse (planning, cypher-gen, synthesis)
- [ ] `CypherGenerationService` z LLM call + validation
- [ ] `SynthesisService` z LLM call

**Priorytet 3 - Data Layer:**
- [ ] `GraphQueryService` z read-only Neo4j queries
- [ ] `VectorSearchService` (refactor `SemantickSearchService`)
- [ ] `DataCollectorService` orchestrating searches

**Priorytet 4 - Orchestration:**
- [ ] `AssistantOrchestrator` łączący wszystkie serwisy
- [ ] OpenTelemetry tracking dla każdego kroku
- [ ] Error handling i response building

### 11.2 Phase 2: Frontend (Week 2)

**Priorytet 1 - Components:**
- [ ] `AssistantPanel.tsx` (replace `SearchPanel`)
- [ ] `AssistantResponse.tsx` dla display
- [ ] `SourcesList.tsx` z klikalnymi linkami do notatek

**Priorytet 2 - State Management:**
- [ ] `assistantStore.ts` z Zustand
- [ ] Integracja z `campaignStore` (clear on change)
- [ ] Integracja z `graphStore` (delegacja GraphDTO)

**Priorytet 3 - API Integration:**
- [ ] `api/assistant.ts` client
- [ ] TypeScript types (`types/assistant.ts`)
- [ ] TanStack Query mutation

**Priorytet 4 - UI/UX:**
- [ ] Loading states
- [ ] Error displays
- [ ] Source citations jako linki

### 11.3 Phase 3: Testing & Refinement (Week 3)

**Priorytet 1 - Backend Tests:**
- [ ] Unit tests dla wszystkich serwisów
- [ ] Integration tests dla `AssistantController`
- [ ] Cypher validation tests

**Priorytet 2 - Frontend Tests:**
- [ ] Component tests (React Testing Library)
- [ ] Store tests
- [ ] API mock tests

**Priorytet 3 - Manual Testing:**
- [ ] Test każdego action type (6 scenariuszy)
- [ ] Test error cases
- [ ] Test cache behavior

**Priorytet 4 - Documentation:**
- [ ] API documentation (Springdoc)
- [ ] README update
- [ ] Deployment guide

### 11.4 Phase 4: Optimization & Polish (Week 4)

**Priorytet 1 - Performance:**
- [ ] Cache hit rate monitoring
- [ ] LLM latency optimization
- [ ] Query optimization (Neo4j + Qdrant)

**Priorytet 2 - Monitoring:**
- [ ] Langfuse dashboard setup
- [ ] Cost tracking alerts
- [ ] Error rate monitoring

**Priorytet 3 - Polish:**
- [ ] UI feedback improvements
- [ ] Better error messages
- [ ] Loading state enhancements

---

## 12. Riscs & Mitigation

### 12.1 Technical Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| **LLM generuje niepoprawny Cypher** | HIGH | MEDIUM | 3-level validation + retry logic |
| **Timeout przekroczony (>10s)** | MEDIUM | LOW | Cache + async processing (future) |
| **Neo4j read-only bypass** | HIGH | LOW | Session.executeRead() + validation |
| **Cache invalidation problems** | MEDIUM | MEDIUM | Clear cache on note creation |
| **High LLM costs** | MEDIUM | HIGH | Monitoring + alerts + prompt optimization |

### 12.2 UX Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| **Długi czas oczekiwania** | MEDIUM | HIGH | Loading feedback + cache |
| **Niejasne odpowiedzi LLM** | MEDIUM | MEDIUM | Prompt engineering + few-shot examples |
| **Brak wizualnego feedbacku dla grafu** | LOW | LOW | Clear message "Zobacz graf powyżej" |

---

## 13. Future Enhancements (Post-MVP)

### 13.1 Iteracja 2: Konwersacyjność

- Historia konwersacji (per kampania, persystowana)
- Kontekst poprzednich pytań
- Follow-up questions
- Multi-turn reasoning

### 13.2 Iteracja 3: Advanced Features

- Highlight + zoom w głównym grafie
- Streaming responses (progressive display)
- Voice input (speech-to-text)
- Export odpowiedzi (PDF/Markdown)

### 13.3 Iteracja 4: Performance

- Async processing z job queue
- Parallel LLM calls gdzie możliwe
- Redis cache (distributed)
- Query complexity limits

---

## 14. Success Metrics

### 14.1 Functional Metrics

- **Accuracy**: >75% queries zwracają poprawne wyniki (manual evaluation)
- **Coverage**: >90% queries są obsługiwane (nie out_of_scope)
- **Cypher Success Rate**: >95% wygenerowanych queries są poprawne

### 14.2 Performance Metrics

- **Latency P50**: <5s response time
- **Latency P95**: <15s response time
- **Cache Hit Rate**: >30% (po tygodniu użycia)

### 14.3 Cost Metrics

- **Cost per query**: <$0.05 (średnia)
- **Monthly cost**: <$50 dla typowego użytkownika (100 queries/month)

---

## 15. Appendix: Code Examples

### 15.1 Full AssistantOrchestrator Implementation Sketch

```java
@Service
public class AssistantOrchestrator {
    
    private final PlanningService planningService;
    private final DataCollectorService dataCollector;
    private final CypherGenerationService cypherGenerator;
    private final GraphQueryService graphQueryService;
    private final SynthesisService synthesisService;
    private final OTelTraceManager traceManager;
    private final CampaignManager campaignManager;
    
    @Cacheable(value = "assistantQueryCache", 
               key = "#campaignUuid + ':' + #query")
    public AssistantResponse processQuery(String campaignUuid, String query) {
        
        // Validate campaign
        Campain campaign = campaignManager.getCampaignByUuid(campaignUuid);
        if (campaign == null) {
            return AssistantResponse.error("Campaign not found");
        }
        
        try (OTelTrace trace = traceManager.createTrace(
            "assistant-query", campaignUuid, null, null, query)) {
            
            trace.setAttribute("query.length", query.length());
            trace.addEvent("query_received");
            
            // PHASE 1: Planning
            trace.addEvent("planning_started");
            PlanningResult plan = planningService.decideAction(campaign, query, trace);
            trace.setAttribute("action.decided", plan.getAction());
            trace.setAttribute("action.reasoning", plan.getReasoning());
            trace.addEvent("planning_completed");
            
            // Handle special cases immediately
            if ("clarification_needed".equals(plan.getAction())) {
                trace.setStatus(true, "Clarification requested");
                return AssistantResponse.clarificationNeeded(
                    plan.getParameters().get("message").toString()
                );
            }
            
            if ("out_of_scope".equals(plan.getAction())) {
                trace.setStatus(true, "Out of scope");
                return AssistantResponse.outOfScope();
            }
            
            // PHASE 2: Data Collection
            trace.addEvent("data_collection_started");
            DataCollectionResult collectedData = dataCollector.collectData(
                campaign, query, plan, trace);
            trace.setAttribute("data.sources_count", collectedData.getSourcesCount());
            trace.addEvent("data_collection_completed");
            
            // PHASE 3: Graph Query (if needed)
            GraphDTO graphData = null;
            if (plan.requiresGraphQuery()) {
                trace.addEvent("graph_query_started");
                
                // Generate Cypher
                String cypher = cypherGenerator.generateCypher(
                    campaign, query, collectedData, plan, trace);
                trace.setAttribute("cypher.generated", cypher);
                
                // Execute
                try {
                    graphData = graphQueryService.executeReadOnlyQuery(
                        cypher, 
                        buildParameters(campaign, collectedData)
                    );
                    trace.setAttribute("graph.nodes_count", graphData.getNodes().size());
                    trace.setAttribute("graph.edges_count", graphData.getEdges().size());
                    trace.addEvent("graph_query_completed");
                    
                } catch (InvalidCypherException e) {
                    trace.addEvent("graph_query_failed");
                    trace.setAttribute("error.cypher", e.getMessage());
                    return AssistantResponse.error(
                        "invalid_cypher",
                        "Wystąpił błąd podczas generowania zapytania do grafu. " +
                        "Szczegóły: " + e.getMessage(),
                        Map.of("generatedCypher", cypher, "error", e.getMessage())
                    );
                }
            }
            
            // PHASE 4: Synthesis
            trace.addEvent("synthesis_started");
            AssistantResponse response = synthesisService.synthesizeResponse(
                campaign, query, plan, collectedData, graphData, trace);
            trace.addEvent("synthesis_completed");
            
            trace.setAttribute("response.type", response.getResponseType());
            trace.setAttribute("response.has_graph", graphData != null);
            trace.setStatus(true, "Query processed successfully");
            
            return response;
            
        } catch (Exception e) {
            LOGGER.error("Error processing assistant query", e);
            return AssistantResponse.error(
                "internal_error",
                "Przepraszam, wystąpił nieoczekiwany błąd. Spróbuj ponownie.",
                Map.of("error", e.getMessage())
            );
        }
    }
    
    private Map<String, Object> buildParameters(Campain campaign, 
                                                 DataCollectionResult data) {
        Map<String, Object> params = new HashMap<>();
        params.put("campaignUuid", campaign.getUuid());
        
        if (data.getFoundArtifact() != null) {
            params.put("artifactId", data.getFoundArtifact().getId());
        }
        
        return params;
    }
}
```

### 15.2 Complete Response Builder Pattern

```java
public class AssistantResponse {
    
    private String responseType;
    private String errorType;
    private String textResponse;
    private GraphDTO graphData;
    private List<SourceReference> sources;
    private List<String> executedActions;
    private Map<String, Object> debugInfo;
    
    // Builder for text response
    public static AssistantResponse text(String response, 
                                         List<SourceReference> sources,
                                         List<String> actions) {
        AssistantResponse ar = new AssistantResponse();
        ar.responseType = "text";
        ar.textResponse = response;
        ar.sources = sources;
        ar.executedActions = actions;
        return ar;
    }
    
    // Builder for text + graph
    public static AssistantResponse textAndGraph(String response,
                                                  GraphDTO graph,
                                                  List<SourceReference> sources,
                                                  List<String> actions) {
        AssistantResponse ar = new AssistantResponse();
        ar.responseType = "text_and_graph";
        ar.textResponse = response;
        ar.graphData = graph;
        ar.sources = sources;
        ar.executedActions = actions;
        return ar;
    }
    
    // Builder for errors
    public static AssistantResponse error(String errorType, 
                                          String message,
                                          Map<String, Object> debug) {
        AssistantResponse ar = new AssistantResponse();
        ar.responseType = "error";
        ar.errorType = errorType;
        ar.textResponse = message;
        ar.sources = Collections.emptyList();
        ar.executedActions = Collections.emptyList();
        ar.debugInfo = debug;
        return ar;
    }
    
    // ... getters/setters ...
}
```

---

## 16. Glossary

- **Agent**: Autonomiczny system AI podejmujący decyzje o źródłach danych
- **Planning**: Faza decyzyjna określająca akcję do wykonania
- **Synthesis**: Faza generowania finalnej odpowiedzi tekstowej
- **Read-Only**: Operacje tylko do odczytu, bez modyfikacji danych
- **Stateless**: Brak pamięci poprzednich interakcji
- **Delegation**: Przekazanie renderowania grafu do dedykowanego komponentu
- **Inline Citation**: Odniesienie do źródła wbudowane w tekst [Notatka: X]

---

## 17. References

- [PRD.md](.ai/prd.md) - Product Requirements Document
- [db.md](.ai/db.md) - Database Schema Documentation
- [backend_structure.md](.ai/backend_structure.md) - Backend Architecture
- [frontend_structure.md](.ai/frontend_structure.md) - Frontend Architecture
- [deduplication_workflow.md](.ai/workflows/deduplication_workflow.md) - Existing Workflow Example

---

**Koniec Raportu**

---

## Notes for Implementers

Ten dokument stanowi kompleksową specyfikację do implementacji. Podczas implementacji:

1. **Priorytetyzuj zgodnie z roadmapem** (sekcja 11)
2. **Używaj OpenTelemetry** dla wszystkich LLM calls (sekcja 8)
3. **Testuj security** (Cypher validation) na każdym etapie (sekcja 9)
4. **Monitoruj koszty** od pierwszego dnia (Langfuse dashboard)
5. **Iteruj na promptach** - to kluczowy element jakości

Powodzenia! 🚀

