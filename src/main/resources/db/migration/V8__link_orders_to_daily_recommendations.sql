ALTER TABLE orders
    ALTER COLUMN analysis_id DROP NOT NULL,
    ADD COLUMN recommendation_trading_date DATE
        REFERENCES daily_recommendations (trading_date),
    ADD CONSTRAINT ck_orders_one_source CHECK (
        (analysis_id IS NOT NULL) <> (recommendation_trading_date IS NOT NULL)
    );

CREATE UNIQUE INDEX uk_orders_daily_auto_buy
    ON orders (connection_id, recommendation_trading_date)
    WHERE recommendation_trading_date IS NOT NULL
        AND side = 'BUY'
        AND trigger_type = 'ENTRY_RULE';