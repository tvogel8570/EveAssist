package com.eveassist.esi.internal;

import com.eveassist.esi.AccessTokens;
import com.eveassist.esi.TokenGrant;
import com.eveassist.esi.CharacterAsset;
import com.eveassist.esi.EsiClient;
import com.eveassist.esi.StructureLookup;
import com.eveassist.esi.EsiResult;
import com.eveassist.esi.MarketOrder;
import com.eveassist.esi.PublicContract;
import com.eveassist.esi.PublicContractItem;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The production {@link EsiClient}. Follows the ESI documentation: a User-Agent with an email, a compatibility date,
 * no call before {@code Expires}, {@code If-None-Match} afterwards, pages that must share one {@code Last-Modified},
 * and the limits in {@link EsiLimiter}. Never retries inside a call: a failure is returned and the caller tries later.
 */
@Component
class HttpEsiClient implements EsiClient {

    private static final Logger log = LoggerFactory.getLogger(HttpEsiClient.class);
    private static final String MARKET_ORDER_GROUP = "market-order";
    /** 1,800 tokens per 15 minutes per character, from ESI's OpenAPI spec. */
    private static final String CHARACTER_ASSET_GROUP = "char-asset";
    /** ESI's OpenAPI spec names no rate-limit group for this route, so only the error limit applies. */
    private static final String STRUCTURE_GROUP = "universe-structure";
    /** 1,800 tokens per 15 minutes per corporation, from ESI's OpenAPI spec. */
    private static final String CORPORATION_ASSET_GROUP = "corp-asset";
    /** The spec puts the divisions route in the wallet group (300 tokens per 15 minutes). */
    private static final String CORPORATION_DIVISIONS_GROUP = "corp-wallet";
    private static final String CHARACTER_DETAIL_GROUP = "char-detail";
    /** The spec names no group for the public character and corporation routes, so only the error limit applies. */
    private static final String PUBLIC_DETAIL_GROUP = "public-detail";
    /** ESI's OpenAPI spec names no rate-limit group for the public contract routes, so only the error limit applies. */
    private static final String PUBLIC_CONTRACT_GROUP = "contracts-public";
    /** Used when ESI sends neither {@code Expires} nor {@code max-age}. */
    private static final Duration FALLBACK_CACHE_TIME = Duration.ofMinutes(5);
    private static final Pattern MAX_AGE = Pattern.compile("max-age=(\\d+)");

    private final RestClient http;
    private final EsiCache cache;
    private final EsiLimiter limiter;
    private final Clock clock;
    private final Semaphore simultaneous;
    private final AccessTokens tokens;

    HttpEsiClient(RestClient.Builder builder, EsiProperties properties, EsiCache cache, EsiLimiter limiter,
                  Clock clock) {
        this(builder, properties, cache, limiter, clock, new NoAccessTokens());
    }

    @Autowired
    HttpEsiClient(RestClient.Builder builder, EsiProperties properties, EsiCache cache, EsiLimiter limiter,
                  Clock clock, AccessTokens tokens) {
        this.tokens = tokens;
        this.http = builder.baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent(properties))
                .defaultHeader("X-Compatibility-Date", properties.compatibilityDate())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
        this.cache = cache;
        this.limiter = limiter;
        this.clock = clock;
        this.simultaneous = new Semaphore(properties.maxConcurrent());
        if (properties.contactEmail().isBlank()) {
            log.warn("eveassist.esi.contact-email is not set (ESI_CONTACT_EMAIL): ESI strongly prefers an email in the User-Agent");
        }
    }

    @Override
    public EsiResult<List<MarketOrder>> marketOrders(int regionId, int typeId) {
        return paged(MARKET_ORDER_GROUP, "/markets/%d/orders?type_id=%d".formatted(regionId, typeId), true, false,
                new ParameterizedTypeReference<List<EsiOrder>>() { })
                .map(orders -> orders.stream().map(HttpEsiClient::toMarketOrder).toList());
    }

    @Override
    public EsiResult<List<PublicContract>> publicContracts(int regionId) {
        return paged(PUBLIC_CONTRACT_GROUP, "/contracts/public/%d".formatted(regionId), true, false,
                new ParameterizedTypeReference<List<EsiContract>>() { })
                .map(contracts -> contracts.stream().map(HttpEsiClient::toPublicContract).toList());
    }

    @Override
    public EsiResult<List<PublicContractItem>> publicContractItems(long contractId) {
        return paged(PUBLIC_CONTRACT_GROUP, "/contracts/public/items/%d".formatted(contractId), false, true,
                new ParameterizedTypeReference<List<EsiContractItem>>() { })
                .map(items -> items.stream().map(HttpEsiClient::toPublicContractItem).toList());
    }

    @Override
    public EsiResult<List<CharacterAsset>> characterAssets(long eveCharacterId) {
        return paged(CHARACTER_ASSET_GROUP, "/characters/%d/assets".formatted(eveCharacterId), true, false,
                new ParameterizedTypeReference<List<EsiAsset>>() { },
                new Auth(eveCharacterId, ASSETS_SCOPE))
                .map(assets -> assets.stream().map(HttpEsiClient::toCharacterAsset).toList());
    }

    @Override
    public EsiResult<Long> characterCorporationId(long eveCharacterId) {
        var answer = object(PUBLIC_DETAIL_GROUP, "/characters/%d".formatted(eveCharacterId), null, EsiCharacter.class);
        if (answer.data() != null && answer.data().corporationId() <= 0) {
            // an answer without a corporation (an unexpected shape) must not become corporation 0
            return EsiResult.failed("ESI gave no corporation for character " + eveCharacterId);
        }
        return answer.map(character -> character.corporationId());
    }

    @Override
    public EsiResult<java.util.Set<String>> characterRoles(long eveCharacterId) {
        return object(CHARACTER_DETAIL_GROUP, "/characters/%d/roles".formatted(eveCharacterId),
                new Auth(eveCharacterId, ROLES_SCOPE), EsiRoles.class)
                .map(roles -> roles.roles() == null ? java.util.Set.<String>of() : java.util.Set.copyOf(roles.roles()));
    }

    @Override
    public EsiResult<String> corporationName(long eveCorporationId) {
        return object(PUBLIC_DETAIL_GROUP, "/corporations/%d".formatted(eveCorporationId), null, EsiCorporation.class)
                .map(corporation -> corporation.name());
    }

    @Override
    public EsiResult<List<CharacterAsset>> corporationAssets(long eveCorporationId, long viaEveCharacterId) {
        return paged(CORPORATION_ASSET_GROUP, "/corporations/%d/assets".formatted(eveCorporationId), true, false,
                new ParameterizedTypeReference<List<EsiAsset>>() { },
                new Auth(viaEveCharacterId, CORPORATION_ASSETS_SCOPE))
                .map(assets -> assets.stream().map(HttpEsiClient::toCharacterAsset).toList());
    }

    @Override
    public EsiResult<java.util.Map<Long, String>> characterAssetNames(long eveCharacterId, List<Long> itemIds) {
        return names(CHARACTER_ASSET_GROUP, "/characters/%d/assets/names".formatted(eveCharacterId),
                new Auth(eveCharacterId, ASSETS_SCOPE), itemIds);
    }

    @Override
    public EsiResult<java.util.Map<Long, String>> corporationAssetNames(long eveCorporationId, long viaEveCharacterId,
            List<Long> itemIds) {
        return names(CORPORATION_ASSET_GROUP, "/corporations/%d/assets/names".formatted(eveCorporationId),
                new Auth(viaEveCharacterId, CORPORATION_ASSETS_SCOPE), itemIds);
    }

    /** ESI takes at most this many item ids in one names call. */
    private static final int MOST_NAMES_PER_CALL = 1000;

    /**
     * Asks for the names in chunks of {@link #MOST_NAMES_PER_CALL}. One failed chunk fails the whole result, so the
     * caller never mistakes "some names" for "all names". ESI answers an item that has no name of its own with the word
     * "None", which is left out here as well as an empty one. Spaces are never trimmed.
     */
    private EsiResult<java.util.Map<Long, String>> names(String group, String uri, Auth auth, List<Long> itemIds) {
        var result = new java.util.LinkedHashMap<Long, String>();
        for (int from = 0; from < itemIds.size(); from += MOST_NAMES_PER_CALL) {
            var chunk = itemIds.subList(from, Math.min(from + MOST_NAMES_PER_CALL, itemIds.size()));
            var answer = postNames(group, uri, auth, chunk);
            if (answer.problem() != null) {
                return EsiResult.failed(answer.problem());
            }
            if (answer.status() != 200 || answer.body() == null) {
                // ESI's own words say why, for example which ids it refused. They hold no token.
                return EsiResult.failed("ESI answered " + answer.status() + " for " + uri.replaceFirst("^/", "")
                        + (answer.error() == null ? "" : ": " + answer.error()));
            }
            for (var entry : answer.body()) {
                // a name is kept exactly as the player typed it: players put spaces first to control how the game
                // sorts their containers and ships, so only an empty name, or ESI's word for "no name", means unnamed
                if (entry.name() != null && !entry.name().isEmpty() && !"None".equals(entry.name())) {
                    result.put(entry.itemId(), entry.name());
                }
            }
        }
        return EsiResult.updated(result, clock.instant());
    }

    private Single<List<EsiName>> postNames(String group, String uri, Auth auth, List<Long> ids) {
        var answer = postNamesOnce(group, uri, auth, ids);
        if (answer.status() == 401) {
            tokens.invalidate(auth.eveCharacterId());
            answer = postNamesOnce(group, uri, auth, ids);
        }
        return answer;
    }

    private Single<List<EsiName>> postNamesOnce(String group, String uri, Auth auth, List<Long> ids) {
        var wait = limiter.before(group);
        if (wait.isPresent()) {
            return Single.problem(wait.get());
        }
        var grant = tokens.accessToken(auth.eveCharacterId(), auth.scope());
        if (grant.problem() != null) {
            return Single.problem(why(grant, auth));
        }
        simultaneous.acquireUninterruptibly();
        try {
            // The body is written out here and sent with its length, not streamed in chunks: ESI answered some calls with
            // "too few items for 'item_ids'", as if the body had not arrived (seen 2026-11-10).
            var json = ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",", "[", "]"))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            var answer = http.post().uri(uri)
                    .headers(headers -> headers.setBearerAuth(grant.token()))
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .contentLength(json.length)
                    .body(json)
                    .exchange((request, response) -> new Single<List<EsiName>>(response.getStatusCode().value(),
                            response.getHeaders(),
                            response.getStatusCode().value() == 200
                                    ? response.bodyTo(new ParameterizedTypeReference<List<EsiName>>() { }) : null,
                            null, response.getStatusCode().value() == 200 ? null : errorText(response)));
            limiter.record(group, answer.status(), answer.headers());
            return answer;
        } catch (RestClientException e) {
            return Single.problem("ESI could not be reached: " + e.getMessage());
        } finally {
            simultaneous.release();
        }
    }

    @Override
    public EsiResult<java.util.Map<Integer, String>> corporationHangars(long eveCorporationId,
                                                                      long viaEveCharacterId) {
        return object(CORPORATION_DIVISIONS_GROUP, "/corporations/%d/divisions".formatted(eveCorporationId),
                new Auth(viaEveCharacterId, DIVISIONS_SCOPE), EsiDivisions.class)
                .map(divisions -> {
                    var names = new java.util.TreeMap<Integer, String>();
                    if (divisions.hangar() != null) {
                        divisions.hangar().stream().filter(h -> h.name() != null)
                                .forEach(h -> names.put(h.division(), h.name()));
                    }
                    return (java.util.Map<Integer, String>) names;
                });
    }

    /**
     * One object route, not cached here: the caller keeps the answer and asks again after a while (see esi.md). A
     * missing token or a status other than 200 is a failure with a reason that holds no token.
     */
    private <T> EsiResult<T> object(String group, String uri, Auth auth, Class<T> type) {
        var answer = getObject(group, uri, auth, type);
        if (answer.problem() != null) {
            return EsiResult.failed(answer.problem());
        }
        if (answer.status() != 200 || answer.body() == null) {
            return EsiResult.failed("ESI answered " + answer.status() + " for " + uri.replaceFirst("^/", ""));
        }
        return EsiResult.updated(answer.body(), expiresOf(answer.headers()));
    }

    @Override
    public StructureLookup structure(long eveCharacterId, long structureId) {
        var auth = new Auth(eveCharacterId, STRUCTURES_SCOPE);
        var uri = "/universe/structures/%d".formatted(structureId);
        var answer = getObject(STRUCTURE_GROUP, uri, auth, EsiStructure.class);
        if (answer.problem() != null) {
            return StructureLookup.failed(answer.problem());
        }
        return switch (answer.status()) {
            case 200 -> answer.body() == null || answer.body().name() == null
                    ? StructureLookup.failed("ESI answered without a name for structure " + structureId)
                    : StructureLookup.found(answer.body().name(), answer.body().solarSystemId());
            case 403 -> StructureLookup.noAccess(answer.error());
            case 404 -> StructureLookup.notFound(answer.error());
            default -> StructureLookup.failed("ESI answered " + answer.status() + " for structure " + structureId);
        };
    }

    /** One object route, asked with a token, and once more after a 401. */
    private <T> Single<T> getObject(String group, String uri, Auth auth, Class<T> type) {
        var answer = getObjectOnce(group, uri, auth, type);
        if (answer.status() == 401 && auth != null) {
            tokens.invalidate(auth.eveCharacterId());
            answer = getObjectOnce(group, uri, auth, type);
        }
        return answer;
    }

    /** With no {@code auth} the route is public and is asked without a token. */
    private <T> Single<T> getObjectOnce(String group, String uri, Auth auth, Class<T> type) {
        var wait = limiter.before(group);
        if (wait.isPresent()) {
            return Single.problem(wait.get());
        }
        String bearer = null;
        if (auth != null) {
            var grant = tokens.accessToken(auth.eveCharacterId(), auth.scope());
            if (grant.problem() != null) {
                return Single.problem(why(grant, auth));
            }
            bearer = grant.token();
        }
        var bearerToken = bearer;
        simultaneous.acquireUninterruptibly();
        try {
            var answer = http.get().uri(uri)
                    .headers(headers -> {
                        if (bearerToken != null) {
                            headers.setBearerAuth(bearerToken);
                        }
                    })
                    .exchange((request, response) -> new Single<T>(response.getStatusCode().value(),
                            response.getHeaders(),
                            response.getStatusCode().value() == 200 ? response.bodyTo(type) : null,
                            null, response.getStatusCode().value() == 200 ? null : errorText(response)));
            limiter.record(group, answer.status(), answer.headers());
            return answer;
        } catch (RestClientException e) {
            return Single.problem("ESI could not be reached: " + e.getMessage());
        } finally {
            simultaneous.release();
        }
    }

    private static String why(TokenGrant grant, Auth auth) {
        return switch (grant.problem()) {
            case NEEDS_REAUTHORIZATION -> "The character needs to be authorized again";
            case MISSING_SCOPE -> "The character did not grant " + auth.scope();
            case UNAVAILABLE -> "A token for the character is not available right now";
        };
    }

    /** Which character's token a call is made with, and the scope that call needs. */
    private record Auth(long eveCharacterId, String scope) {
    }

    /**
     * Reads a route through all its pages. With {@code cached}, the route is not asked again before {@code Expires}
     * and is asked with {@code If-None-Match} afterwards. A 204 gives an updated, empty list. With {@code goneOn403},
     * so does a 403: for a public contract's lines, ESI's OpenAPI spec says 204 for a contract that has expired or been
     * accepted, but ESI answers 403 "Contract accepted by player, requires authorization" for one accepted after the
     * list was cached (seen 2026-11-09).
     */
    private <E> EsiResult<List<E>> paged(String group, String path, boolean cached, boolean goneOn403,
                                         ParameterizedTypeReference<List<E>> type) {
        return paged(group, path, cached, goneOn403, type, null);
    }

    private <E> EsiResult<List<E>> paged(String group, String path, boolean cached, boolean goneOn403,
                                         ParameterizedTypeReference<List<E>> type, Auth auth) {
        var key = path.substring(1);
        var stored = cached ? cache.find(key) : Optional.<EsiCache.Entry>empty();
        if (stored.isPresent() && stored.get().expires().isAfter(clock.instant())) {
            return EsiResult.unchanged(stored.get().expires());
        }
        var separator = path.contains("?") ? "&" : "?";
        // A page set that changes while it is read is read again once, without asking for a 304
        for (int attempt = 0; attempt < 2; attempt++) {
            var etag = attempt == 0 ? stored.map(EsiCache.Entry::etag).orElse(null) : null;
            var first = get(group, path + separator + "page=1", etag, type, auth);
            if (first.problem() != null) {
                return EsiResult.failed(first.problem());
            }
            if (first.status() == 304 && stored.isPresent()) {
                var expires = expiresOf(first.headers());
                cache.save(key, new EsiCache.Entry(stored.get().etag(), stored.get().lastModified(), expires),
                        clock.instant());
                return EsiResult.unchanged(expires);
            }
            if (first.status() == 204 || (goneOn403 && first.status() == 403)) {
                return EsiResult.updated(List.of(), expiresOf(first.headers()));
            }
            if (first.status() != 200) {
                return EsiResult.failed("ESI answered " + first.status() + " for " + key);
            }
            var rows = new ArrayList<>(first.body());
            var lastModified = first.headers().getLastModified();
            var pages = (int) number(first.headers().getFirst("X-Pages"), 1);
            var consistent = true;
            for (int page = 2; page <= pages && consistent; page++) {
                var next = get(group, path + separator + "page=" + page, null, type, auth);
                if (next.problem() != null) {
                    return EsiResult.failed(next.problem());
                }
                if (next.status() != 200) {
                    return EsiResult.failed("ESI answered " + next.status() + " for page " + page + " of " + key);
                }
                consistent = next.headers().getLastModified() == lastModified;
                rows.addAll(next.body());
            }
            if (consistent) {
                var expires = expiresOf(first.headers());
                if (cached) {
                    cache.save(key, new EsiCache.Entry(first.headers().getETag(),
                            lastModified < 0 ? null : Instant.ofEpochMilli(lastModified), expires), clock.instant());
                }
                return EsiResult.updated(rows, expires);
            }
        }
        return EsiResult.failed("The pages of " + key + " changed while they were being read");
    }

    private <E> Page<E> get(String group, String uri, String etag, ParameterizedTypeReference<List<E>> type,
                            Auth auth) {
        var page = getOnce(group, uri, etag, type, auth);
        if (page.status() == 401 && auth != null) {
            // The token was refused: forget it and ask once more with a fresh one. Never a loop.
            tokens.invalidate(auth.eveCharacterId());
            page = getOnce(group, uri, etag, type, auth);
        }
        return page;
    }

    private <E> Page<E> getOnce(String group, String uri, String etag, ParameterizedTypeReference<List<E>> type,
                                Auth auth) {
        var wait = limiter.before(group);
        if (wait.isPresent()) {
            return Page.problem(wait.get());
        }
        String bearer = null;
        if (auth != null) {
            var grant = tokens.accessToken(auth.eveCharacterId(), auth.scope());
            if (grant.problem() != null) {
                return Page.problem(why(grant, auth));
            }
            bearer = grant.token();
        }
        var bearerToken = bearer;
        simultaneous.acquireUninterruptibly();
        try {
            var page = http.get().uri(uri)
                    .headers(headers -> {
                        if (etag != null) {
                            headers.set(HttpHeaders.IF_NONE_MATCH, etag);
                        }
                        if (bearerToken != null) {
                            headers.setBearerAuth(bearerToken);
                        }
                    })
                    .exchange((request, response) -> new Page<E>(response.getStatusCode().value(),
                            response.getHeaders(), bodyOf(response, type), null));
            limiter.record(group, page.status(), page.headers());
            return page;
        } catch (RestClientException e) {
            return Page.problem("ESI could not be reached: " + e.getMessage());
        } finally {
            simultaneous.release();
        }
    }

    /**
     * The rows of a 200, or null for any other status. A 200 with no body at all is an empty list: ESI answers that
     * for some public contracts that are still in the region's list (seen 2026-11-09, content-length 0), and Spring
     * then reads the body as null.
     */
    private static <E> List<E> bodyOf(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response,
                                      ParameterizedTypeReference<List<E>> type) throws java.io.IOException {
        if (response.getStatusCode().value() != 200) {
            return null;
        }
        var body = response.bodyTo(type);
        return body == null ? List.of() : body;
    }

    private Instant expiresOf(HttpHeaders headers) {
        var expires = headers.getExpires();
        if (expires > 0) {
            return Instant.ofEpochMilli(expires);
        }
        var cacheControl = headers.getCacheControl();
        if (cacheControl != null) {
            var matcher = MAX_AGE.matcher(cacheControl);
            if (matcher.find()) {
                return clock.instant().plusSeconds(Long.parseLong(matcher.group(1)));
            }
        }
        return clock.instant().plus(FALLBACK_CACHE_TIME);
    }

    private static long number(String text, long fallback) {
        try {
            return Long.parseLong(text.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String userAgent(EsiProperties properties) {
        var version = HttpEsiClient.class.getPackage().getImplementationVersion();
        var contact = properties.contactEmail().isBlank() ? "" : properties.contactEmail() + "; ";
        return "EveAssist/%s (%s+https://github.com/tvogel8570/EveAssist)".formatted(version == null ? "dev" : version, contact);
    }

    private static MarketOrder toMarketOrder(EsiOrder order) {
        return new MarketOrder(order.orderId(), order.typeId(), order.locationId(), order.buy(), order.price(),
                order.volumeRemain());
    }

    private static CharacterAsset toCharacterAsset(EsiAsset asset) {
        return new CharacterAsset(asset.itemId(), asset.typeId(), asset.quantity(), asset.locationId(),
                asset.locationType(), asset.locationFlag(), asset.singleton(), asset.blueprintCopy());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EsiAsset(
            @JsonProperty("item_id") long itemId,
            @JsonProperty("type_id") int typeId,
            @JsonProperty("quantity") long quantity,
            @JsonProperty("location_id") long locationId,
            @JsonProperty("location_type") String locationType,
            @JsonProperty("location_flag") String locationFlag,
            @JsonProperty("is_singleton") boolean singleton,
            @JsonProperty("is_blueprint_copy") Boolean blueprintCopy) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EsiStructure(
            @JsonProperty("name") String name,
            @JsonProperty("solar_system_id") int solarSystemId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EsiName(@JsonProperty("item_id") long itemId, @JsonProperty("name") String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EsiCharacter(@JsonProperty("corporation_id") long corporationId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EsiCorporation(@JsonProperty("name") String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EsiRoles(@JsonProperty("roles") List<String> roles) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EsiDivisions(@JsonProperty("hangar") List<EsiHangar> hangar) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EsiHangar(@JsonProperty("division") int division, @JsonProperty("name") String name) {
    }

    private record Single<T>(int status, HttpHeaders headers, T body, String problem, String error) {
        static <T> Single<T> problem(String problem) {
            return new Single<>(0, null, null, problem, null);
        }
    }

    /** ESI's own words for an error, such as {"error":"Forbidden"}, cut short. It holds no token. */
    private static String errorText(org.springframework.http.client.ClientHttpResponse response) {
        try {
            var text = new String(response.getBody().readNBytes(300), java.nio.charset.StandardCharsets.UTF_8).trim();
            return text.isEmpty() ? null : text;
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private record Page<E>(int status, HttpHeaders headers, List<E> body, String problem) {
        static <E> Page<E> problem(String problem) {
            return new Page<>(0, null, null, problem);
        }
    }

    private static PublicContract toPublicContract(EsiContract contract) {
        return new PublicContract(contract.contractId(), contract.type(),
                contract.price() == null ? BigDecimal.ZERO : contract.price(), contract.issuerId(),
                contract.dateIssued(), contract.dateExpired(), contract.startLocationId(),
                contract.volume() == null ? 0 : contract.volume());
    }

    private static PublicContractItem toPublicContractItem(EsiContractItem item) {
        return new PublicContractItem(item.recordId(), item.typeId(), item.quantity(), item.included(),
                Boolean.TRUE.equals(item.blueprintCopy()), item.materialEfficiency(), item.timeEfficiency(), item.runs());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EsiOrder(
            @JsonProperty("order_id") long orderId,
            @JsonProperty("type_id") int typeId,
            @JsonProperty("location_id") long locationId,
            @JsonProperty("is_buy_order") boolean buy,
            @JsonProperty("price") BigDecimal price,
            @JsonProperty("volume_remain") long volumeRemain) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EsiContract(
            @JsonProperty("contract_id") long contractId,
            @JsonProperty("type") String type,
            @JsonProperty("price") BigDecimal price,
            @JsonProperty("issuer_id") long issuerId,
            @JsonProperty("date_issued") Instant dateIssued,
            @JsonProperty("date_expired") Instant dateExpired,
            @JsonProperty("start_location_id") Long startLocationId,
            @JsonProperty("volume") Double volume) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EsiContractItem(
            @JsonProperty("record_id") long recordId,
            @JsonProperty("type_id") int typeId,
            @JsonProperty("quantity") long quantity,
            @JsonProperty("is_included") boolean included,
            @JsonProperty("is_blueprint_copy") Boolean blueprintCopy,
            @JsonProperty("material_efficiency") Integer materialEfficiency,
            @JsonProperty("time_efficiency") Integer timeEfficiency,
            @JsonProperty("runs") Integer runs) {
    }
}
