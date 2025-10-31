# Frontend – Struktura Katalogu

## Struktura Katalogu Frontend:
```
CampaignNotes/frontend/
├── src/
│   ├── api/                    # klient Axios, interceptory (JWT, obsługa błędów)
│   ├── assets/                 # statyczne zasoby
│   ├── components/             # komponenty React
│   │   └── ui/                 # komponenty Shadcn/ui
│   ├── hooks/                  # custom hooks (useNeo4jGraph, useAuth, etc.)
│   ├── layouts/                # layouty aplikacji
│   ├── lib/                    # funkcje pomocnicze
│   ├── pages/                  # komponenty stron/widoków
│   ├── stores/                 # Zustand stores i slices
│   └── types/                  # definicje TypeScript
├── tsconfig.json               # TypeScript strict mode
├── tailwind.config.js          # Tailwind (dark mode only)
└── components.json             # Shadcn/ui config

```

## Opis modułów (stan aktualny):

- **`src/api/`**: Warstwa komunikacji z backendem. Klient Axios z interceptorami obsługującymi autoryzację JWT oraz błędy API. Sesja użytkownika wygasa po 10 minutach bezczynności.

- **`src/components/`**: Komponenty React podzielone na Shadcn/ui (`ui/`) oraz komponenty aplikacji (CampaignList, NoteEditor, GraphCanvas, AssistantPanel).

- **`src/hooks/`**: Custom hooks specyficzne dla projektu:
  - `useNeo4jGraph` - enkapsulacja Neo4j Visualization Library, zarządzanie interaktywnym grafem kampanii
  - `useAuth` - zarządzanie stanem autoryzacji
  - `useLocalStorage` - persystencja stanu UI

- **`src/layouts/`**: Główny layout aplikacji z headerem, wysuwnym panelem biblioteki kampanii, płótnem grafu (centrum) i panelami bocznymi dla notatek/asystenta AI.

- **`src/stores/`**: Zarządzanie stanem globalnym (Zustand) z middleware `persist`:
  - Store wybranej kampanii
  - Store stanu UI (otwarte panele)
  - Store historii czatu z asystentem AI (persystencja per kampania)

- **`src/types/`**: Interfejsy TypeScript dla modeli domenowych (Campaign, Note, Artifact, Relationship) oraz odpowiedzi API.

## Główne Technologie:

- **React 19** + **TypeScript 5.7** (strict mode) + **Vite 6**
- **Vite 6** - dev server i bundler (HMR, ESM)
- **Tailwind CSS 3.4** + **Shadcn/ui** (komponenty UI, dark mode)
- **Zustand 5** (global state) + **TanStack Query 5** (server state, cache)
- **Axios 1.7** (HTTP) + **React Hook Form 7** (formularze)
- **Neo4j NVL 1.0.0** (@neo4j-nvl/base) - wizualizacja grafu kampanii
- **react-hot-toast** (powiadomienia)

Pozostałe elementy:
- **package.json**: zależności i skrypty npm
- **ESLint + Prettier + Husky**: linting, formatowanie, pre-commit hooks
