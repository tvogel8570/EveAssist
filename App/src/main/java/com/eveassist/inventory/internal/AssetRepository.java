package com.eveassist.inventory.internal;

import com.eveassist.esi.CharacterAsset;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * A character's assets and the state of their fetch. The caller gives only ids of characters it got from the viewer's
 * own linked characters, so no method takes anyone else's.
 */
@Repository
class AssetRepository {

    static final String KIND = "ASSETS";
    private static final int BATCH = 1000;

    private final JdbcClient jdbc;
    private final JdbcTemplate template;

    AssetRepository(JdbcClient jdbc, JdbcTemplate template) {
        this.jdbc = jdbc;
        this.template = template;
    }

    /** One fetch as the page shows it. */
    record FetchState(UUID characterId, Instant fetchedAt, Instant changedAt, Instant attemptedAt, String problem,
            Integer itemCount) {
    }

    /**
     * Replaces the character's assets with what ESI gave, and records the fetch, in one transaction. The ESI call is
     * made before this, never inside it.
     */
    @Transactional
    void replace(UUID characterId, List<CharacterAsset> assets, Instant now) {
        jdbc.sql("DELETE FROM character_asset WHERE character_id = :id").param("id", characterId).update();
        var placed = AssetClassifier.classify(assets);
        for (int from = 0; from < placed.size(); from += BATCH) {
            var chunk = placed.subList(from, Math.min(from + BATCH, placed.size()));
            template.batchUpdate("""
                    INSERT INTO character_asset (character_id, item_id, type_id, quantity, location_id, location_type,
                        location_flag, is_singleton, blueprint_copy, root_location_id, depth, container_type_id, held)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement statement, int i) throws SQLException {
                    var item = chunk.get(i);
                    var asset = item.asset();
                    statement.setObject(1, characterId);
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
                }

                @Override
                public int getBatchSize() {
                    return chunk.size();
                }
            });
        }
        saveState(characterId, now, now, null, assets.size());
    }

    /** ESI said the stored copy is still current. */
    void recordUnchanged(UUID characterId, Instant now) {
        var count = jdbc.sql("SELECT count(*) FROM character_asset WHERE character_id = :id")
                .param("id", characterId).query(Integer.class).single();
        var previous = state(List.of(characterId)).stream().findFirst();
        saveState(characterId, now, previous.map(FetchState::changedAt).orElse(null), null, count);
    }

    /** The fetch did not work: the stored assets stay, with their age. */
    void recordFailure(UUID characterId, Instant now, String problem) {
        jdbc.sql("""
                INSERT INTO character_fetch (character_id, kind, attempted_at, problem)
                VALUES (:id, :kind, :now, :problem)
                ON CONFLICT (character_id, kind) DO UPDATE SET attempted_at = :now, problem = :problem
                """)
                .param("id", characterId).param("kind", KIND).param("now", timestamp(now)).param("problem", problem)
                .update();
    }

    List<FetchState> state(Collection<UUID> characterIds) {
        if (characterIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT character_id, fetched_at, changed_at, attempted_at, problem, item_count FROM character_fetch
                WHERE kind = :kind AND character_id = ANY(:ids)
                """)
                .param("kind", KIND).param("ids", characterIds.toArray(UUID[]::new))
                .query((rs, row) -> new FetchState(rs.getObject("character_id", UUID.class),
                        instant(rs.getTimestamp("fetched_at")), instant(rs.getTimestamp("changed_at")),
                        instant(rs.getTimestamp("attempted_at")), rs.getString("problem"),
                        (Integer) rs.getObject("item_count")))
                .list();
    }

    private void saveState(UUID characterId, Instant fetchedAt, Instant changedAt, String problem, int count) {
        jdbc.sql("""
                INSERT INTO character_fetch (character_id, kind, fetched_at, changed_at, attempted_at, problem,
                    item_count)
                VALUES (:id, :kind, :fetched, :changed, :fetched, :problem, :count)
                ON CONFLICT (character_id, kind) DO UPDATE SET fetched_at = :fetched, changed_at = :changed,
                    attempted_at = :fetched, problem = :problem, item_count = :count
                """)
                .param("id", characterId).param("kind", KIND).param("fetched", timestamp(fetchedAt))
                .param("changed", timestamp(changedAt)).param("problem", problem).param("count", count)
                .update();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
