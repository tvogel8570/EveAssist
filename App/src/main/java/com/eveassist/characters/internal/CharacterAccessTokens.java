package com.eveassist.characters.internal;

import com.eveassist.esi.AccessTokens;
import com.eveassist.esi.TokenGrant;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gives the ESI client an access token for a linked character (see identity-and-tokens.md, Refreshing access tokens).
 * An access token is refreshed when less than a minute of it is left, not after a request fails. Only one refresh runs
 * at a time for each character: a lock in this instance, then a Postgres advisory lock that also works across
 * instances, held for the one call to EVE SSO, which times out. The newest refresh token is the one stored. When EVE
 * refuses it, or the character was sold, the character is marked as needing re-authorization and nothing is retried.
 * Access tokens live in memory only. No token is ever logged.
 */
@Component
class CharacterAccessTokens implements AccessTokens {

    private static final Logger log = LoggerFactory.getLogger(CharacterAccessTokens.class);
    /** Refresh this long before an access token expires. */
    private static final Duration REFRESH_BEFORE_EXPIRY = Duration.ofSeconds(60);
    /** Keeps this application's advisory locks apart from any other use of the database's. */
    private static final long LOCK_NAMESPACE = 0x4556454153530000L;

    private record Cached(String token, Instant expiresAt, Set<String> scopes) {
        @Override
        public String toString() {
            return "Cached[token hidden]";
        }
    }

    private final CharacterRepository characters;
    private final EveSso sso;
    private final TokenCipher cipher;
    private final DataSource dataSource;
    private final Clock clock;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();
    private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
    /**
     * A newer refresh token that EVE gave and that could not be saved yet. EVE may rotate refresh tokens at any time
     * without notice (its old documentation says the returned token "may not be the same"), and then the token we have
     * saved may already be dead, so the new one must not be lost. It is held here, used for the next refresh, and
     * saved again at the next call. Never logged.
     */
    private final Map<Long, String> unsavedRefreshTokens = new ConcurrentHashMap<>();
    /** Characters for which a rotated refresh token was already seen, so that it is reported once. */
    private final Set<Long> rotationSeen = ConcurrentHashMap.newKeySet();
    private final Counter rotated;
    /**
     * A refresh holds one pooled connection for its lock and needs another for its own queries. If every connection
     * were held for a lock, each refresh would wait for a second one that never comes, until the pool timed out. So
     * at most this many refreshes run at once. It must stay at or below half the pool (see
     * documentation/runbooks/concurrency-tuning.md).
     */
    static final int MOST_CONCURRENT_REFRESHES = 2;
    private final Semaphore refreshes = new Semaphore(MOST_CONCURRENT_REFRESHES);

    CharacterAccessTokens(CharacterRepository characters, EveSso sso, TokenCipher cipher, DataSource dataSource,
            Clock clock, MeterRegistry meters) {
        this.rotated = Counter.builder("eveassist.sso.refresh.rotated")
                .description("Refreshes where EVE returned a different refresh token than the one sent: rotation")
                .register(meters);
        this.characters = characters;
        this.sso = sso;
        this.cipher = cipher;
        this.dataSource = dataSource;
        this.clock = clock;
    }

    @Override
    public TokenGrant accessToken(long eveCharacterId, String requiredScope) {
        var standing = characters.findStanding(eveCharacterId);
        if (standing.isEmpty() || standing.get().needsReauthorization()) {
            return TokenGrant.unavailable(TokenGrant.Problem.NEEDS_REAUTHORIZATION);
        }
        // the scope is checked before any token is fetched, so a call that would be a 403 is never made
        if (!standing.get().scopes().contains(requiredScope)) {
            return TokenGrant.unavailable(TokenGrant.Problem.MISSING_SCOPE);
        }
        if (unsavedRefreshTokens.containsKey(eveCharacterId)) {
            savePending(eveCharacterId);
        }
        var cached = usable(eveCharacterId);
        if (cached != null) {
            return TokenGrant.of(cached.token());
        }
        var lock = locks.computeIfAbsent(eveCharacterId, id -> new ReentrantLock());
        lock.lock();
        try {
            refreshes.acquireUninterruptibly();
            try {
                return refreshLocked(eveCharacterId, requiredScope);
            } finally {
                refreshes.release();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void invalidate(long eveCharacterId) {
        cache.remove(eveCharacterId);
    }

    /** Forgets the access token held for a character, because its saved access was removed, dropped or replaced. */
    void forget(long eveCharacterId) {
        cache.remove(eveCharacterId);
        unsavedRefreshTokens.remove(eveCharacterId);
        rotationSeen.remove(eveCharacterId);
    }

    /** Saves a refresh token that EVE gave and that could not be saved before. Tried again at the next call if it fails. */
    private void savePending(long eveCharacterId) {
        var lock = locks.computeIfAbsent(eveCharacterId, id -> new ReentrantLock());
        lock.lock();
        try {
            var pending = unsavedRefreshTokens.get(eveCharacterId);
            if (pending == null) {
                return;
            }
            try {
                characters.replaceToken(eveCharacterId, cipher.encrypt(eveCharacterId, pending));
                unsavedRefreshTokens.remove(eveCharacterId, pending);
            } catch (RuntimeException e) {
                log.error("Still could not save a newer refresh token for character {}: {}", eveCharacterId,
                        e.getClass().getSimpleName());
            }
        } finally {
            lock.unlock();
        }
    }

    private Cached usable(long eveCharacterId) {
        var cached = cache.get(eveCharacterId);
        return cached != null && cached.expiresAt().minus(REFRESH_BEFORE_EXPIRY).isAfter(clock.instant()) ? cached : null;
    }

    private TokenGrant refreshLocked(long eveCharacterId, String requiredScope) {
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_lock(?)")) {
                statement.setLong(1, LOCK_NAMESPACE ^ eveCharacterId);
                statement.execute();
            }
            try {
                return refresh(eveCharacterId, requiredScope);
            } finally {
                try (var statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                    statement.setLong(1, LOCK_NAMESPACE ^ eveCharacterId);
                    statement.execute();
                }
            }
        } catch (SQLException e) {
            log.warn("Could not lock the token refresh for character {}", eveCharacterId);
            return TokenGrant.unavailable(TokenGrant.Problem.UNAVAILABLE);
        }
    }

    private TokenGrant refresh(long eveCharacterId, String requiredScope) {
        // another thread or instance may have refreshed while this one waited for the lock
        var cached = usable(eveCharacterId);
        if (cached != null) {
            return TokenGrant.of(cached.token());
        }
        var stored = characters.findToken(eveCharacterId);
        var standing = characters.findStanding(eveCharacterId);
        if (stored.isEmpty() || standing.isEmpty()) {
            return TokenGrant.unavailable(TokenGrant.Problem.NEEDS_REAUTHORIZATION);
        }
        EveSso.RefreshedAccess refreshed;
        String saved;
        String current;
        try {
            saved = cipher.decrypt(eveCharacterId, stored.get());
            // a newer token that could not be saved is the one EVE honours, so it is the one to send
            var unsaved = unsavedRefreshTokens.get(eveCharacterId);
            current = unsaved != null ? unsaved : saved;
            refreshed = sso.refresh(current);
        } catch (EveSso.InvalidGrantException e) {
            log.warn("EVE refused the saved access of character {}", eveCharacterId);
            characters.markNeedsReauthorization(eveCharacterId);
            cache.remove(eveCharacterId);
            unsavedRefreshTokens.remove(eveCharacterId);
            return TokenGrant.unavailable(TokenGrant.Problem.NEEDS_REAUTHORIZATION);
        } catch (EveSso.SsoException | IllegalStateException e) {
            log.warn("Could not refresh the access token of character {}: {}", eveCharacterId, e.getMessage());
            return TokenGrant.unavailable(TokenGrant.Problem.UNAVAILABLE);
        }
        var identity = refreshed.identity();
        if (identity.characterId() != eveCharacterId || !identity.ownerHash().equals(standing.get().ownerHash())) {
            // the character was sold or transferred: its new owner must link it to their own account
            log.warn("The owner of character {} has changed", eveCharacterId);
            characters.markNeedsReauthorization(eveCharacterId);
            cache.remove(eveCharacterId);
            unsavedRefreshTokens.remove(eveCharacterId);
            return TokenGrant.unavailable(TokenGrant.Problem.NEEDS_REAUTHORIZATION);
        }
        if (!refreshed.refreshToken().equals(current)) {
            rotated.increment();
            if (rotationSeen.add(eveCharacterId)) {
                log.info("EVE returned a different refresh token for character {}: refresh token rotation is on",
                        eveCharacterId);
            }
        }
        if (!refreshed.refreshToken().equals(saved)) {
            // the newest refresh token is the one to keep. If it cannot be saved it is not lost: it is held in memory,
            // sent at the next refresh, and saved again at the next call, because EVE may have retired the old one.
            try {
                characters.replaceToken(eveCharacterId, cipher.encrypt(eveCharacterId, refreshed.refreshToken()));
                unsavedRefreshTokens.remove(eveCharacterId);
            } catch (RuntimeException e) {
                unsavedRefreshTokens.put(eveCharacterId, refreshed.refreshToken());
                log.error("Could not save a newer refresh token for character {}: {}. It is kept in memory and saving "
                        + "is tried again.", eveCharacterId, e.getClass().getSimpleName());
            }
        } else {
            unsavedRefreshTokens.remove(eveCharacterId);
        }
        if (!identity.scopes().equals(standing.get().scopes())) {
            characters.updateScopes(eveCharacterId, identity.scopes());
        }
        cache.put(eveCharacterId, new Cached(refreshed.accessToken(), identity.expiresAt(), identity.scopes()));
        return identity.scopes().contains(requiredScope) ? TokenGrant.of(refreshed.accessToken())
                : TokenGrant.unavailable(TokenGrant.Problem.MISSING_SCOPE);
    }
}
