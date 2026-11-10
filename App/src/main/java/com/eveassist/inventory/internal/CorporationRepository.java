package com.eveassist.inventory.internal;

import com.eveassist.esi.CharacterAsset;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The corporations of an account's own characters, what is known about who may read them, their assets and the state of
 * the fetch. A corporation belongs to one account, and every method takes that account's id or ids of its own
 * corporations or characters, so none reaches another account's data.
 */
@Repository
class CorporationRepository {

    static final String KIND = "ASSETS";
    private static final int BATCH = 1000;

    private final JdbcClient jdbc;
    private final JdbcTemplate template;

    CorporationRepository(JdbcClient jdbc, JdbcTemplate template) {
        this.jdbc = jdbc;
        this.template = template;
    }

    /** A corporation of the account, as the page shows it. */
    record Corporation(UUID id, long eveCorporationId, String name, Instant namedAt, Instant divisionsAt) {
    }

    /** What is known about a character's corporation: which it is, and whether the character is a Director there. */
    record Membership(UUID characterId, long eveCorporationId, boolean director, Instant checkedAt,
            Instant corporationCheckedAt) {
    }

    /** One fetch of a corporation's assets, as the page shows it. */
    record FetchState(UUID corporationId, Instant fetchedAt, Instant changedAt, Instant attemptedAt, String problem,
            Integer itemCount) {
    }

    // ---- who belongs to which corporation

    Optional<Membership> membership(UUID characterId) {
        return jdbc.sql("""
                SELECT character_id, eve_corporation_id, director, checked_at, corporation_checked_at
                FROM character_corporation
                WHERE character_id = :id
                """).param("id", characterId)
                .query((rs, row) -> new Membership(rs.getObject("character_id", UUID.class),
                        rs.getLong("eve_corporation_id"), rs.getBoolean("director"),
                        rs.getTimestamp("checked_at").toInstant(),
                        rs.getTimestamp("corporation_checked_at").toInstant()))
                .optional();
    }

    List<Membership> memberships(Collection<UUID> characterIds) {
        if (characterIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT character_id, eve_corporation_id, director, checked_at, corporation_checked_at
                FROM character_corporation
                WHERE character_id = ANY(:ids)
                """).param("ids", characterIds.toArray(UUID[]::new))
                .query((rs, row) -> new Membership(rs.getObject("character_id", UUID.class),
                        rs.getLong("eve_corporation_id"), rs.getBoolean("director"),
                        rs.getTimestamp("checked_at").toInstant(),
                        rs.getTimestamp("corporation_checked_at").toInstant()))
                .list();
    }

    /** @param corporationAskedAt when ESI was last asked which corporation the character is in */
    void saveMembership(UUID characterId, long eveCorporationId, boolean director, Instant now,
            Instant corporationAskedAt) {
        jdbc.sql("""
                INSERT INTO character_corporation (character_id, eve_corporation_id, director, checked_at,
                    corporation_checked_at)
                VALUES (:id, :corporation, :director, :now, :corporationAt)
                ON CONFLICT (character_id) DO UPDATE
                SET eve_corporation_id = :corporation, director = :director, checked_at = :now,
                    corporation_checked_at = :corporationAt
                """)
                .param("id", characterId).param("corporation", eveCorporationId).param("director", director)
                .param("now", Timestamp.from(now)).param("corporationAt", Timestamp.from(corporationAskedAt))
                .update();
    }

    // ---- the corporations

    List<Corporation> listFor(UUID accountId) {
        return jdbc.sql("""
                SELECT public_id, eve_corporation_id, name, named_at, divisions_at FROM corporation
                WHERE owner_account_id = :account ORDER BY name, eve_corporation_id
                """).param("account", accountId)
                .query((rs, row) -> new Corporation(rs.getObject("public_id", UUID.class),
                        rs.getLong("eve_corporation_id"), rs.getString("name"),
                        rs.getTimestamp("named_at").toInstant(), instant(rs.getTimestamp("divisions_at"))))
                .list();
    }

    /** Creates the corporation for the account, or renames it. Returns its id. */
    UUID saveCorporation(UUID accountId, long eveCorporationId, String name, Instant now) {
        return jdbc.sql("""
                INSERT INTO corporation (public_id, owner_account_id, eve_corporation_id, name, named_at)
                VALUES (:id, :account, :corporation, :name, :now)
                ON CONFLICT (owner_account_id, eve_corporation_id) DO UPDATE SET name = :name, named_at = :now
                RETURNING public_id
                """)
                .param("id", UUID.randomUUID()).param("account", accountId).param("corporation", eveCorporationId)
                .param("name", name).param("now", Timestamp.from(now))
                .query(UUID.class).single();
    }

    /** The corporation of the account with this EVE id, whether or not a name is known yet. */
    Optional<Corporation> find(UUID accountId, long eveCorporationId) {
        return listFor(accountId).stream().filter(c -> c.eveCorporationId() == eveCorporationId).findFirst();
    }

    /** Removes the account's corporations that none of its characters belong to any more, with their data. */
    void dropAbandoned(UUID accountId, Collection<Long> keptEveCorporationIds) {
        jdbc.sql("""
                DELETE FROM corporation WHERE owner_account_id = :account
                  AND NOT (eve_corporation_id = ANY(:kept))
                """)
                .param("account", accountId).param("kept", keptEveCorporationIds.toArray(Long[]::new)).update();
    }

    // ---- hangar division names

    @Transactional
    void replaceDivisions(UUID corporationId, Map<Integer, String> names, Instant now) {
        jdbc.sql("DELETE FROM corporation_division WHERE corporation_id = :id").param("id", corporationId).update();
        names.forEach((division, name) -> {
            if (division >= 1 && division <= 7) {
                jdbc.sql("INSERT INTO corporation_division (corporation_id, division, name) "
                                + "VALUES (:id, :division, :name)")
                        .param("id", corporationId).param("division", division).param("name", name).update();
            }
        });
        jdbc.sql("UPDATE corporation SET divisions_at = :now WHERE public_id = :id")
                .param("now", Timestamp.from(now)).param("id", corporationId).update();
    }

    /** A failed ask for the names: leave the old ones, and do not ask again before the usual time is over. */
    void touchDivisions(UUID corporationId, Instant now) {
        jdbc.sql("UPDATE corporation SET divisions_at = :now WHERE public_id = :id")
                .param("now", Timestamp.from(now)).param("id", corporationId).update();
    }

    /** The names of the hangar divisions, for each of the corporations that gave any. */
    Map<UUID, Map<Integer, String>> divisionNames(Collection<UUID> corporationIds) {
        var result = new java.util.HashMap<UUID, Map<Integer, String>>();
        if (corporationIds.isEmpty()) {
            return result;
        }
        jdbc.sql("""
                SELECT corporation_id, division, name FROM corporation_division WHERE corporation_id = ANY(:ids)
                """).param("ids", corporationIds.toArray(UUID[]::new))
                .query((rs, row) -> {
                    result.computeIfAbsent(rs.getObject("corporation_id", UUID.class), k -> new java.util.TreeMap<>())
                            .put(rs.getInt("division"), rs.getString("name"));
                    return null;
                }).list();
        return result;
    }

    // ---- assets

    /**
     * Replaces the corporation's assets with what ESI gave, and records the fetch, in one transaction. The ESI call
     * is made before this, never inside it.
     */
    @Transactional
    void replace(UUID corporationId, List<CharacterAsset> assets, Instant now) {
        jdbc.sql("DELETE FROM corporation_asset WHERE corporation_id = :id").param("id", corporationId).update();
        var placed = AssetClassifier.classifyCorporation(assets);
        for (int from = 0; from < placed.size(); from += BATCH) {
            var chunk = placed.subList(from, Math.min(from + BATCH, placed.size()));
            template.batchUpdate("""
                    INSERT INTO corporation_asset (corporation_id, item_id, type_id, quantity, location_id,
                        location_type, location_flag, is_singleton, blueprint_copy, root_location_id, depth,
                        container_type_id, held, division)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement statement, int i) throws SQLException {
                    var item = chunk.get(i);
                    var asset = item.asset();
                    statement.setObject(1, corporationId);
                    statement.setLong(2, asset.itemId());
                    statement.setInt(3, asset.typeId());
                    statement.setLong(4, asset.quantity());
                    statement.setLong(5, asset.locationId());
                    statement.setString(6, asset.locationType());
                    statement.setString(7, asset.locationFlag());
                    statement.setBoolean(8, asset.singleton());
                    if (asset.blueprintCopy() == null) {
                        statement.setNull(9, Types.BOOLEAN);
                    } else {
                        statement.setBoolean(9, asset.blueprintCopy());
                    }
                    statement.setLong(10, item.rootLocationId());
                    statement.setInt(11, item.depth());
                    if (item.containerTypeId() == null) {
                        statement.setNull(12, Types.INTEGER);
                    } else {
                        statement.setInt(12, item.containerTypeId());
                    }
                    statement.setString(13, item.held().name());
                    if (item.division() == null) {
                        statement.setNull(14, Types.SMALLINT);
                    } else {
                        statement.setInt(14, item.division());
                    }
                }

                @Override
                public int getBatchSize() {
                    return chunk.size();
                }
            });
        }
        saveState(corporationId, now, now, null, placed.size());
    }

    /** ESI said the stored copy is still current. */
    void recordUnchanged(UUID corporationId, Instant now) {
        var count = jdbc.sql("SELECT count(*) FROM corporation_asset WHERE corporation_id = :id")
                .param("id", corporationId).query(Integer.class).single();
        var previous = state(List.of(corporationId)).stream().findFirst();
        saveState(corporationId, now, previous.map(FetchState::changedAt).orElse(null), null, count);
    }

    /** The fetch did not work: the stored assets stay, with their age. */
    void recordFailure(UUID corporationId, Instant now, String problem) {
        jdbc.sql("""
                INSERT INTO corporation_fetch (corporation_id, kind, attempted_at, problem)
                VALUES (:id, :kind, :now, :problem)
                ON CONFLICT (corporation_id, kind) DO UPDATE SET attempted_at = :now, problem = :problem
                """)
                .param("id", corporationId).param("kind", KIND).param("now", Timestamp.from(now))
                .param("problem", problem).update();
    }

    List<FetchState> state(Collection<UUID> corporationIds) {
        if (corporationIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT corporation_id, fetched_at, changed_at, attempted_at, problem, item_count
                FROM corporation_fetch WHERE kind = :kind AND corporation_id = ANY(:ids)
                """)
                .param("kind", KIND).param("ids", corporationIds.toArray(UUID[]::new))
                .query((rs, row) -> new FetchState(rs.getObject("corporation_id", UUID.class),
                        instant(rs.getTimestamp("fetched_at")), instant(rs.getTimestamp("changed_at")),
                        instant(rs.getTimestamp("attempted_at")), rs.getString("problem"),
                        (Integer) rs.getObject("item_count")))
                .list();
    }

    private void saveState(UUID corporationId, Instant fetchedAt, Instant changedAt, String problem, int count) {
        jdbc.sql("""
                INSERT INTO corporation_fetch (corporation_id, kind, fetched_at, changed_at, attempted_at, problem,
                    item_count)
                VALUES (:id, :kind, :fetched, :changed, :fetched, :problem, :count)
                ON CONFLICT (corporation_id, kind) DO UPDATE SET fetched_at = :fetched, changed_at = :changed,
                    attempted_at = :fetched, problem = :problem, item_count = :count
                """)
                .param("id", corporationId).param("kind", KIND).param("fetched", Timestamp.from(fetchedAt))
                .param("changed", changedAt == null ? null : Timestamp.from(changedAt)).param("problem", problem)
                .param("count", count).update();
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
