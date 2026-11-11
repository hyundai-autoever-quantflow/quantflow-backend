-- Broker connections now represent live accounts only.
-- Do not reinterpret an existing paper account as a live account.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM broker_connections
        WHERE environment <> 'LIVE'
    ) THEN
        RAISE EXCEPTION 'Cannot remove broker environment: non-LIVE connections still exist. Disconnecting alone is insufficient; resolve their data before retrying migration.';
    END IF;
END;
$$;

ALTER TABLE broker_connections
    DROP COLUMN environment;
