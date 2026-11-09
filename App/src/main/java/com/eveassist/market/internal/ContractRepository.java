package com.eveassist.market.internal;

import com.eveassist.esi.PublicContract;
import com.eveassist.esi.PublicContractItem;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** The public item exchange contracts of regions, shared by all users. Public data, so there is no viewer. */
@Repository
public class ContractRepository {

    /**
     * Contracts above this volume (m3) are not kept. A blueprint copy takes 0.01 m3, so a pack of ten thousand fits, and
     * the large contracts (ships, cargo) would cost thousands of ESI calls for lines that hold no copies.
     */
    static final double MAX_VOLUME = 100.0;

    private final JdbcClient jdbc;
    private final JdbcTemplate batch;

    ContractRepository(JdbcClient jdbc, JdbcTemplate batch) {
        this.jdbc = jdbc;
        this.batch = batch;
    }

    /**
     * Makes the stored contracts of the region match the list ESI gave: contracts that have gone from it are removed,
     * and new item exchange contracts with a price and a small volume are added, still waiting for their lines. Contracts already stored
     * are left alone, because a contract does not change.
     *
     * @return how many new contracts were added
     */
    @Transactional
    public int sync(int regionId, List<PublicContract> listed, Instant at) {
        var wanted = new HashMap<Long, PublicContract>();
        for (var contract : listed) {
            if (contract.isItemExchange() && contract.price().signum() > 0 && contract.volume() <= MAX_VOLUME
                    && contract.dateExpired().isAfter(at)) {
                wanted.put(contract.contractId(), contract);
            }
        }
        Set<Long> stored = new HashSet<>(jdbc.sql("SELECT contract_id FROM contract_public WHERE region_id = :region")
                .param("region", regionId).query(Long.class).list());
        var gone = stored.stream().filter(id -> !wanted.containsKey(id)).toList();
        batch.batchUpdate("DELETE FROM contract_public WHERE contract_id = ?", gone, 1000,
                (statement, id) -> statement.setLong(1, id));
        var added = wanted.values().stream().filter(contract -> !stored.contains(contract.contractId())).toList();
        batch.batchUpdate("""
                INSERT INTO contract_public (contract_id, region_id, price, issued_at, expires_at, start_location_id, volume)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (contract_id) DO NOTHING
                """, added, 1000, (statement, contract) -> {
            statement.setLong(1, contract.contractId());
            statement.setInt(2, regionId);
            statement.setBigDecimal(3, contract.price());
            statement.setTimestamp(4, Timestamp.from(contract.dateIssued()));
            statement.setTimestamp(5, Timestamp.from(contract.dateExpired()));
            if (contract.startLocationId() == null) {
                statement.setNull(6, java.sql.Types.BIGINT);
            } else {
                statement.setLong(6, contract.startLocationId());
            }
            statement.setDouble(7, contract.volume());
        });
        confirm(regionId, at);
        return added.size();
    }

    /** The region's contract list is known to be current as of this time, whether or not it changed. */
    public void confirm(int regionId, Instant at) {
        jdbc.sql("""
                INSERT INTO contract_public_fetch (region_id, fetched_at) VALUES (:region, :at)
                ON CONFLICT (region_id) DO UPDATE SET fetched_at = excluded.fetched_at
                """).param("region", regionId).param("at", Timestamp.from(at)).update();
    }

    public Optional<Instant> fetchedAt(int regionId) {
        return jdbc.sql("SELECT fetched_at FROM contract_public_fetch WHERE region_id = :region")
                .param("region", regionId).query((rs, row) -> rs.getTimestamp("fetched_at").toInstant())
                .optional();
    }

    /** The newest contracts that are still waiting for their lines. */
    public List<Long> pending(int regionId, int limit) {
        return jdbc.sql("""
                SELECT contract_id FROM contract_public
                WHERE region_id = :region AND NOT items_fetched
                ORDER BY issued_at DESC LIMIT :limit
                """).param("region", regionId).param("limit", limit).query(Long.class).list();
    }

    public int pendingCount(int regionId) {
        return jdbc.sql("SELECT count(*) FROM contract_public WHERE region_id = :region AND NOT items_fetched")
                .param("region", regionId).query(Integer.class).single();
    }

    @Transactional
    public void saveItems(long contractId, List<PublicContractItem> items, ContractFlags flags) {
        batch.batchUpdate("""
                INSERT INTO contract_public_item (contract_id, record_id, type_id, quantity, included, is_copy, me, te, runs)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (contract_id, record_id) DO NOTHING
                """, items, 1000, (statement, item) -> {
            statement.setLong(1, contractId);
            statement.setLong(2, item.recordId());
            statement.setInt(3, item.typeId());
            statement.setLong(4, item.quantity());
            statement.setBoolean(5, item.included());
            statement.setBoolean(6, item.blueprintCopy());
            setInt(statement, 7, item.materialEfficiency());
            setInt(statement, 8, item.timeEfficiency());
            setInt(statement, 9, item.runs());
        });
        jdbc.sql("UPDATE contract_public SET items_fetched = true, has_bpo = :bpo, has_abyssal = :abyssal WHERE contract_id = :id")
                .param("id", contractId).param("bpo", flags.bpo()).param("abyssal", flags.abyssal()).update();
    }

    /** How many stored contracts are set aside, as {@code [with a blueprint original, with an Abyssal item]}. */
    public int[] setAside(int regionId) {
        return jdbc.sql("""
                SELECT count(*) FILTER (WHERE has_bpo) AS bpo, count(*) FILTER (WHERE has_abyssal) AS abyssal
                FROM contract_public WHERE region_id = :region AND expires_at > now()
                """).param("region", regionId)
                .query((rs, row) -> new int[] {rs.getInt("bpo"), rs.getInt("abyssal")}).single();
    }

    /** Contracts in the region that hold at least one copy of the blueprint, with all their lines. */
    public List<StoredContract> contractsWithCopyOf(int regionId, int blueprintTypeId, Instant now) {
        var contracts = new HashMap<Long, BigDecimal>();
        var lines = new HashMap<Long, List<ContractLine>>();
        jdbc.sql("""
                SELECT c.contract_id, c.price, i.type_id, i.quantity, i.included, i.is_copy, i.me, i.te, i.runs
                FROM contract_public c
                JOIN contract_public_item i ON i.contract_id = c.contract_id
                WHERE c.region_id = :region AND c.items_fetched AND c.expires_at > :now
                  AND NOT c.has_bpo AND NOT c.has_abyssal
                  AND c.contract_id IN (SELECT contract_id FROM contract_public_item
                                        WHERE type_id = :type AND is_copy)
                """).param("region", regionId).param("type", blueprintTypeId).param("now", Timestamp.from(now))
                .query((rs, row) -> {
                    var id = rs.getLong("contract_id");
                    contracts.put(id, rs.getBigDecimal("price"));
                    var runs = rs.getInt("runs");
                    lines.computeIfAbsent(id, key -> new ArrayList<>()).add(new ContractLine(rs.getInt("type_id"),
                            rs.getLong("quantity"), rs.getBoolean("included"), rs.getBoolean("is_copy"),
                            rs.getInt("me"), rs.getInt("te"), rs.wasNull() ? null : runs));
                    return null;
                }).list();
        return contracts.entrySet().stream()
                .map(entry -> new StoredContract(entry.getKey(), entry.getValue(), lines.get(entry.getKey())))
                .toList();
    }

    private static void setInt(java.sql.PreparedStatement statement, int index, Integer value)
            throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }
}
