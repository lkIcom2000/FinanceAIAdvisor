-- Story 1.5: append-only Audit Records and evidence items (AD-13), and the non-owner application role.
-- Flyway runs as the owner; the application connects as fine_app. Its password is set in callback afterMigrate.sql.
-- persona_id is an id-only reference to the personas module (AD-12): no foreign key.

CREATE TABLE audit_records (
    seq             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id              UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    kind            VARCHAR(32)  NOT NULL
                    CHECK (kind IN ('RESPONSE', 'ACTION', 'NOTIFICATION', 'DOCUMENT_EXTRACTION')),
    run_id          VARCHAR(64),
    persona_id      VARCHAR(64)  NOT NULL,
    turn_id         UUID,
    action_id       UUID,
    fixture_version VARCHAR(64)  NOT NULL,
    schema_version  INTEGER      NOT NULL,
    payload         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (turn_id IS NULL OR action_id IS NULL)
);

CREATE INDEX audit_records_run_id ON audit_records (run_id, seq);

CREATE TABLE evidence_items (
    prov_id    VARCHAR(16)  PRIMARY KEY,
    persona_id VARCHAR(64)  NOT NULL,
    origin     VARCHAR(16)  NOT NULL CHECK (origin IN ('turn', 'overview', 'monitoring', 'simulate', 'document')),
    origin_id  VARCHAR(64),
    content    JSONB        NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE FUNCTION reject_audit_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit tables are append-only: % on % rejected', TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER audit_records_append_only BEFORE UPDATE OR DELETE ON audit_records
    FOR EACH ROW EXECUTE FUNCTION reject_audit_change();
CREATE TRIGGER audit_records_no_truncate BEFORE TRUNCATE ON audit_records
    FOR EACH STATEMENT EXECUTE FUNCTION reject_audit_change();
CREATE TRIGGER evidence_items_append_only BEFORE UPDATE OR DELETE ON evidence_items
    FOR EACH ROW EXECUTE FUNCTION reject_audit_change();
CREATE TRIGGER evidence_items_no_truncate BEFORE TRUNCATE ON evidence_items
    FOR EACH STATEMENT EXECUTE FUNCTION reject_audit_change();

-- Roles are cluster-wide: create the application role only once.
DO
$$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'fine_app') THEN
        CREATE ROLE fine_app LOGIN;
    END IF;
END;
$$;

GRANT USAGE ON SCHEMA public TO fine_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON personas, users, persona_state TO fine_app;
GRANT SELECT, INSERT ON audit_records, evidence_items TO fine_app;

-- Tables of later stories: full data access by default; audit-like tables revoke UPDATE and DELETE explicitly.
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO fine_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO fine_app;
