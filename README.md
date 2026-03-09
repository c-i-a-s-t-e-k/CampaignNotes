# CampaignNotes

The CampaignNotes application is a dedicated narrative note-taking tool designed primarily for Game Masters (GMs) in RPG games.

## 1. Problem Statement
Many narrative creators encounter difficulties due to the limitations of human working memory when running multi-threaded campaigns. Remembering all character motivations or connections between locations can be very challenging, and the lack of quick access to specific information from previous sessions poses a serious threat to the integrity and consistency of the story. CampaignNotes, acting as an AI assistant, eliminates these limitations through its integrated note-taking system, a visualization format for multi-threaded narratives (directed graphs), and an automated history search mechanism, relieving the Game Master's memory burden.

## 2. How It Works
The application, powered by large language models, processes inputted notes, transforming them into a relational graph structure and saving them as semantic vectors. The artifacts and relationships extracted by AI can then be interacted with – you simply need to ask the built-in chat assistant questions in natural language. The tool quickly analyzes the constructed narrative structure and locates the necessary facts, making it easier for the user to reconstruct the entire scenario.

Watch the video demonstrating the application in action:
[How It Works Demonstration](https://www.youtube.com/watch?v=wWOqkW150o0)

## 3. Project Architecture

The diagram below illustrates the architectural structure of the entire system and its interacting modules.

![Project Architecture](tmp/Architektura_projektu.png)

The application consists of the following main components:

* **Backend (Java 21 / Spring Boot 3):** The central server responsible for managing data flow, transforming data into embeddings and graph nodes, directing system queries to the LLM, and coordinating with the array of external databases.
* **Frontend (React 19 / TypeScript / Vite):** The user interface for communicating with the server and AI chat, and for rendering visualizations of the input graph using the Neo4j NVL library.
* **Databases:** The solution uses a multi-purpose database stack:
  * **Qdrant** — an optimized vector database that searches for relations based on cosine distance, enabling semantic search.
  * **Neo4j** — a graph database storing narrative logic in a structure of nodes (artifacts) connected by multiple types of relationships.
  * **SQLite** — a relational database needed to store standard metadata, not directly related to vector-graph logic.
* **LLM Management (Langfuse):** The LLM models used by the application (OpenAI or local instances via Ollama) are connected to the **Langfuse** system, which logs their deductive *traces* and handles versioning of internal prompt instructions, thereby giving developers better oversight of the artificial intelligence's operation.

## 4. Setup and Configuration

### 1. Running External Services (Docker)

The application requires several services that can all be run using Docker.

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

# Run a chosen model (e.g., qwen3)
ollama run qwen3
```

### 2. Environment Configuration
Copy the template file to your target file, and then fill in your credentials:
```bash
cp .env-example .env
```
The `.env-example` file contains all necessary environment variables, such as:
- `NEO4J_URI`, `NEO4J_USER`, `NEO4J_PASSWORD`
- `QUADRANT_GRPC_PORT`, `QUADRANT_URL`
- `LANGFUSE_HOST`, `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY`
- `OLLAMA_HOST`
- `OPENAI_API_KEY`

### 3. Building and Running from the Shell

#### Backend
```bash
./gradlew build
./gradlew bootRun
```

#### Frontend
```bash
cd frontend
npm install
npm start
```
Your local client interface should now be available at: `http://localhost:3000`.
