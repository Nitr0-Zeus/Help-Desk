INSERT OR IGNORE INTO sla_policies (priority, first_response_hours, resolution_hours, active) VALUES
    ('BAIXA', 8, 72, 1),
    ('MEDIA', 4, 48, 1),
    ('ALTA', 2, 24, 1),
    ('CRITICA', 1, 8, 1);

INSERT OR IGNORE INTO invite_codes (code, role, expires_at) VALUES
    ('AGENTE-2026', 'AGENTE', '2030-12-31 23:59:59.000');
