# Product Requirements Document (PRD) - CampaignNotes

## 1. Product Overview

CampaignNotes is a specialized application designed for tabletop RPG game masters to create and manage knowledge bases for their campaigns. The system helps track the complex web of characters, locations, items, events, and their relationships using a graph-based approach. The application leverages AI to automatically identify narrative artifacts from session notes and visualizes their connections, making it easier for story creators to maintain narrative consistency.

The application integrates a graph database (Neo4j) for storing relationships between artifacts and a vector database (Qdrant) for semantic search capabilities. This combination allows users to both visually navigate their campaign elements and perform semantic searches across their notes.

CampaignNotes serves as a single source of truth for game masters, addressing the challenges of tracking evolving narratives across multiple gaming sessions.

## 2. User Problem

Story creators for tabletop RPGs face significant challenges in managing complex narratives:

- Difficulty tracking ongoing events, relationships between characters, and maintaining narrative consistency across multiple sessions
- Lack of tools that organize complex worlds and storylines in a clear, navigable format
- Challenges in retrieving specific information from previous sessions when needed during gameplay
- Struggle to identify contradictions or inconsistencies in evolving narratives
- Difficulty visualizing the complex web of relationships between narrative elements
- Time-consuming manual organization of campaign notes without automated assistance
- Need to quickly reference or search through extensive campaign materials during live gameplay

The problem is particularly acute for campaigns that span months or years, involve numerous NPCs and locations, and feature complex, branching storylines.

## 3. Functional Requirements

### 3.1 User Management System
- FR-001: User registration and login using email/password authentication
- FR-002: Password change functionality 
- FR-003: Account deletion option
- FR-004: Basic administrator role with access to AI confidence metrics

### 3.2 Campaign Management
- FR-005: Creation of separate campaign environments
- FR-006: Campaign overview dashboard
- FR-007: Campaign settings and metadata management

### 3.3 Note Management
- FR-008: Note entry interface supporting up to 500 words per note
- FR-010: Versioning system through note overrides
- FR-011: Special terminology recording as explanation notes

### 3.4 Artifact Identification and Tracking
- FR-012: AI-powered identification of narrative artifacts (characters, locations, items, events)
- FR-013: User confirmation workflow for AI suggestions
- FR-014: Manual artifact creation and editing
- FR-015: Duplicate artifact detection and management

### 3.5 Relationship Management
- FR-016: Creation of relationships between artifacts
- FR-017: Custom relationship type definition
- FR-018: Relationship visualization with labeled connections

### 3.6 Graph Visualization
- FR-019: Graph-based visualization of story artifacts and relationships
- FR-020: Color coding and icons for different artifact types
- FR-021: Filtering and navigation tools for the graph view

### 3.7 Search and Query
- FR-022: Semantic search capabilities across campaign notes
- FR-023: Filtering by artifact type and relationship
- FR-024: Assistant interface for querying note contents

### 3.8 System Performance
- FR-025: Response time under 10 seconds for simple queries
- FR-026: Processing time under 1 minute for complex requests
- FR-027: AI artifact identification with minimum 75% accuracy

## 4. Product Constraints

### 4.1 Technical Constraints
- PC-001: Web-based application (no mobile version in MVP)
- PC-002: Integration limited to Neo4j for graph database and Qdrant for vector database
- PC-003: 500 words limit per note
- PC-004: No batch processing of notes in MVP
- PC-005: No cross-campaign artifacts in MVP

### 4.2 Feature Constraints
- PC-006: No AI-generated narrative content
- PC-007: No graphics or map editing capabilities
- PC-008: Limited text formatting options
- PC-009: No multi-user collaboration on single campaign
- PC-010: Minimalist user interface
- PC-011: No advanced account management (two-factor authentication, external auth integration)
- PC-012: No extensive campaign statistics or analytics
- PC-013: No hybrid search capabilities
- PC-014: No external system integrations beyond Neo4j and Qdrant

### 4.3 Performance Constraints
- PC-015: Processing time must be less than 1 minute
- PC-016: Query response time must be less than 10 seconds for simple queries

## 5. User Stories

### User Authentication

#### US-001: User Registration
- Description: As a new user, I want to register for an account so that I can create and manage my campaigns.
- Acceptance Criteria:
  1. User can enter email and password
  2. System validates email format and password strength
  3. System confirms successful registration
  4. User receives confirmation email
  5. User can access the application after registration

#### US-002: User Login
- Description: As a registered user, I want to log in to access my campaigns.
- Acceptance Criteria:
  1. User can enter email and password
  2. System validates credentials
  3. User gains access to their account upon successful authentication
  4. System provides error feedback for invalid credentials
  5. User can request password reset if forgotten

#### US-003: Password Change
- Description: As a user, I want to change my password to maintain account security.
- Acceptance Criteria:
  1. User can access password change functionality
  2. System requires current password verification
  3. System validates new password strength
  4. System confirms successful password change
  5. User can log in with new password

#### US-004: Account Deletion
- Description: As a user, I want to delete my account when I no longer need the service.
- Acceptance Criteria:
  1. User can access account deletion option
  2. System requires confirmation of deletion intent
  3. System permanently removes user data upon confirmation
  4. User receives confirmation of account deletion

### Campaign Management

#### US-005: Campaign Creation
- Description: As a user, I want to create a new campaign to organize my RPG session notes.
- Acceptance Criteria:
  1. User can enter campaign name and description
  2. System creates new empty campaign environment
  3. User is directed to the campaign dashboard
  4. Campaign appears in user's campaign list

#### US-006: Campaign Navigation
- Description: As a user, I want to navigate between my different campaigns.
- Acceptance Criteria:
  1. User can see list of all their campaigns
  2. User can select and open any campaign
  3. System loads the selected campaign data
  4. User can return to campaign list from within a campaign

#### US-007: Campaign Settings
- Description: As a user, I want to modify campaign settings to customize my experience.
- Acceptance Criteria:
  1. User can access campaign settings
  2. User can update campaign name and description
  3. User can set default view preferences
  4. System saves and applies setting changes

### Note Management

#### US-008: Note Entry
- Description: As a GM, I want to enter session notes to document campaign events.
- Acceptance Criteria:
  1. User can create new note with title and content
  2. System accepts up to 500 words per note
  3. User can save note to the campaign
  4. Notes are loaded to vector and graph databases
  5. Notes artifacts appear in campaign graph
  6. System prevents notes exceeding token limit

#### US-010: Note Override Creation
- Description: As a GM, I want to create note overrides to update campaign information.
- Acceptance Criteria:
  1. User can mark note as overriding previous information
  2. System identifies potential conflicts
  3. User can review and confirm changes
  4. System updates graph with new information
  5. System maintains history of previous versions

#### US-011: Special Terminology Recording
- Description: As a GM, I want to record special game terminology with explanations.
- Acceptance Criteria:
  1. User can mark terms as special terminology
  2. User can add explanations for terms
  3. Terms are highlighted in notes
  4. System creates special nodes for terminology in graph
  5. User can access terminology glossary

### Artifact and Relationship Management

#### US-012: Automated Artifact and Relationship Identification
- Description: As a GM, I want the system to automatically identify artifacts in my notes.
- Acceptance Criteria:
  1. System processes notes to identify potential artifacts and their relationships
  2. System categorizes artifacts by type (character, location, item, event)
  3. System connects artifacts with relationships with proposed labels
  4. System presents identified artifacts and relationships to user
  5. Identification accuracy exceeds 75%
  6. Processing completes in under 1 minute

#### US-013: Artifact and Relationship Confirmation
- Description: As a GM, I want to confirm or reject suggested artifacts and relationships before they're added to the graph.
- Acceptance Criteria:
  1. User can view all suggested artifacts and relationships
  2. User can confirm or reject each suggestion
  3. User can modify artifact details before confirmation
  4. User can modify relationship label
  5. System adds confirmed artifacts to the graph
  6. System connects with confirmed relationships existing artifacts
  7. System learns from user corrections

#### US-015: Duplicate Artifact Management
- Description: As a GM, I want to manage potential duplicate artifacts to maintain a clean graph.
- Acceptance Criteria:
  1. System identifies potential duplicate artifacts
  2. User can compare potential duplicates
  3. User can merge duplicates or keep separate
  4. System updates graph based on user decision
  5. Relationships transfer correctly during merges

### Graph Visualization

#### US-019: Artifact Graph View
- Description: As a GM, I want to view my campaign as a graph to visualize connections.
- Acceptance Criteria:
  1. System displays artifacts as nodes in graph
  2. System displays relationships as edges
  3. Different artifact types have distinct visual representations
  4. User can interact with graph elements

#### US-020: Graph Navigation
- Description: As a GM, I want to navigate the graph to explore different aspects of my campaign.
- Acceptance Criteria:
  1. User can zoom in/out of graph
  2. User can pan across graph
  3. User can center on specific artifacts
  4. Graph maintains performance during navigation

### Search and Query

#### US-022: Semantic Search
- Description: As a GM, I want to search my notes semantically to find relevant information.
- Acceptance Criteria:
  1. User can enter natural language queries
  2. System returns semantically relevant results
  3. Results link to source notes
  4. Results link to graph
  5. Search completes in under 10 seconds
  6. Results are ranked by relevance

#### US-024: Assistant Query
- Description: As a GM, I want to query an assistant about my campaign content.
- Acceptance Criteria:
  1. User can ask questions in natural language
  2. System responds with relevant information
  3. System cites sources from notes
  4. System can display relevant graph sections
  5. Responses complete in under 10 seconds for simple queries

### Administrator Functions

#### US-025: AI Performance Monitoring
- Description: As an admin, I want to monitor AI performance metrics.
- Acceptance Criteria:
  1. Admin can view artifact identification accuracy stats
  2. Admin can see processing time metrics
  3. Admin can view user correction patterns
  4. Admin can access error logs
  5. Stats update in real-time
  6. Ai tokens costs

## 6. Success Metrics

### 6.1 Technical Performance
- SM-001: AI artifact identification accuracy exceeds 75%
- SM-002: Processing time for note analysis under 1 minute for 500 words notes
- SM-003: Query response time under 10 seconds for simple queries
