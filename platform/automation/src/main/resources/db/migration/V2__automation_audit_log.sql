-- ============================================================================
-- V2__automation_audit_log.sql
-- Bounded context: Automation (Phase 5 — Wave-E E2).
--
-- Append-only audit log de TODO comando ejecutado vía SafeOpsKernel.
-- Cualquier intento — aceptado o rechazado, exitoso o fallido — deja una
-- entry aquí. Sin excepciones (regla de Wave-E: "el cluster no se toca sin
-- audit").
--
-- Tabla prefija el nombre del módulo (`automation_*`) por convención.
-- ============================================================================

CREATE TABLE IF NOT EXISTS automation_audit_log (
    id              UUID         PRIMARY KEY,
    executed_at     TIMESTAMPTZ  NOT NULL,
    command_kind    VARCHAR(64)  NOT NULL,
    command_payload JSONB        NOT NULL,
    executor_kind   VARCHAR(32)  NOT NULL,
    outcome         VARCHAR(16)  NOT NULL,
    exit_code       INTEGER,
    duration_ms     BIGINT,
    stdout_excerpt  TEXT,
    stderr_excerpt  TEXT,
    rejection_reason TEXT,
    user_id         VARCHAR(128),
    correlation_id  UUID
);

CREATE INDEX IF NOT EXISTS ix_audit_executed_at  ON automation_audit_log(executed_at DESC);
CREATE INDEX IF NOT EXISTS ix_audit_command_kind ON automation_audit_log(command_kind);
CREATE INDEX IF NOT EXISTS ix_audit_user_id      ON automation_audit_log(user_id);
CREATE INDEX IF NOT EXISTS ix_audit_outcome      ON automation_audit_log(outcome);

COMMENT ON TABLE  automation_audit_log               IS 'Append-only log de comandos SafeOps (Wave-E E2).';
COMMENT ON COLUMN automation_audit_log.outcome       IS 'ACCEPTED_OK | ACCEPTED_FAIL | REJECTED | TIMED_OUT';
COMMENT ON COLUMN automation_audit_log.stdout_excerpt IS 'Primeros ~2000 chars de stdout. Para detalle completo: usar id en endpoint detail.';
COMMENT ON COLUMN automation_audit_log.stderr_excerpt IS 'Primeros ~2000 chars de stderr.';
