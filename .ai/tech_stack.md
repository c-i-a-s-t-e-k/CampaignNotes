# Technology Stack - CampaignNotes

## Frontend - React with Tailwind CSS:
- **React** will provide interactivity for UI components.
- **Tailwind CSS** allows for utility-first styling.
- **TypeScript** can be added for static typing and improved developer experience (assuming it might be added later or is desired).

## Backend - Java Application with Gradle:
- Built using **Java**, leveraging the JVM ecosystem.
- Managed by **Gradle 7.2** for dependency management and build automation.
- Utilizes **JUnit 4 (4.13.2)** for unit testing.
- Includes **Google Guava (30.1.1-jre)** for utility libraries.
- Uses **Neo4j** for graph database storage (based on PRD).
- Uses **Qdrant** for vector database and semantic search (based on PRD).

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
