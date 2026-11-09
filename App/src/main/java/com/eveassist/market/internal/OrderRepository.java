package com.eveassist.market.internal;

import com.eveassist.esi.MarketOrder;
import com.eveassist.sde.TypeId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** The orders at Jita 4-4, shared by all users. Public data, so there is no viewer. */
@Repository
public class OrderRepository {

    private final JdbcClient jdbc;
    private final JdbcTemplate batch;

    OrderRepository(JdbcClient jdbc, JdbcTemplate batch) {
        this.jdbc = jdbc;
        this.batch = batch;
    }

    /** Replaces everything stored for the item, so that orders that have gone from the market go from here too. */
    @Transactional
    public void replace(TypeId type, List<MarketOrder> orders, Instant fetchedAt) {
        jdbc.sql("DELETE FROM jita_order WHERE type_id = :type").param("type", type.value()).update();
        batch.batchUpdate("INSERT INTO jita_order (order_id, type_id, is_buy, price, volume_remain) VALUES (?, ?, ?, ?, ?)",
                orders, 1000, (statement, order) -> {
                    statement.setLong(1, order.orderId());
                    statement.setInt(2, type.value());
                    statement.setBoolean(3, order.buy());
                    statement.setBigDecimal(4, order.price());
                    statement.setLong(5, order.volumeRemain());
                });
        confirm(type, fetchedAt);
    }

    /** The orders are known to be current as of this time, whether or not they changed. */
    public void confirm(TypeId type, Instant at) {
        jdbc.sql("""
                INSERT INTO jita_fetch (type_id, fetched_at) VALUES (:type, :at)
                ON CONFLICT (type_id) DO UPDATE SET fetched_at = excluded.fetched_at
                """).param("type", type.value()).param("at", Timestamp.from(at)).update();
    }

    public Map<TypeId, Instant> fetchedAt(Collection<TypeId> types) {
        var result = new HashMap<TypeId, Instant>();
        if (!types.isEmpty()) {
            jdbc.sql("SELECT type_id, fetched_at FROM jita_fetch WHERE type_id IN (:types)")
                    .param("types", types.stream().map(TypeId::value).toList())
                    .query((rs, row) -> result.put(new TypeId(rs.getInt("type_id")), rs.getTimestamp("fetched_at").toInstant()))
                    .list();
        }
        return result;
    }

    /** The stored orders of the items, as {@code (isBuy -> orders)} per item. */
    public Map<TypeId, Map<Boolean, List<PriceRule.BookOrder>>> ordersFor(Collection<TypeId> types) {
        var result = new HashMap<TypeId, Map<Boolean, List<PriceRule.BookOrder>>>();
        if (types.isEmpty()) {
            return result;
        }
        jdbc.sql("SELECT type_id, is_buy, price, volume_remain FROM jita_order WHERE type_id IN (:types)")
                .param("types", types.stream().map(TypeId::value).toList())
                .query((rs, row) -> {
                    result.computeIfAbsent(new TypeId(rs.getInt("type_id")), type -> new HashMap<>())
                            .computeIfAbsent(rs.getBoolean("is_buy"), side -> new ArrayList<>())
                            .add(new PriceRule.BookOrder(rs.getBigDecimal("price"), rs.getLong("volume_remain")));
                    return null;
                }).list();
        return result;
    }
}
