package com.eveassist.characters.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Which corporation each linked character is in and whether it has the Director role there, as last read from ESI. It
 * is a fact about the character, so it lives with the character: removing the character removes it.
 */
@Repository
class MembershipRepository {

    /**
     * @param checkedAt            when the roles were last asked for
     * @param corporationCheckedAt when the corporation was last asked for
     */
    record Membership(UUID characterId, long eveCorporationId, boolean director, Instant checkedAt,
            Instant corporationCheckedAt) {
    }

    private final JdbcClient jdbc;

    MembershipRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Membership> find(UUID characterId) {
        return jdbc.sql("""
                SELECT character_id, eve_corporation_id, director, checked_at, corporation_checked_at
                FROM character_corporation WHERE character_id = :id
                """).param("id", characterId).query(MembershipRepository::membership).optional();
    }

    List<Membership> findAll(Collection<UUID> characterIds) {
        if (characterIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT character_id, eve_corporation_id, director, checked_at, corporation_checked_at
                FROM character_corporation WHERE character_id = ANY(:ids)
                """).param("ids", characterIds.toArray(UUID[]::new)).query(MembershipRepository::membership).list();
    }

    void save(UUID characterId, long eveCorporationId, boolean director, Instant now, Instant corporationAskedAt) {
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

    private static Membership membership(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Membership(rs.getObject("character_id", UUID.class), rs.getLong("eve_corporation_id"),
                rs.getBoolean("director"), rs.getTimestamp("checked_at").toInstant(),
                rs.getTimestamp("corporation_checked_at").toInstant());
    }
}
