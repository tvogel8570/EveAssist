package com.eveassist.characters.internal;

import com.eveassist.esi.AccessTokens;
import com.eveassist.esi.TokenGrant;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    CharacterAccessTokens(CharacterRepository characters, EveSso sso, TokenCipher cipher, DataSource dataSource,
            Clock clock) {
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
        var cached = usable(eveCharacterId);
        if (cached != null) {
            return TokenGrant.of(cached.token());
        }
        var lock = locks.computeIfAbsent(eveCharacterId, id -> new ReentrantLock());
        lock.lock();
        try {
            return refreshLocked(eveCharacterId, requiredScope);
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
        String current;
        try {
            current = cipher.decrypt(eveCharacterId, stored.get());
            refreshed = sso.refresh(current);
        } catch (EveSso.InvalidGrantException e) {
            log.warn("EVE refused the saved access of character {}", eveCharacterId);
            characters.markNeedsReauthorization(eveCharacterId);
            cache.remove(eveCharacterId);
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
            return TokenGrant.unavailable(TokenGrant.Problem.NEEDS_REAUTHORIZATION);
        }
        if (!refreshed.refreshToken().equals(current)) {
            // the newest refresh token is the one to keep
            characters.replaceToken(eveCharacterId, cipher.encrypt(eveCharacterId, refreshed.refreshToken()));
        }
        if (!identity.scopes().equals(standing.get().scopes())) {
            characters.updateScopes(eveCharacterId, identity.scopes());
        }
        cache.put(eveCharacterId, new Cached(refreshed.accessToken(), identity.expiresAt(), identity.scopes()));
        return identity.scopes().contains(requiredScope) ? TokenGrant.of(refreshed.accessToken())
                : TokenGrant.unavailable(TokenGrant.Problem.MISSING_SCOPE);
    }
}
