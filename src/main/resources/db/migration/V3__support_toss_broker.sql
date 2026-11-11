-- Keep the database constraint aligned with BrokerType (KIS, TOSS).
-- V1 created this column-level CHECK with PostgreSQL's generated name.
ALTER TABLE broker_connections
    DROP CONSTRAINT broker_connections_broker_check,
    ADD CONSTRAINT ck_broker_connections_broker
        CHECK (broker IN ('KIS', 'TOSS'));
