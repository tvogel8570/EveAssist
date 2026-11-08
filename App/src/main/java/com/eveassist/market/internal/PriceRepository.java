package com.eveassist.market.internal;

import com.eveassist.market.JitaPrice;
import com.eveassist.sde.TypeId;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PriceRepository {

    private final JdbcClient jdbc;
    private final JdbcTemplate batch;

    PriceRepository(JdbcClient jdbc, JdbcTemplate batch) {
        this.jdbc = jdbc;
        this.batch = batch;
    }

    public Map<TypeId, JitaPrice> pricesFor(Collection<TypeId> types) {
        if (types.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("SELECT type_id, sell_price, as_of FROM jita_price WHERE type_id IN (:types)")
                .param("types", types.stream().map(TypeId::value).toList())
                .query((rs, row) -> new JitaPrice(new TypeId(rs.getInt("type_id")), rs.getBigDecimal("sell_price"),
                        rs.getTimestamp("as_of").toInstant()))
                .list().stream()
                .collect(Collectors.toMap(JitaPrice::type, price -> price));
    }

    public void store(List<JitaPrice> prices) {
        batch.batchUpdate("""
                INSERT INTO jita_price (type_id, sell_price, as_of) VALUES (?, ?, ?)
                ON CONFLICT (type_id) DO UPDATE SET sell_price = excluded.sell_price, as_of = excluded.as_of
                """, prices, 1000, (statement, price) -> {
            statement.setInt(1, price.type().value());
            statement.setBigDecimal(2, price.sell());
            statement.setTimestamp(3, Timestamp.from(price.asOf()));
        });
    }
}
