#struktura pakietów - CampaignNotes Backend

### 1. `CampaignNotes.core`
**Opis:** Rdzeń aplikacji zawierający punkt wejścia oraz konfigurację globalną.
**Zawiera:** Główną klasę aplikacji, konfigurację aplikacji, stałe globalne oraz inicjalizację komponentów systemowych.

### 2. `CampaignNotes.campaign`
**Opis:** Moduł odpowiedzialny za zarządzanie kampaniami.
**Zawiera:** Serwisy do operacji CRUD na kampaniach, logikę biznesową związaną z kampaniami, zarządzanie metadanymi kampanii oraz walidację danych kampanii.

### 3. `CampaignNotes.campaign.note`
**Opis:** Moduł zarządzania notatkami w ramach kampanii.
**Zawiera:** Serwisy do przetwarzania notatek, repozytoria dostępu do danych notatek, walidatory notatek oraz logikę związaną z embeddings notatek.

### 4. `CampaignNotes.campaign.artifact`
**Opis:** Moduł do zarządzania artefaktami narracyjnymi wyodrębnianymi z notatek.
**Zawiera:** Serwisy do ekstrakcji artefaktów, zarządzanie kategoriami artefaktów, repozytoria artefaktów, procesory do analizy relacji między artefaktami oraz narzędzia do budowy grafu narracyjnego.

### 5. `CampaignNotes.database`
**Opis:** Warstwa dostępu do danych i zarządzania połączeniami z bazami danych.
**Zawiera:** Menedżer połączeń z bazami danych, repozytoria specyficzne dla każdej bazy (Neo4j, Qdrant, SQLite), konfigurację połączeń oraz narzędzia diagnostyczne dostępności baz.

### 6. `CampaignNotes.llm`
**Opis:** Moduł integracji z usługami Large Language Models.
**Zawiera:** Serwisy komunikacji z API OpenAI, generatory embeddings, abstrakcje dla różnych dostawców LLM, cache'owanie odpowiedzi oraz zarządzanie tokenami i kosztami.

### 7. `CampaignNotes.monitoring`
**Opis:** Moduł obserwacji i monitorowania działania aplikacji.
**Zawiera:** Klienty do systemów observability (Langfuse), kolektory metryk, trackery wykorzystania zasobów oraz narzędzia do debugowania i profilowania.

### 9. `CampaignNotes.admin`
**Opis:** Moduł funkcjonalności administracyjnych.
**Zawiera:** Kontrolery administracyjne, serwisy zarządzania systemem, narzędzia diagnostyczne, operacje maintenance'u oraz zarządzanie uprawnieniami.

### 11. `CampaignNotes.utils`
**Opis:** Narzędzia pomocnicze wykorzystywane w całej aplikacji.
**Zawiera:** Utility do walidacji, operacje na stringach, helpery JSON, narzędzia do pracy z datami oraz inne funkcje pomocnicze.

### 12. `CampaignNotes.exception`
**Opis:** Obsługa wyjątków specyficznych dla domeny aplikacji.
**Zawiera:** Niestandardowe wyjątki dla różnych modułów, handlery błędów, mapowanie błędów na kody odpowiedzi oraz strategie recovery.

### 13. `model`
**Opis:** Modele danych używane w całej aplikacji.
**Zawiera:** Organizację w podpakiety według domeny (campaign, note, artifact, llm, dto), obiekty transferu danych oraz encje bazodanowe.