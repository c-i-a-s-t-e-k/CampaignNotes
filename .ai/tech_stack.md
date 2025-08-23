# Technology Stack - CampaignNotes

## Struktura Katalogu Projektu:
```
CampaignNotes/
├── app/                    # Aplikacja backendowa Java
│   ├── src/main/java/      # Kod źródłowy Java
│   ├── src/test/           # Testy jednostkowe
│   ├── build.gradle        # Konfiguracja Gradle
│   └── sqlite.db           # Baza danych użytkowników i kampanii
├── frontend/               # Aplikacja React
│   ├── src/                # Kod źródłowy React
│   ├── public/             # Pliki statyczne
│   └── package.json        # Zależności Node.js
├── qdrant_storage/         # Dane bazy wektorowej Qdrant
├── .github/                # Konfiguracja CI/CD
└── gradle/                 # Wrapper Gradle
```

## Bazy Danych:

Projekt wykorzystuje architekturę trzech baz danych:
- **SQLite** - Centralna baza relacyjna dla metadanych kampanii
- **Neo4j** - Grafowa baza danych dla artefaktów i relacji
- **Qdrant** - Wektorowa baza danych dla semantycznego wyszukiwania notatek


## Frontend - React with Tailwind CSS:
- **React** will provide interactivity for UI components.
- **Tailwind CSS** allows for utility-first styling.
- **TypeScript** can be added for static typing and improved developer experience (assuming it might be added later or is desired).

## Backend - Java Application with Gradle:
- Built using **Java 21**, leveraging the JVM ecosystem.
- Managed by **Gradle 7.2** for dependency management and build automation.
- Utilizes **JUnit 5 (5.11.4)** for unit testing with modern testing capabilities
- Includes **Google Guava (30.1.1-jre)** for utility libraries

## AI - LLM Integration and Monitoring:
- Communication with various LLMs via **OpenRouter.ai**:
    - Provides access to a wide range of models (OpenAI, Anthropic, Google, etc.).
    - Enables cost management through API key limits.
- **Langfuse** for monitoring, prompt management, and evaluation:
    - Tracks AI call performance and costs.
    - Facilitates prompt development and optimization.
- **Promptfoo** for systematic prompt testing and evaluation:
    - Enables setting up test cases for prompts.
    - Helps ensure prompt quality and identify regressions.

## CI/CD and Hosting (Placeholder):
- **GitHub Actions** for Continuous Integration and Continuous Deployment pipelines (Common choice).
- Hosting platform to be determined (e.g., DigitalOcean, AWS, Google Cloud) potentially using Docker images.
