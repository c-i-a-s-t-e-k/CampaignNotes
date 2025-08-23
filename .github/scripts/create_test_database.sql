-- CampaignNotes Test Database Schema
-- Pełna struktura zgodna z SQLdb-plan.md

-- Włączenie foreign keys i optymalizacja
PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA cache_size = 10000;
PRAGMA temp_store = memory;

-- ========================================
-- TWORZENIE TABEL
-- ========================================

-- Tabela users
CREATE TABLE users (
    id TEXT PRIMARY KEY,
    email TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    is_active BOOLEAN DEFAULT 1,
    deleted_at INTEGER DEFAULT NULL,
    email_verified BOOLEAN DEFAULT 0,
    email_verification_token TEXT,
    email_verification_expires_at INTEGER,
    last_login_at INTEGER,
    is_admin BOOLEAN DEFAULT 0
);

-- Tabela campains (rozszerzona)
CREATE TABLE campains (
    uuid TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    neo4j_label TEXT NOT NULL,
    quadrant_collection_name TEXT NOT NULL,
    user_id TEXT NOT NULL,
    description TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    is_active BOOLEAN DEFAULT 1,
    deleted_at INTEGER DEFAULT NULL,
    settings TEXT, -- JSON
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Tabela artifact_categories
CREATE TABLE artifact_categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE NOT NULL,
    description TEXT NOT NULL,
    is_active BOOLEAN DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tabela artifact_categories_to_campaigns
CREATE TABLE artifact_categories_to_campaigns (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    campaign_uuid TEXT NOT NULL,
    category_name TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (campaign_uuid) REFERENCES campains(uuid) ON DELETE CASCADE,
    FOREIGN KEY (category_name) REFERENCES artifact_categories(name) ON DELETE CASCADE,
    UNIQUE(campaign_uuid, category_name)
);

-- Tabela campaign_notes
CREATE TABLE campaign_notes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    campaign_uuid TEXT NOT NULL,
    note_uuid TEXT UNIQUE NOT NULL,
    title TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    qdrant_sync_status TEXT DEFAULT 'pending',
    qdrant_sync_error TEXT,
    qdrant_last_sync_at INTEGER,
    neo4j_sync_status TEXT DEFAULT 'pending',
    neo4j_sync_error TEXT,
    neo4j_last_sync_at INTEGER,
    is_override BOOLEAN DEFAULT 0,
    word_count INTEGER,
    is_active BOOLEAN DEFAULT 1,
    deleted_at INTEGER DEFAULT NULL,
    FOREIGN KEY (campaign_uuid) REFERENCES campains(uuid) ON DELETE CASCADE,
    CHECK (qdrant_sync_status IN ('pending', 'syncing', 'synced', 'error', 'retry')),
    CHECK (neo4j_sync_status IN ('pending', 'syncing', 'synced', 'error', 'retry'))
);

-- Tabela artifact_notes
CREATE TABLE artifact_notes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    artifact_uuid TEXT NOT NULL,
    note_uuid TEXT NOT NULL,
    campaign_uuid TEXT NOT NULL,
    confidence_score REAL,
    created_at INTEGER NOT NULL,
    created_by_ai BOOLEAN DEFAULT 1,
    confirmed_by_user BOOLEAN DEFAULT 0,
    is_active BOOLEAN DEFAULT 1,
    deleted_at INTEGER DEFAULT NULL,
    FOREIGN KEY (campaign_uuid) REFERENCES campains(uuid) ON DELETE CASCADE,
    FOREIGN KEY (note_uuid) REFERENCES campaign_notes(note_uuid) ON DELETE CASCADE,
    UNIQUE(artifact_uuid, note_uuid)
);

-- Tabela note_overrides
CREATE TABLE note_overrides (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    past_time_note_uuid TEXT NOT NULL,
    override_note_uuid TEXT NOT NULL,
    campaign_uuid TEXT NOT NULL,
    override_reason TEXT,
    created_at INTEGER NOT NULL,
    is_active BOOLEAN DEFAULT 1,
    deleted_at INTEGER DEFAULT NULL,
    FOREIGN KEY (campaign_uuid) REFERENCES campains(uuid) ON DELETE CASCADE,
    FOREIGN KEY (past_time_note_uuid) REFERENCES campaign_notes(note_uuid),
    FOREIGN KEY (override_note_uuid) REFERENCES campaign_notes(note_uuid),
    UNIQUE(past_time_note_uuid, override_note_uuid)
);

-- Tabela campaign_terminology
CREATE TABLE campaign_terminology (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    campaign_uuid TEXT NOT NULL,
    term TEXT NOT NULL,
    explanation TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    created_by_user_id TEXT NOT NULL,
    is_active BOOLEAN DEFAULT 1,
    deleted_at INTEGER DEFAULT NULL,
    FOREIGN KEY (campaign_uuid) REFERENCES campains(uuid) ON DELETE CASCADE,
    FOREIGN KEY (created_by_user_id) REFERENCES users(id),
    UNIQUE(campaign_uuid, term)
);

-- Tabela password_reset_tokens
CREATE TABLE password_reset_tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    token TEXT UNIQUE NOT NULL,
    expires_at INTEGER NOT NULL,
    used_at INTEGER DEFAULT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Tabela scheduled_deletions
CREATE TABLE scheduled_deletions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    table_name TEXT NOT NULL,
    record_id TEXT NOT NULL,
    delete_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    processed BOOLEAN DEFAULT 0
);

-- ========================================
-- TWORZENIE INDEKSÓW
-- ========================================

-- Indeksy na klucze obce
CREATE INDEX idx_campains_user_id ON campains(user_id);
CREATE INDEX idx_campaign_notes_campaign_uuid ON campaign_notes(campaign_uuid);
CREATE INDEX idx_campaign_notes_note_uuid ON campaign_notes(note_uuid);
CREATE INDEX idx_artifact_notes_campaign_uuid ON artifact_notes(campaign_uuid);
CREATE INDEX idx_artifact_notes_note_uuid ON artifact_notes(note_uuid);
CREATE INDEX idx_artifact_notes_artifact_uuid ON artifact_notes(artifact_uuid);
CREATE INDEX idx_note_overrides_campaign_uuid ON note_overrides(campaign_uuid);
CREATE INDEX idx_terminology_campaign_uuid ON campaign_terminology(campaign_uuid);
CREATE INDEX idx_password_reset_user_id ON password_reset_tokens(user_id);

-- Indeksy na często wyszukiwane pola
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_campaign_notes_sync_status ON campaign_notes(qdrant_sync_status, neo4j_sync_status);
CREATE INDEX idx_artifact_notes_confirmed ON artifact_notes(confirmed_by_user);

-- Indeksy na soft delete
CREATE INDEX idx_users_active ON users(is_active, deleted_at);
CREATE INDEX idx_campains_active ON campains(is_active, deleted_at);
CREATE INDEX idx_campaign_notes_active ON campaign_notes(is_active, deleted_at);

-- ========================================
-- TWORZENIE TRIGGERÓW
-- ========================================

-- Triggery dla updated_at
CREATE TRIGGER users_updated_at 
    AFTER UPDATE ON users 
    BEGIN 
        UPDATE users SET updated_at = strftime('%s', 'now') WHERE id = NEW.id;
    END;

CREATE TRIGGER campains_updated_at 
    AFTER UPDATE ON campains 
    BEGIN 
        UPDATE campains SET updated_at = strftime('%s', 'now') WHERE uuid = NEW.uuid;
    END;

CREATE TRIGGER campaign_notes_updated_at 
    AFTER UPDATE ON campaign_notes 
    BEGIN 
        UPDATE campaign_notes SET updated_at = strftime('%s', 'now') WHERE note_uuid = NEW.note_uuid;
    END;

-- ========================================
-- DANE TESTOWE
-- ========================================

-- Domyślny użytkownik testowy
INSERT INTO users (id, email, password_hash, created_at, updated_at, is_active, email_verified, is_admin)
VALUES (
    'test-user-ci',
    'test@campaignnotes.local',
    'test-placeholder-hash',
    strftime('%s', 'now'),
    strftime('%s', 'now'),
    1,
    1,
    1
);

-- Przykładowe kategorie artefaktów
INSERT INTO artifact_categories (name, description) VALUES
    ('Character', 'Player characters, NPCs, and other personas'),
    ('Location', 'Cities, dungeons, regions, and other places'),
    ('Item', 'Weapons, armor, magical items, and equipment'),
    ('Event', 'Plot events, battles, and significant occurrences');

-- Przykładowa kampania testowa
INSERT INTO campains (uuid, name, neo4j_label, quadrant_collection_name, user_id, description, created_at, updated_at)
VALUES (
    'test-campaign-uuid',
    'Test Campaign',
    'TestCampaign',
    'test_collection',
    'test-user-ci',
    'Test campaign for CI/CD',
    strftime('%s', 'now'),
    strftime('%s', 'now')
);

-- Powiązanie kategorii z kampanią testową
INSERT INTO artifact_categories_to_campaigns (campaign_uuid, category_name) VALUES
    ('test-campaign-uuid', 'Character'),
    ('test-campaign-uuid', 'Location'),
    ('test-campaign-uuid', 'Item'),
    ('test-campaign-uuid', 'Event');

-- Optymalizacja na końcu
PRAGMA optimize;
