# CampaignNotes - Frontend

Interfejs użytkownika dla aplikacji CampaignNotes - narzędzia dla mistrzów gry RPG do zarządzania kampaniami za pomocą interaktywnej wizualizacji grafu.

## 🚀 Stos Technologiczny

### Core
- **React 19** - Biblioteka UI
- **TypeScript 5.7** - Statyczne typowanie (strict mode)
- **Create React App** - Tooling i konfiguracja

### Styling & UI
- **Tailwind CSS 3.4** - Utility-first CSS framework
- **Shadcn/ui** - Kolekcja dostępnych komponentów UI
- **Lucide React** - Biblioteka ikon

### State Management
- **Zustand 5** - Globalne zarządzanie stanem aplikacji
- **TanStack Query 5** - Server state management, cache, synchronizacja

### Data & API
- **Axios 1.7** - HTTP client z interceptorami
- **React Hook Form 7** - Zarządzanie formularzami

### Visualization
- **Neo4j NVL** - Biblioteka do wizualizacji grafu kampanii

### Utilities
- **react-hot-toast** - Powiadomienia użytkownika
- **clsx + tailwind-merge** - Zarządzanie klasami CSS

## 📦 Wymagania

- Node.js 18+ (zalecane: 20+)
- npm 9+ lub yarn 1.22+

## 🛠️ Instalacja

1. Zainstaluj zależności:
```bash
npm install
```

2. Skonfiguruj zmienne środowiskowe (opcjonalne, dla developmentu lokalnego):
```bash
cp .env.example .env
```

Edytuj `.env` i uzupełnij wartości:
```env
REACT_APP_API_URL=http://localhost:8080
REACT_APP_NEO4J_URI=bolt://localhost:7687
REACT_APP_NEO4J_USER=neo4j
REACT_APP_NEO4J_PASSWORD=your-password
```

3. Inicjalizuj Git hooks (automatycznie przy npm install):
```bash
npm run prepare
```

## 🏃 Uruchamianie

### Development Mode
```bash
npm start
```
Otwiera aplikację na [http://localhost:3000](http://localhost:3000).

Strona automatycznie przeładuje się po zapisaniu zmian. Błędy ESLint pojawią się w konsoli.

### Production Build
```bash
npm run build
```
Buduje zoptymalizowaną wersję produkcyjną do folderu `build/`.

### Testy
```bash
npm test
```
Uruchamia testy w trybie watch.

## 📁 Struktura Projektu

```
src/
├── api/              # Klient API, interceptory
├── assets/           # Statyczne zasoby (obrazy, ikony)
├── components/       # Komponenty React
│   └── ui/          # Komponenty Shadcn/ui
├── hooks/           # Custom hooks (useNeo4jGraph, etc.)
├── layouts/         # Layouty aplikacji
├── lib/             # Funkcje pomocnicze
├── pages/           # Komponenty stron/widoków
├── stores/          # Zustand stores
├── types/           # Definicje TypeScript
├── App.tsx          # Główny komponent
├── index.tsx        # Entry point
└── index.css        # Globalne style
```

Szczegółowy opis struktury dostępny w dokumentacji: [`.ai/frontend_structure.md`](../.ai/frontend_structure.md)

## 🎨 Shadcn/ui Components

Projekt jest skonfigurowany do pracy z Shadcn/ui. Aby dodać nowy komponent:

```bash
npx shadcn@latest add button
npx shadcn@latest add dialog
# etc.
```

Komponenty zostaną dodane do `src/components/ui/`.

## 🧪 Quality Tools

Projekt wykorzystuje:
- **ESLint** - Linting kodu TypeScript/React
- **Prettier** - Formatowanie kodu
- **Husky + lint-staged** - Automatyczne formatowanie przy commit

Pre-commit hook automatycznie:
1. Uruchamia ESLint z auto-fix
2. Formatuje kod Prettier

## 🎯 Kluczowe Funkcjonalności (Planowane)

- ✅ Podstawowa konfiguracja i struktura
- ⏳ Wizualizacja grafu kampanii (Neo4j NVL)
- ⏳ Panel zarządzania kampaniami
- ⏳ Edytor notatek z walidacją (max 500 słów)
- ⏳ Asystent AI do wyszukiwania semantycznego
- ⏳ Potwierdzanie sugestii AI
- ⏳ Persystencja stanu UI (localStorage)
- ⏳ Obsługa sesji i autoryzacji

## 🌙 Motyw

Aplikacja domyślnie używa **ciemnego motywu** (dark mode). Struktura CSS jest przygotowana pod łatwe dodanie jasnego motywu w przyszłości.

## 📝 Konwencje Kodu

- **Komponenty**: PascalCase (`CampaignList.tsx`)
- **Hooks**: camelCase z `use` (`useNeo4jGraph.ts`)
- **Utils**: camelCase (`formatDate.ts`)
- **Types**: PascalCase (`Campaign`, `Note`)
- **Stores**: camelCase + `Store` (`campaignStore.ts`)

## 🔗 Powiązane Dokumenty

- [Plan Rozwoju Frontendu](../.ai/tmp/frontend_plan.md) - Szczegółowy plan funkcjonalności
- [Struktura Frontendu](../.ai/frontend_structure.md) - Opis modułów i architektury
- [Product Requirements](../.ai/prd.md) - Wymagania produktowe

## 📄 Licencja

Projekt prywatny - CampaignNotes
