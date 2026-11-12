package com.eveassist.esi.internal;

import java.time.Instant;
import java.util.Optional;

/** What ESI last said about each route and parameter set. Public data, so there is no viewer. */
interface EsiCache {

    record Entry(String etag, Instant lastModified, Instant expires) {
    }

    Optional<Entry> find(String key);

    void save(String key, Entry entry, Instant fetchedAt);
}
