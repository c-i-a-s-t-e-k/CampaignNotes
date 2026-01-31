# CampaignNotes

Application designed to assist in tracking yore notes and helping organizing your RPG sessions with usage of LLM power.

## Technology Stack

### Backend
- **Java 21** - Core application
- **Gradle 7.2** - Build automation
- **SQLite** - User and campaign metadata
- **Neo4j** - Artifact relationship graph
- **Qdrant** - Vector database for semantic search

### Frontend
- **React 19** - UI framework
- **TypeScript** - Type-safe development
- **Vite** - Build tool and dev server
- **Zustand** - State management
- **React Query** - Data fetching and caching
- **Tailwind CSS** - Styling
- **Neo4j NVL** - Graph visualization

### AI Integration
- **OpenAI API** - LLM and embedding generation
- **Ollama** - Local LLM models support
- **Langfuse** - AI observability and prompt management
- **OpenTelemetry** - Distributed tracing

## Setup and Configuration

### 1. Running External Services

The application requires several external services that can be run using Docker:

#### Neo4j (Graph Database)
```bash
docker run -d \
  --name neo4j \
  -p 7474:7474 -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/your-password \
  neo4j:latest
```

#### Qdrant (Vector Database)
```bash
docker run -d \
  --name qdrant \
  -p 6333:6333 -p 6334:6334 \
  -v ./qdrant_storage:/qdrant/storage:z \
  qdrant/qdrant
```

#### Langfuse (AI Observability)
```bash
docker run -d \
  --name langfuse \
  -p 3000:3000 \
  -e DATABASE_URL=your-postgres-url \
  langfuse/langfuse:latest
```

#### Ollama (Local LLM Models)
```bash
# Install Ollama
curl -fsSL https://ollama.com/install.sh | sh

# Run a model (e.g., qwen3)
ollama run qwen3
```

### 2. Environment Configuration

Copy the `.env-example` file to `.env` and fill in the required values:

```bash
cp .env-example .env
```

The `.env-example` file contains all necessary environment variables:
- `NEO4J_URI`, `NEO4J_USER`, `NEO4J_PASSWORD` - Neo4j configuration
- `QUADRANT_GRPC_PORT`, `QUADRANT_URL` - Qdrant configuration
- `LANGFUSE_HOST`, `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY` - Langfuse configuration
- `OLLAMA_HOST` - Ollama server address
- `OPENAI_API_KEY` - OpenAI API key

## Tracking and Observability

CampaignNotes uses **OpenTelemetry** to track AI operations (embeddings, LLM generations).
Traces are exported to **Langfuse** via the OTLP endpoint for monitoring, debugging, and cost analysis.

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

### Backend

```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun
```

### Frontend

```bash
# Navigate to frontend directory
cd frontend

# Install dependencies
npm install

# Run development server
npm start

# Build for production
npm run build

# Preview production build
npm run preview
```

The frontend will be available at `http://localhost:3000`.

### Testing

```bash
# Run unit tests only
./gradlew test

# Run unit tests + integration tests
./gradlew test integrationTest

# Or in one command (clean build with all tests)
./gradlew clean build integrationTest
```

## Architecture

The application consists of two main components:

### Backend (Java/Spring)
- **CampaignNotes/** - Main application logic
- **database/** - Data access layer (SQLite, Neo4j, Qdrant)
- **llm/** - LLM services and embedding generation
- **tracking/** - Langfuse and OpenTelemetry integration
- **model/** - Domain models

### Frontend (React/TypeScript)
- **pages/** - Application pages
- **components/** - UI components
- **api/** - API clients for backend communication
- **stores/** - State management (Zustand)
- **hooks/** - Custom React hooks
- **types/** - TypeScript type definitions

### Data Flow
1. Frontend communicates with backend via REST API
2. Backend processes notes using LLM (OpenAI/Ollama)
3. Embeddings are stored in Qdrant for semantic search
4. Relationship graph is stored in Neo4j
5. All operations are tracked in Langfuse via OpenTelemetry
