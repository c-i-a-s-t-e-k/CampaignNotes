# Instrukcje testowania

## Testy jednostkowe (Unit Tests)

**Opis**: Testy izolowane używające mocków, szybkie, bez połączeń zewnętrznych

**Uruchomienie**:
```bash
./gradlew test
```

**Zakres**:
- `ChatMessageTest` - testy modelu ChatMessage
- `PromptContentTest` - testy modelu PromptContent
- `LangfusePromptManagerEnhancedTest` - testy manipulacji promptami
- `SemantickSearchServiceTest` - testy serwisu wyszukiwania semantycznego z mockami

**Charakterystyka**:
- Szybkie wykonanie (< 10 sekund)
- Nie wymagają uruchomionych baz danych
- Nie generują kosztów API
- Używają mocków (Mockito) do izolacji testowanego kodu
- Uruchamiane domyślnie przy każdym buildzie

---

## Testy integracyjne (Integration Tests)

**Opis**: Testy wymagające prawdziwych połączeń z bazami danych i API

**Wymagania**:
- Uruchomione bazy danych (SQLite, Neo4j, Qdrant)
- Skonfigurowane zmienne środowiskowe:
  - `LANGFUSE_PUBLIC_KEY` - klucz publiczny Langfuse
  - `LANGFUSE_SECRET_KEY` - klucz prywatny Langfuse
  - `LANGFUSE_HOST` - host Langfuse API
  - `NEO4J_URI` - URI bazy Neo4j
  - `NEO4J_USER` - użytkownik Neo4j
  - `NEO4J_PASSWORD` - hasło Neo4j
  - `QDRANT_URL` - URL serwera Qdrant
  - `QDRANT_GRPC_PORT` - port gRPC Qdrant
- Działający skrypt: `./start_dbs.sh`

**Uruchomienie**:
```bash
# Krok 1: Uruchom bazy danych
./start_dbs.sh

# Krok 2: Uruchom testy integracyjne
./gradlew integrationTest
```

**Zakres**:
- `DataBaseLoaderTest` - testuje połączenia z SQLite, Neo4j, Qdrant
  - Tworzy i usuwa kampanie w prawdziwych bazach
  - Weryfikuje synchronizację między bazami
  
- `NoteServiceTest` - testuje pełny flow dodawania notatek
  - SQLite (metadane notatek)
  - Qdrant (embeddingi notatek)
  - Neo4j (grafy artefaktów)
  - Langfuse (tracking operacji)
  
- `OpenTelemetryIntegrationTest` - testuje integrację OpenTelemetry
  - Eksport spanów do Langfuse przez OTLP
  - Zagnieżdżone observacje (traces, generations, embeddings)
  
- `LangfuseClientTest` - testuje połączenie z Langfuse
  - Większość testów oznaczona jako `@Disabled`
  - Tylko podstawowy test połączenia aktywny

**Charakterystyka**:
- Wolne wykonanie (30-120 sekund)
- Wymagają pełnej infrastruktury
- **GENERUJĄ KOSZTY API** (Langfuse, OpenAI)
- Testują rzeczywiste integracje
- Nie uruchamiane domyślnie

---

## Uruchomienie wszystkich testów

```bash
# Uruchom unit testy + integration testy
./gradlew test integrationTest

# Lub w jednym poleceniu (clean build z wszystkimi testami)
./gradlew clean build integrationTest
```

---

## Continuous Integration (CI)

### Konfiguracja dla GitHub Actions / GitLab CI

**Zalecane ustawienie**:
- Na każdy push: uruchamiaj tylko `./gradlew test` (unit tests)
- Na merge do main: uruchamiaj `./gradlew test integrationTest` (wszystkie testy)
- Na nocne buildy: uruchamiaj pełny zestaw testów

**Przykład GitHub Actions**:
```yaml
jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
      - name: Run unit tests
        run: ./gradlew test
        
  integration-tests:
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
      - name: Start databases
        run: ./start_dbs.sh
      - name: Run integration tests
        run: ./gradlew integrationTest
        env:
          LANGFUSE_PUBLIC_KEY: ${{ secrets.LANGFUSE_PUBLIC_KEY }}
          LANGFUSE_SECRET_KEY: ${{ secrets.LANGFUSE_SECRET_KEY }}
          # ... inne zmienne środowiskowe
```

---

## Uwagi

### Koszty API
- Testy integracyjne mogą generować koszty:
  - **Langfuse**: tracking, prompt management (zazwyczaj darmowe w podstawowym planie)
  - **OpenAI**: embeddingi w `NoteServiceTest` (koszty zależą od liczby tokenów)
- Zalecane jest uruchamianie testów integracyjnych:
  - Lokalnie: tylko przed commitem do main
  - CI/CD: tylko na main branch lub przed release'em

### Izolacja danych
- Testy integracyjne używają prawdziwych baz danych
- Każdy test wykonuje cleanup w `@AfterEach`
- W przypadku przerwania testu mogą pozostać dane testowe
- Rekomendowane: osobne środowisko testowe (nie używać produkcyjnych baz)

### Debugowanie testów
```bash
# Uruchom konkretny test
./gradlew test --tests ChatMessageTest

# Uruchom konkretny test integracyjny
./gradlew integrationTest --tests DataBaseLoaderTest

# Uruchom z dodatkowym logowaniem
./gradlew test --info

# Uruchom z pełnym stack trace
./gradlew test --stacktrace
```

### Test Coverage
```bash
# Wygeneruj raport pokrycia (wymaga jacoco plugin)
./gradlew test jacocoTestReport

# Raport będzie dostępny w: build/reports/jacoco/test/html/index.html
```

---

## Struktura testów

```
app/src/test/java/CampaignNotes/
├── ChatMessageTest.java                    [UNIT]
├── PromptContentTest.java                  [UNIT]
├── LangfusePromptManagerEnhancedTest.java  [UNIT]
├── SemantickSearchServiceTest.java         [UNIT]
├── DataBaseLoaderTest.java                 [INTEGRATION]
├── NoteServiceTest.java                    [INTEGRATION]
├── LangfuseClientTest.java                 [INTEGRATION]
└── tracking/otel/
    └── OpenTelemetryIntegrationTest.java   [INTEGRATION]
```

**Legenda**:
- `[UNIT]` - Test jednostkowy (mockowany, szybki)
- `[INTEGRATION]` - Test integracyjny (prawdziwe połączenia, wolny)

---

## FAQ

**Q: Dlaczego `./gradlew test` nie uruchamia wszystkich testów?**  
A: Domyślnie uruchamiane są tylko testy jednostkowe (bez tagu `@Tag("integration")`). To oszczędza czas i koszty API podczas normalnego developmentu.

**Q: Jak uruchomić tylko jeden konkretny test integracyjny?**  
A: Użyj `./gradlew integrationTest --tests NazwaTestu`

**Q: Czy mogę uruchomić testy integracyjne bez uruchomionych baz?**  
A: Nie, testy integracyjne wymagają działających instancji SQLite, Neo4j i Qdrant. Użyj `./start_dbs.sh` przed uruchomieniem testów.

**Q: Test integracyjny się zawiesił/przerwał. Jak wyczyścić dane testowe?**  
A: Sprawdź bazy danych pod kątem kampanii testowych (nazwy zawierają "Test Campaign") i usuń je ręcznie lub uruchom ponownie test - cleanup jest w `@AfterEach`.

**Q: Ile kosztuje uruchomienie testów integracyjnych?**  
A: Zależy od użycia:
- `DataBaseLoaderTest`: ~0.00 USD (tylko bazy danych, bez API)
- `NoteServiceTest`: ~0.01-0.02 USD (embeddingi OpenAI)
- `OpenTelemetryIntegrationTest`: ~0.00 USD (tylko tracking)
- `LangfuseClientTest`: ~0.00 USD (większość disabled)

**Łącznie**: < 0.05 USD za pełne uruchomienie suite testów integracyjnych.

