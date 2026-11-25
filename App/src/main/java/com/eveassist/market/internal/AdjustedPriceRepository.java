package com.eveassist.market.internal;

import com.eveassist.esi.MarketPrice;
import com.eveassist.market.AdjustedPriceAge;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** CCP's adjusted prices, shared by all users. Public data, so there is no viewer. */
@Repository
public class AdjustedPriceRepository {

    private final JdbcClient jdbc;
    private final JdbcTemplate batch;

    AdjustedPriceRepository(JdbcClient jdbc, JdbcTemplate batch) {
        this.jdbc = jdbc;
        this.batch = batch;
    }

    /**
     * Replaces every stored price with ESI's copy, in one transaction, so a reader sees the old prices or the new ones and never a
     * mixture. A type that ESI no longer lists goes from here too.
     */
    @Transactional
    public void replaceAll(List<MarketPrice> prices, Instant fetchedAt, Instant sourceModifiedAt) {
        jdbc.sql("DELETE FROM adjusted_price").update();
        batch.batchUpdate("INSERT INTO adjusted_price (type_id, adjusted_price, average_price) VALUES (?, ?, ?)",
                prices, 1000, (statement, price) -> {
                    statement.setInt(1, price.typeId());
                    statement.setBigDecimal(2, price.adjustedPrice());
                    statement.setBigDecimal(3, price.averagePrice());
                });
        confirm(fetchedAt, sourceModifiedAt);
    }

    /**
     * The stored prices are known to be ESI's current copy as of this time, whether or not they changed. ESI's own {@code
     * Last-Modified} replaces the one stored when there is one, and an answer that did not say keeps what was stored.
     */
    public void confirm(Instant at, Instant sourceModifiedAt) {
        jdbc.sql("""
                INSERT INTO adjusted_price_fetch (id, fetched_at, source_modified_at) VALUES (true, :at, :modified)
                ON CONFLICT (id) DO UPDATE SET fetched_at = excluded.fetched_at,
                    source_modified_at = coalesce(excluded.source_modified_at, adjusted_price_fetch.source_modified_at)
                """)
                .param("at", Timestamp.from(at))
                .param("modified", sourceModifiedAt == null ? null : Timestamp.from(sourceModifiedAt), java.sql.Types.TIMESTAMP)
                .update();
    }

    public Optional<AdjustedPriceAge> age() {
        return jdbc.sql("SELECT fetched_at, source_modified_at FROM adjusted_price_fetch")
                .query((rs, row) -> new AdjustedPriceAge(rs.getTimestamp("fetched_at").toInstant(),
                        rs.getTimestamp("source_modified_at") == null ? null : rs.getTimestamp("source_modified_at").toInstant()))
                .optional();
    }

    public boolean hasAny() {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM adjusted_price)").query(Boolean.class).single();
    }

    /** The adjusted prices of the items that have one. An item ESI sent no adjusted price for is left out, and one with 0 is not. */
    public Map<TypeId, BigDecimal> pricesOf(Collection<TypeId> types) {
        var prices = new HashMap<TypeId, BigDecimal>();
        if (!types.isEmpty()) {
            jdbc.sql("SELECT type_id, adjusted_price FROM adjusted_price WHERE adjusted_price IS NOT NULL AND type_id IN (:types)")
                    .param("types", types.stream().map(TypeId::value).toList())
                    .query((rs, row) -> prices.put(new TypeId(rs.getInt("type_id")), rs.getBigDecimal("adjusted_price")))
                    .list();
        }
        return prices;
    }
}
