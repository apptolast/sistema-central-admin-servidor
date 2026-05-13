-- ============================================================================
-- V0__modulith_event_publication.sql
--
-- Tabla que Spring Modulith JPA usa para el registry de eventos asíncronos.
-- Modulith 1.4.x con `spring-modulith-events-jpa` mapea la entidad
-- JpaEventPublication a esta tabla. Como Hibernate corre con
-- `ddl-auto: validate`, Flyway tiene que crearla en el primer arranque.
--
-- Schema basado en el SQL official de Modulith (PostgreSQL):
-- https://docs.spring.io/spring-modulith/reference/events.html#publication-registry.jdbc
-- ============================================================================

CREATE TABLE IF NOT EXISTS event_publication (
    id                UUID                       NOT NULL,
    listener_id       VARCHAR(512)               NOT NULL,
    event_type        VARCHAR(512)               NOT NULL,
    serialized_event  TEXT                       NOT NULL,
    publication_date  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    completion_date   TIMESTAMP(6) WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS event_publication_by_completion_date
    ON event_publication (completion_date);

CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash
    ON event_publication (md5(serialized_event));

COMMENT ON TABLE event_publication IS
    'Spring Modulith JPA event registry. NO TOCAR — gestionado por Modulith.';
