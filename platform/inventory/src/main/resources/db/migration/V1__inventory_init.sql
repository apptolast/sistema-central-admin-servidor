-- ============================================================================
-- V1__inventory_init.sql
-- Bounded context: Inventory (Phase 1)
-- Convención: cada módulo prefija sus tablas con su nombre (inventory_*).
-- pgvector y otros tipos de Spring AI no se usan aquí; Phase 3 (knowledge) los añade.
-- ============================================================================

CREATE TABLE IF NOT EXISTS inventory_pod (
    id                  UUID         PRIMARY KEY,
    namespace           VARCHAR(128) NOT NULL,
    name                VARCHAR(253) NOT NULL,
    resource_version    VARCHAR(64)  NOT NULL,
    observed_generation BIGINT       NOT NULL,
    phase               VARCHAR(16)  NOT NULL,
    node_name           VARCHAR(253),
    pod_ip              VARCHAR(45),
    containers          JSONB        NOT NULL,
    owner_kind          VARCHAR(64),
    owner_name          VARCHAR(253),
    labels              JSONB        NOT NULL,
    annotations         JSONB        NOT NULL,
    observed_at         TIMESTAMPTZ  NOT NULL,
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT uq_pod_ns_name UNIQUE (namespace, name)
);

CREATE INDEX IF NOT EXISTS ix_pod_ns      ON inventory_pod(namespace);
CREATE INDEX IF NOT EXISTS ix_pod_phase   ON inventory_pod(phase);
CREATE INDEX IF NOT EXISTS ix_pod_deleted ON inventory_pod(deleted_at);

CREATE TABLE IF NOT EXISTS inventory_service (
    id                  UUID         PRIMARY KEY,
    namespace           VARCHAR(128) NOT NULL,
    name                VARCHAR(253) NOT NULL,
    resource_version    VARCHAR(64)  NOT NULL,
    observed_generation BIGINT       NOT NULL,
    type                VARCHAR(24)  NOT NULL,
    cluster_ip          VARCHAR(45),
    external_ip         VARCHAR(45),
    ports               JSONB        NOT NULL,
    selector            JSONB        NOT NULL,
    observed_at         TIMESTAMPTZ  NOT NULL,
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT uq_service_ns_name UNIQUE (namespace, name)
);

CREATE INDEX IF NOT EXISTS ix_service_ns   ON inventory_service(namespace);
CREATE INDEX IF NOT EXISTS ix_service_type ON inventory_service(type);

CREATE TABLE IF NOT EXISTS inventory_ingress (
    id                  UUID         PRIMARY KEY,
    namespace           VARCHAR(128) NOT NULL,
    name                VARCHAR(253) NOT NULL,
    kind                VARCHAR(48)  NOT NULL,
    resource_version    VARCHAR(64)  NOT NULL,
    observed_generation BIGINT       NOT NULL,
    hosts               JSONB        NOT NULL,
    tls_secret_name     VARCHAR(253),
    observed_at         TIMESTAMPTZ  NOT NULL,
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT uq_ingress_ns_name_kind UNIQUE (namespace, name, kind)
);

CREATE INDEX IF NOT EXISTS ix_ingress_ns ON inventory_ingress(namespace);

CREATE TABLE IF NOT EXISTS inventory_pvc (
    id                      UUID         PRIMARY KEY,
    namespace               VARCHAR(128) NOT NULL,
    name                    VARCHAR(253) NOT NULL,
    resource_version        VARCHAR(64)  NOT NULL,
    observed_generation     BIGINT       NOT NULL,
    phase                   VARCHAR(16)  NOT NULL,
    storage_class_name      VARCHAR(253),
    access_modes            JSONB        NOT NULL,
    requested_storage_bytes BIGINT       NOT NULL CHECK (requested_storage_bytes > 0),
    volume_name             VARCHAR(253),
    observed_at             TIMESTAMPTZ  NOT NULL,
    deleted_at              TIMESTAMPTZ,
    CONSTRAINT uq_pvc_ns_name UNIQUE (namespace, name)
);

CREATE INDEX IF NOT EXISTS ix_pvc_ns    ON inventory_pvc(namespace);
CREATE INDEX IF NOT EXISTS ix_pvc_phase ON inventory_pvc(phase);

CREATE TABLE IF NOT EXISTS inventory_certificate (
    id                  UUID         PRIMARY KEY,
    namespace           VARCHAR(128) NOT NULL,
    name                VARCHAR(253) NOT NULL,
    resource_version    VARCHAR(64)  NOT NULL,
    observed_generation BIGINT       NOT NULL,
    secret_name         VARCHAR(253) NOT NULL,
    dns_names           JSONB        NOT NULL,
    issuer              VARCHAR(253) NOT NULL,
    ready               BOOLEAN      NOT NULL,
    expires_at          TIMESTAMPTZ,
    observed_at         TIMESTAMPTZ  NOT NULL,
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT uq_cert_ns_name UNIQUE (namespace, name)
);

CREATE INDEX IF NOT EXISTS ix_cert_ns      ON inventory_certificate(namespace);
CREATE INDEX IF NOT EXISTS ix_cert_expires ON inventory_certificate(expires_at);
CREATE INDEX IF NOT EXISTS ix_cert_ready   ON inventory_certificate(ready);
