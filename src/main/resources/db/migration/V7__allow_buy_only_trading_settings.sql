ALTER TABLE trading_settings
    ALTER COLUMN take_profit_pct DROP NOT NULL,
    ALTER COLUMN stop_loss_pct DROP NOT NULL,
    ALTER COLUMN max_holding_minutes DROP NOT NULL;