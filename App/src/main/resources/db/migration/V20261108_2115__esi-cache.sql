-- What ESI last told us about each cacheable route and parameter set, so that nothing is fetched before it expires.
CREATE TABLE esi_cache (
    cache_key     text        PRIMARY KEY,
    etag          text,
    last_modified timestamptz,
    expires       timestamptz NOT NULL,
    fetched_at    timestamptz NOT NULL
);
