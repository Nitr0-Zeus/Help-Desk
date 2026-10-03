CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('CLIENTE','AGENTE')),
    active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS invite_codes (
    code TEXT PRIMARY KEY,
    role TEXT NOT NULL CHECK (role IN ('CLIENTE','AGENTE')),
    expires_at TEXT NOT NULL,
    used_by INTEGER REFERENCES users(id),
    used_at TEXT
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TEXT NOT NULL,
    revoked INTEGER NOT NULL DEFAULT 0,
    replaced_by TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS sla_policies (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    priority TEXT NOT NULL UNIQUE CHECK (priority IN ('BAIXA','MEDIA','ALTA','CRITICA')),
    first_response_hours INTEGER NOT NULL,
    resolution_hours INTEGER NOT NULL,
    active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS tickets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    protocol TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    priority TEXT NOT NULL CHECK (priority IN ('BAIXA','MEDIA','ALTA','CRITICA')),
    status TEXT NOT NULL CHECK (status IN ('ABERTO','EM_ATENDIMENTO','AGUARDANDO_CLIENTE','RESOLVIDO','FECHADO')),
    created_by INTEGER NOT NULL REFERENCES users(id),
    assigned_to INTEGER REFERENCES users(id) ON DELETE SET NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    first_response_at TEXT,
    waiting_since TEXT,
    resolved_at TEXT,
    closed_at TEXT,
    first_response_due_at TEXT,
    due_at TEXT,
    sla_breached INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_tickets_status ON tickets(status);
CREATE INDEX IF NOT EXISTS idx_tickets_created_by ON tickets(created_by);
CREATE INDEX IF NOT EXISTS idx_tickets_assigned_to ON tickets(assigned_to);

CREATE TABLE IF NOT EXISTS timeline_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_id INTEGER NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    type TEXT NOT NULL CHECK (type IN ('COMMENT','EVENT')),
    event_type TEXT CHECK (event_type IN ('STATUS_CHANGED','ASSIGNED','SLA_PAUSED','SLA_RESUMED','SLA_BREACHED')),
    author_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
    body TEXT,
    payload TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_timeline_ticket_created ON timeline_entries(ticket_id, created_at);

CREATE TABLE IF NOT EXISTS attachments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timeline_entry_id INTEGER NOT NULL REFERENCES timeline_entries(id) ON DELETE CASCADE,
    original_filename TEXT NOT NULL,
    storage_name TEXT NOT NULL UNIQUE,
    content_type TEXT NOT NULL,
    size INTEGER NOT NULL,
    created_at TEXT NOT NULL
);
