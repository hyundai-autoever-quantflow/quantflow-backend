CREATE TABLE stock_categories (
    code VARCHAR(40) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    depth SMALLINT NOT NULL CHECK (depth BETWEEN 0 AND 2),
    parent_code VARCHAR(40),
    listed_on DATE NOT NULL,
    symbols JSONB,
    symbols_checked_on DATE,
    CONSTRAINT ck_stock_categories_symbols CHECK (
        (symbols IS NULL AND symbols_checked_on IS NULL)
        OR (symbols IS NOT NULL AND symbols_checked_on IS NOT NULL AND jsonb_typeof(symbols) = 'array')
    )
);
