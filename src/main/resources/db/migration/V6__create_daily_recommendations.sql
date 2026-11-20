CREATE TABLE daily_recommendations (
                                       trading_date DATE PRIMARY KEY,
                                       ranking_date DATE NOT NULL CHECK (ranking_date < trading_date),
                                       generated_at TIMESTAMPTZ NOT NULL,
                                       result JSONB NOT NULL
);