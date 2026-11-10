package com.eveassist.inventory.internal;

import com.eveassist.esi.StructureLookup;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The structures where a character has items, with what ESI last said about each. */
@Repository
class StructureRepository {

    /**
     * Structure ids start above this. NPC stations are about 60,000,000 to 64,000,000, and solar systems about
     * 30,000,000 to 33,000,000.
     */
    static final long FIRST_STRUCTURE_ID = 1_000_000_000_000L;

    private final JdbcClient jdbc;

    StructureRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The structures where the character has top-level items and nothing current is known about, nearest to a first
     * answer first. A top-level item is one whose location is not another item of the same character: real ESI types
     * an item in a structure as {@code item}, so the type cannot be trusted (see esi.md).
     *
     * @param named          how long a found name is trusted
     * @param noAccessWait   how long to leave a structure the character cannot see before asking again
     * @param notFoundWait   how long to leave a structure ESI did not find
     */
    List<Long> toAsk(UUID characterId, Instant now, java.time.Duration named, java.time.Duration noAccessWait,
            java.time.Duration notFoundWait, int limit) {
        return jdbc.sql("""
                SELECT s.structure_id FROM (
                    SELECT DISTINCT a.location_id AS structure_id FROM character_asset a
                    WHERE a.character_id = :id AND a.location_id >= :first
                      AND NOT EXISTS (SELECT 1 FROM character_asset p
                                      WHERE p.character_id = a.character_id AND p.item_id = a.location_id)
                ) s
                LEFT JOIN character_structure c ON c.character_id = :id AND c.structure_id = s.structure_id
                WHERE (c.structure_id IS NULL
                   OR (c.status = 'FOUND' AND c.asked_at < :foundBefore)
                   OR (c.status = 'NO_ACCESS' AND c.asked_at < :noAccessBefore)
                   OR (c.status = 'NOT_FOUND' AND c.asked_at < :notFoundBefore))
                  -- a name that another character of the same account learned lately needs no call, and no 403
                  AND NOT EXISTS (SELECT 1 FROM character_structure k
                                  JOIN character kc ON kc.public_id = k.character_id
                                  WHERE kc.owner_account_id = (SELECT owner_account_id FROM character WHERE public_id = :id)
                                    AND k.structure_id = s.structure_id AND k.status = 'FOUND'
                                    AND k.asked_at >= :foundBefore)
                ORDER BY (c.structure_id IS NULL) DESC, s.structure_id
                LIMIT :limit
                """)
                .param("id", characterId).param("first", FIRST_STRUCTURE_ID)
                .param("foundBefore", Timestamp.from(now.minus(named)))
                .param("noAccessBefore", Timestamp.from(now.minus(noAccessWait)))
                .param("notFoundBefore", Timestamp.from(now.minus(notFoundWait)))
                .param("limit", limit)
                .query(Long.class).list();
    }

    /** Keeps what ESI said. A failure is not kept: it says nothing about the structure. */
    void save(UUID characterId, long structureId, StructureLookup answer, Instant now) {
        if (answer.status() == StructureLookup.Status.FAILED) {
            return;
        }
        var found = answer.status() == StructureLookup.Status.FOUND;
        jdbc.sql("""
                INSERT INTO character_structure (character_id, structure_id, status, name, solar_system_id, asked_at)
                VALUES (:id, :structure, :status, :name, :system, :now)
                ON CONFLICT (character_id, structure_id) DO UPDATE
                SET status = :status, name = :name, solar_system_id = :system, asked_at = :now
                """)
                .param("id", characterId).param("structure", structureId).param("status", answer.status().name())
                .param("name", found ? answer.name() : null).param("system", found ? answer.solarSystemId() : null)
                .param("now", Timestamp.from(now))
                .update();
    }

    /**
     * How many structures the character has items in, and how many of them have a name, whichever character of the
     * same account learned it.
     */
    Counts counts(UUID characterId) {
        return jdbc.sql("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE EXISTS (
                           SELECT 1 FROM character_structure k JOIN character kc ON kc.public_id = k.character_id
                           WHERE kc.owner_account_id = (SELECT owner_account_id FROM character WHERE public_id = :id)
                             AND k.structure_id = s.structure_id AND k.status = 'FOUND')) AS named
                FROM (
                    SELECT DISTINCT a.location_id AS structure_id FROM character_asset a
                    WHERE a.character_id = :id AND a.location_id >= :first
                      AND NOT EXISTS (SELECT 1 FROM character_asset p
                                      WHERE p.character_id = a.character_id AND p.item_id = a.location_id)
                ) s
                """)
                .param("id", characterId).param("first", FIRST_STRUCTURE_ID)
                .query((rs, row) -> new Counts(rs.getInt("total"), rs.getInt("named")))
                .single();
    }

    record Counts(int total, int named) {
    }
}
