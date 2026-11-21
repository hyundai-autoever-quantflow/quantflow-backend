package org.example.quantflowbackend.domain.stock.repository;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.broker.client.TossSectorClient.Sector;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StockCategoryRepository {
    private final JdbcClient jdbc;
    private final JdbcTemplate batches;
    private final JsonMapper json = new JsonMapper();

    public List<Category> findLatestCatalog() {
        return jdbc.sql("""
                SELECT code, name, depth, parent_code, listed_on
                FROM stock_categories
                WHERE listed_on = (SELECT max(listed_on) FROM stock_categories)
                ORDER BY depth, name, code
                """)
                .query(Category.class).list();
    }

    @Transactional
    public void saveCatalog(List<Sector> sectors, LocalDate checkedOn) {
        batches.batchUpdate("""
                INSERT INTO stock_categories (code, name, depth, parent_code, listed_on)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (code) DO UPDATE
                SET name = EXCLUDED.name, depth = EXCLUDED.depth, parent_code = EXCLUDED.parent_code,
                    listed_on = EXCLUDED.listed_on
                WHERE stock_categories.listed_on <= EXCLUDED.listed_on
                """, sectors.stream().map(sector -> new Object[]{
                sector.sectorId(), sector.name(), sector.depth(), sector.parentSectorId(), checkedOn
        }).toList());
    }

    public Optional<LocalDate> lastMembershipSync(String code) {
        return jdbc.sql("""
                SELECT symbols_checked_on FROM stock_categories
                WHERE code = :code AND symbols_checked_on IS NOT NULL
                """).param("code", code).query(LocalDate.class).optional();
    }

    @Transactional
    public void saveMembership(String code, List<String> symbols, LocalDate checkedOn) {
        jdbc.sql("""
                UPDATE stock_categories
                SET symbols = CAST(:symbols AS jsonb), symbols_checked_on = :checkedOn
                WHERE code = :code AND (symbols_checked_on IS NULL OR symbols_checked_on < :checkedOn)
                """).param("code", code).param("symbols", json.writeValueAsString(symbols))
                .param("checkedOn", checkedOn).update();
        applyMembership(code);
    }

    public void applyMembership(String code) {
        jdbc.sql("""
                UPDATE stocks s
                SET category_codes = CASE
                    WHEN jsonb_exists(c.symbols, s.symbol) THEN s.category_codes || jsonb_build_array(c.code)
                    ELSE s.category_codes - c.code
                END
                FROM stock_categories c
                WHERE c.code = :code AND c.symbols IS NOT NULL
                  AND s.market IN ('KOSPI','KOSDAQ') AND s.instrument_type = 'STOCK'
                  AND s.market_flags ->> 'listingProvider' = 'TOSS'
                  AND s.market_flags ->> 'isCommonShare' = 'true'
                  AND jsonb_exists(s.category_codes, c.code) <> jsonb_exists(c.symbols, s.symbol)
                """).param("code", code).update();
    }

    public record Category(String code, String name, int depth, String parentCode, LocalDate listedOn) {}
}
