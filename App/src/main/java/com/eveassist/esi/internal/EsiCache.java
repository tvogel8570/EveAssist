package com.eveassist.esi.internal;

import java.time.Instant;
import java.util.Optional;

/** What ESI last said about each route and parameter set. Public data, so there is no viewer. */
interface EsiCache {

    record Entry(String etag, Instant lastModified, Instant expires) {
    }

    Optional<Entry> find(String key);

    void save(String key, Entry entry, Instant fetchedAt);

    /** Forgets every entry whose key starts with the prefix, so that the next call for it is made in full. */
    void forgetPrefix(String keyPrefix);
}
