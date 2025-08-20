# Backend – Struktura Katalogu

## Struktura Katalogu Backend:
```
CampaignNotes/app/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── CampaignNotes/          # logika aplikacji i integracje
│   │   │   │   ├── database/           # dostęp do baz danych (SQLite, Neo4j, Qdrant)
│   │   │   │   ├── llm/                # integracja z LLM oraz generowanie embeddingów
│   │   │   │   └── tracking/           # monitorowanie i śledzenie (Langfuse)
│   │   │   └── model/                  # modele domenowe
│   │   └── resources/
│   └── test/
│       ├── java/
│       │   └── CampaignNotes/          # testy jednostkowe i integracyjne backendu
│       └── resources/
└── sqlite.db
```

Opis modułów (stan aktualny):
- Pakiet `CampaignNotes` (główny): zawiera punkt wejścia aplikacji, interfejs terminalowy oraz serwisy domenowe i administracyjne. Odpowiada za uruchomienie programu, zarządzanie kampaniami, notatkami oraz operacjami na artefaktach (kategorie, graf zależności) z poziomu warstwy aplikacyjnej.
- `CampaignNotes/database`: warstwa dostępu do danych. Zapewnia konfigurację i połączenia z trzema bazami (SQLite jako rejestr kampanii i użytkowników, Neo4j dla grafu artefaktów, Qdrant dla wektorowych wyszukiwań) oraz metody repozytoryjne do wykonywania operacji CRUD i zapytań specyficznych dla każdej bazy.
- `CampaignNotes/llm`: integracja z usługami LLM. Odpowiada za generowanie embeddingów oraz wywołania modeli językowych, w tym obsługę parametrów modeli i kosztów.
- `CampaignNotes/tracking`: monitoring i śledzenie zdarzeń AI. Zapewnia raportowanie metryk, logowanie promptów/odpowiedzi i integrację z narzędziami obserwowalności (Langfuse).
- `model`: modele domenowe wykorzystywane w całej aplikacji, m.in. artefakty i ich kategorie, notatki oraz relacje między nimi, a także obiekty pomocnicze powiązane z LLM (embeddingi, odpowiedzi modeli, cenniki).
- `src/main/resources` i `src/test/resources`: katalogi na zasoby konfiguracyjne i testowe (obecnie puste).
- `src/test/java/CampaignNotes`: zestaw testów jednostkowych i integracyjnych pokrywających uruchomienie aplikacji, warstwę LLM/tracking oraz logikę notatek i ładowania bazy.

Pozostałe elementy:
- `build.gradle`: konfiguracja projektu Gradle i zależności backendu.
- `sqlite.db`: lokalna baza SQLite wykorzystywana przez backend.
- `bin/` oraz `build/`: katalogi artefaktów kompilacji i uruchomienia (generowane przez IDE/Gradle).

