# CampaignNotes

Application designed to assist in tracking and organizing your RPG sessions.

## Technology Stack

### Backend
- **Java 21** - Core application
- **Gradle 7.2** - Build automation
- **SQLite** - User and campaign metadata
- **Neo4j** - Artifact relationship graph
- **Qdrant** - Vector database for semantic search

### AI Integration
- **OpenAI API** (via OpenRouter) - LLM and embedding generation
- **Langfuse** - AI observability and prompt management
- **OpenTelemetry** - Distributed tracing

## Tracking and Observability

CampaignNotes uses **OpenTelemetry** to track AI operations (embeddings, LLM generations).
Traces are exported to **Langfuse** via the OTLP endpoint for monitoring, debugging, and cost analysis.

### Configuration

Create a `.env` file in the project root with:

```env
# Langfuse Configuration
LANGFUSE_HOST=https://cloud.langfuse.com
LANGFUSE_PUBLIC_KEY=pk-lf-...
LANGFUSE_SECRET_KEY=sk-lf-...

# OpenAI Configuration (via OpenRouter)
OPENAI_API_KEY=your-openrouter-api-key

# Database Configuration
NEO4J_URI=bolt://localhost:7687
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=your-password
```

OpenTelemetry is automatically initialized on application startup and exports traces to Langfuse.

### What is Tracked

- **Embedding Generation** - Token usage, duration, model used
- **Artifact Extraction (NAE)** - LLM calls, prompts, responses
- **Relationship Extraction (ARE)** - LLM calls, tokens, results
- **Full Workflows** - End-to-end campaign note processing

### Viewing Traces

Access your Langfuse dashboard to view:
- Execution traces for each note processed
- Token usage and costs per operation
- LLM call latencies
- Error rates and debugging information

## Building and Running

```bash
# Build the project
./gradlew build

# Run the application
./gradlew run

# Run tests
./gradlew test
```

## Architecture

The application follows a modular architecture:

- `CampaignNotes/` - Main application logic
- `CampaignNotes/database/` - Database access layer
- `CampaignNotes/llm/` - LLM and embedding services
- `CampaignNotes/tracking/` - Langfuse integration
- `CampaignNotes/tracking/otel/` - OpenTelemetry implementation
- `model/` - Domain models
