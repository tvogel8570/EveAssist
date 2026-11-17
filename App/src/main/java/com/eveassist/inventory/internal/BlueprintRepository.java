package com.eveassist.inventory.internal;

import com.eveassist.esi.CharacterBlueprint;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Stores the latest blueprint data from ESI and fetch state independently of the holder's asset rows. */
@Repository
class BlueprintRepository {

    static final String KIND = "BLUEPRINTS";
    private final JdbcClient jdbc;

    BlueprintRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record FetchState(UUID holderId, Instant fetchedAt, Instant changedAt, Instant attemptedAt, String problem,
            Integer itemCount) {
    }

    @Transactional
    void replaceCharacter(UUID characterId, List<CharacterBlueprint> blueprints, Instant now) {
        jdbc.sql("DELETE FROM character_blueprint WHERE character_id = :id").param("id", characterId).update();
        for (var blueprint : blueprints) {
            jdbc.sql("""
                    INSERT INTO character_blueprint (character_id, item_id, type_id, location_id, location_flag,
                        quantity, material_efficiency, time_efficiency, runs)
                    VALUES (:holder, :item, :type, :location, :flag, :quantity, :me, :te, :runs)
                    """)
                    .param("holder", characterId).param("item", blueprint.itemId()).param("type", blueprint.typeId())
                    .param("location", blueprint.locationId()).param("flag", blueprint.locationFlag())
                    .param("quantity", blueprint.quantity()).param("me", blueprint.materialEfficiency())
                    .param("te", blueprint.timeEfficiency()).param("runs", blueprint.runs()).update();
        }
        saveState("character_fetch", "character_id", characterId, now, now, null, blueprints.size());
    }

    @Transactional
    void replaceCorporation(UUID corporationId, List<CharacterBlueprint> blueprints, Instant now) {
        jdbc.sql("DELETE FROM corporation_blueprint WHERE corporation_id = :id").param("id", corporationId).update();
        for (var blueprint : blueprints) {
            jdbc.sql("""
                    INSERT INTO corporation_blueprint (corporation_id, item_id, type_id, location_id, location_flag,
                        quantity, material_efficiency, time_efficiency, runs)
                    VALUES (:holder, :item, :type, :location, :flag, :quantity, :me, :te, :runs)
                    """)
                    .param("holder", corporationId).param("item", blueprint.itemId()).param("type", blueprint.typeId())
                    .param("location", blueprint.locationId()).param("flag", blueprint.locationFlag())
                    .param("quantity", blueprint.quantity()).param("me", blueprint.materialEfficiency())
                    .param("te", blueprint.timeEfficiency()).param("runs", blueprint.runs()).update();
        }
        saveState("corporation_fetch", "corporation_id", corporationId, now, now, null, blueprints.size());
    }

    boolean everStoredCharacter(UUID characterId) {
        return everStored("character_fetch", "character_id", characterId);
    }

    boolean everStoredCorporation(UUID corporationId) {
        return everStored("corporation_fetch", "corporation_id", corporationId);
    }

    void unchangedCharacter(UUID characterId, Instant now) {
        unchanged("character_fetch", "character_id", "character_blueprint", "character_id", characterId, now);
    }

    void unchangedCorporation(UUID corporationId, Instant now) {
        unchanged("corporation_fetch", "corporation_id", "corporation_blueprint", "corporation_id", corporationId, now);
    }

    void failedCharacter(UUID characterId, Instant now, String problem) {
        failed("character_fetch", "character_id", characterId, now, problem);
    }

    void failedCorporation(UUID corporationId, Instant now, String problem) {
        failed("corporation_fetch", "corporation_id", corporationId, now, problem);
    }

    List<FetchState> characterStates(Collection<UUID> holderIds) {
        return states("character_fetch", "character_id", holderIds);
    }

    List<FetchState> corporationStates(Collection<UUID> holderIds) {
        return states("corporation_fetch", "corporation_id", holderIds);
    }

    private boolean everStored(String table, String idColumn, UUID id) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM " + table + " WHERE " + idColumn
                        + " = :id AND kind = :kind AND changed_at IS NOT NULL)")
                .param("id", id).param("kind", KIND).query(Boolean.class).single();
    }

    private void unchanged(String fetchTable, String idColumn, String blueprintTable, String blueprintIdColumn,
            UUID id, Instant now) {
        var count = jdbc.sql("SELECT count(*) FROM " + blueprintTable + " WHERE " + blueprintIdColumn + " = :id")
                .param("id", id).query(Integer.class).single();
        var previous = states(fetchTable, idColumn, List.of(id)).stream().findFirst();
        saveState(fetchTable, idColumn, id, now, previous.map(FetchState::changedAt).orElse(null), null, count);
    }

    private void failed(String table, String idColumn, UUID id, Instant now, String problem) {
        jdbc.sql("""
                INSERT INTO %s (%s, kind, attempted_at, problem)
                VALUES (:id, :kind, :now, :problem)
                ON CONFLICT (%s, kind) DO UPDATE SET attempted_at = :now, problem = :problem
                """.formatted(table, idColumn, idColumn))
                .param("id", id).param("kind", KIND).param("now", Timestamp.from(now)).param("problem", problem)
                .update();
    }

    private List<FetchState> states(String table, String idColumn, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT %s AS holder_id, fetched_at, changed_at, attempted_at, problem, item_count FROM %s
                WHERE kind = :kind AND %s = ANY(:ids)
                """.formatted(idColumn, table, idColumn))
                .param("kind", KIND).param("ids", ids.toArray(UUID[]::new))
                .query((rs, row) -> new FetchState(rs.getObject("holder_id", UUID.class),
                        instant(rs.getTimestamp("fetched_at")), instant(rs.getTimestamp("changed_at")),
                        instant(rs.getTimestamp("attempted_at")), rs.getString("problem"),
                        (Integer) rs.getObject("item_count")))
                .list();
    }

    private void saveState(String table, String idColumn, UUID id, Instant fetchedAt, Instant changedAt, String problem,
            int count) {
        jdbc.sql("""
                INSERT INTO %s (%s, kind, fetched_at, changed_at, attempted_at, problem, item_count)
                VALUES (:id, :kind, :fetched, :changed, :fetched, :problem, :count)
                ON CONFLICT (%s, kind) DO UPDATE SET fetched_at = :fetched, changed_at = :changed,
                    attempted_at = :fetched, problem = :problem, item_count = :count
                """.formatted(table, idColumn, idColumn))
                .param("id", id).param("kind", KIND).param("fetched", Timestamp.from(fetchedAt))
                .param("changed", changedAt == null ? null : Timestamp.from(changedAt)).param("problem", problem)
                .param("count", count).update();
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}