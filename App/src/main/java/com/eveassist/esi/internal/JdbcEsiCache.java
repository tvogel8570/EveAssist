package com.eveassist.esi.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** What ESI last said about each route and parameter set. Public data, so there is no viewer. */
@Repository
class JdbcEsiCache implements EsiCache {

    private final JdbcClient jdbc;

    JdbcEsiCache(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Entry> find(String key) {
        return jdbc.sql("SELECT etag, last_modified, expires FROM esi_cache WHERE cache_key = :key")
                .param("key", key)
                .query((rs, row) -> new Entry(rs.getString("etag"),
                        rs.getTimestamp("last_modified") == null ? null : rs.getTimestamp("last_modified").toInstant(),
                        rs.getTimestamp("expires").toInstant()))
                .optional();
    }

    @Override
    public void forgetPrefix(String keyPrefix) {
        jdbc.sql("DELETE FROM esi_cache WHERE starts_with(cache_key, :prefix)").param("prefix", keyPrefix).update();
    }

    @Override
    public void save(String key, Entry entry, Instant fetchedAt) {
        jdbc.sql("""
                INSERT INTO esi_cache (cache_key, etag, last_modified, expires, fetched_at)
                VALUES (:key, :etag, :lastModified, :expires, :fetchedAt)
                ON CONFLICT (cache_key) DO UPDATE SET etag = excluded.etag, last_modified = excluded.last_modified,
                    expires = excluded.expires, fetched_at = excluded.fetched_at
                """)
                .param("key", key)
                .param("etag", entry.etag())
                .param("lastModified", entry.lastModified() == null ? null : Timestamp.from(entry.lastModified()))
                .param("expires", Timestamp.from(entry.expires()))
                .param("fetchedAt", Timestamp.from(fetchedAt))
                .update();
    }
}
