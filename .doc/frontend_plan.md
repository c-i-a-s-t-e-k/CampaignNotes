# Plan Rozwoju Frontendu - CampaignNotes

## 1. Przegląd (Overview)

Ten dokument opisuje planowaną architekturę, technologie i kluczowe funkcjonalności dla interfejsu użytkownika (UI) aplikacji CampaignNotes. Frontend będzie webową aplikacją typu Single Page Application (SPA) zbudowaną w oparciu o React. Głównym celem jest dostarczenie interaktywnego i wydajnego narzędzia dla mistrzów gry, z centralnym punktem w postaci wizualizacji grafu kampanii. Zgodnie z wymaganiem `PC-010`, interfejs ma być minimalistyczny, co oznacza, że elementy drugorzędne nie powinny przytłaczać użytkownika i odwracać uwagi od kluczowych treści: grafu i notatek.

## 2. Podstawowe Technologie i Zależności

Na podstawie naszej dyskusji, stos technologiczny dla frontendu został sfinalizowany:

- **Framework**: React 18+ z TypeScriptem (włączony tryb `strict`).
- **Styling**: Tailwind CSS do tworzenia utility-first UI.
- **System Komponentów**: Shadcn/ui jako zbiór konfigurowalnych, dostępnych komponentów.
- **Globalne Zarządzanie Stanem**: Zustand do zarządzania globalnym stanem UI, takim jak wybrana kampania czy zaznaczony węzeł.
- **Pobieranie Danych z Serwera**: TanStack Query (React Query) do zarządzania stanem serwera, cache'owania i obsługi zapytań API.
- **Wizualizacja Grafu**: Oficjalna biblioteka Neo4j Visualization Library (NVL) napisana w TypeScripcie.
- **Zarządzanie Formularzami**: React Hook Form do walidacji i obsługi formularzy (np. edytor notatek).
- **Powiadomienia**: `react-hot-toast` do wyświetlania globalnych, nieinwazyjnych powiadomień o błędach i sukcesach.
- **Konfiguracja Środowiskowa**: Standardowy mechanizm oparty o pliki `.env`.

## 3. Architektura UI i Opis Makiety

Interfejs użytkownika będzie składał się z kilku głównych, współpracujących ze sobą sekcji:

- **Główny Layout**:
  - **Górny Pasek (Header)**: Zawiera nazwę aplikacji, przełącznik trybu ciemnego i potencjalnie inne globalne akcje.
  - **Panel Biblioteki (Lewa strona)**: Wysuwany panel z listą dostępnych kampanii użytkownika oraz przyciskiem do tworzenia nowej. Wybór kampanii powoduje przeładowanie całego kontekstu roboczego.
  - **Płótno Grafu (Centrum)**: Główny obszar roboczy, w którym renderowana jest interaktywna wizualizacja grafu kampanii za pomocą biblioteki NVL.
  - **Panele Informacyjne (Prawa strona)**: Wyświetlają dodatkowe informacje, prawdopodobnie treść notatek lub szczegóły wybranego elementu na grafie.
  - **Panel Dolny**: Zawiera edytor do dodawania nowych notatek ("Placeholder") oraz interfejs asystenta AI.

- **Kluczowe Komponenty i Ich Interakcje**:
  - **Wizualizacja Grafu**: Będzie zarządzana przez dedykowany hook (`useNeo4jGraph`), który enkapsuluje logikę biblioteki NVL. Umożliwi to interakcję (klikanie węzłów) oraz programowe sterowanie widokiem (np. centrowanie na węźle w odpowiedzi na akcję asystenta). Interakcje z grafem będą aktualizować globalny stan w Zustand.
  - **Asystent AI ("Ask Assistant")**: Prosty interfejs oparty na polu tekstowym, który zwraca listę wyników. Może inicjować akcje na grafie.
  - **Edytor Notatek**: Pole tekstowe z walidacją po stronie klienta, ograniczającą długość notatki do 500 słów.

## 4. Kluczowe Przepływy Funkcjonalne

- **Zarządzanie Sesją**: Sesja wygasa po 10 minutach bezczynności. Gdy token autoryzacyjny straci ważność, aplikacja wyświetli modal z prośbą o ponowne zalogowanie.
- **Persystencja Stanu UI**: Stan interfejsu, taki jak ostatnio wybrana kampania, historia czatu z asystentem dla każdej kampanii oraz widok grafu (zoom, pozycja), będzie zapisywany w `localStorage` przy użyciu `persist` middleware dla Zustand.
- **Przepływ Potwierdzania Sugestii AI**: Zostanie zaimplementowany jako osobny widok (prawdopodobnie modal), w którym użytkownik zobaczy listę sugerowanych artefaktów i relacji wraz z fragmentem notatki źródłowej. Użytkownik będzie mógł edytować, usuwać i dodawać elementy przed ich zatwierdzeniem.
- **Obsługa Stanów Aplikacji**:
  - **Ładowanie**: Stany ładowania danych będą obsługiwane granularnie przy użyciu flag `isLoading`/`isFetching` z TanStack Query, wyświetlając komponenty typu "skeleton" lub spinnery.
  - **Błędy**: Błędy API będą komunikowane za pomocą globalnych powiadomień (toastów).
  - **Puste Stany**: Nowa, pusta kampania będzie wyświetlać pustą przestrzeń, z rekomendacją dodania komunikatu zachęcającego do działania.
- **Dostępność**: Aplikacja będzie spełniać minimalne wymagania dostępności, zapewniając pełną obsługę wszystkich interaktywnych elementów za pomocą klawiatury.

## 5. Planowane Prace i Strategia Implementacji

1.  **Konfiguracja Projektu**: Inicjalizacja projektu React z TypeScriptem, Tailwind CSS i konfiguracja pre-commit hooks dla lintera i formatera.
2.  **Warstwa API**: Stworzenie centralnego klienta API (np. Axios) z interceptorami do obsługi autoryzacji i błędów.
3.  **Zarządzanie Stanem**: Implementacja sklepu Zustand z podziałem na `slices` i skonfigurowanie middleware'u `persist`.
4.  **Prototyp Grafu**: Stworzenie hooka `useNeo4jGraph` i zintegrowanie biblioteki NVL w ramach prostego komponentu w celu weryfikacji API i obsługi interakcji.
5.  **Rozwój Komponentów**: Stopniowa implementacja komponentów UI zgodnie z makietą, zaczynając od layoutu, przez panel biblioteki, po bardziej złożone elementy jak edytor notatek i asystent.
6.  **CI/CD**: Skonfigurowanie pipeline'u GitHub Actions, który będzie uruchamiał testy jednostkowe dla Pull Requestów oznaczonych etykietą "frontend".

## 6. Ograniczenia i Przyszły Rozwój

- **Responsywność**: Wersja MVP będzie użyteczna na standardowych ekranach laptopów. Pełna responsywność nie jest priorytetem.
- **Motywy**: Aplikacja będzie posiadać tylko ciemny motyw. Struktura CSS zostanie jednak przygotowana pod łatwe dodanie jasnego motywu w przyszłości.
- **Skalowalność List**: Lista kampanii w panelu biblioteki nie będzie posiadać na razie funkcji wyszukiwania.
- **Synchronizacja Między Kartami**: Nie jest wymagane automatyczne synchronizowanie stanu między wieloma otwartymi kartami przeglądarki.
- **Onboarding**: W MVP nie jest planowany rozbudowany onboarding użytkownika.
