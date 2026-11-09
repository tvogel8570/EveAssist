package com.eveassist.esi.internal;

import com.eveassist.esi.EsiClient;
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

    HttpEsiClient(RestClient.Builder builder, EsiProperties properties, EsiCache cache, EsiLimiter limiter,
                  Clock clock) {
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
        return paged(MARKET_ORDER_GROUP, "/markets/%d/orders?type_id=%d".formatted(regionId, typeId), true,
                new ParameterizedTypeReference<List<EsiOrder>>() { })
                .map(orders -> orders.stream().map(HttpEsiClient::toMarketOrder).toList());
    }

    @Override
    public EsiResult<List<PublicContract>> publicContracts(int regionId) {
        return paged(PUBLIC_CONTRACT_GROUP, "/contracts/public/%d".formatted(regionId), true,
                new ParameterizedTypeReference<List<EsiContract>>() { })
                .map(contracts -> contracts.stream().map(HttpEsiClient::toPublicContract).toList());
    }

    @Override
    public EsiResult<List<PublicContractItem>> publicContractItems(long contractId) {
        return paged(PUBLIC_CONTRACT_GROUP, "/contracts/public/items/%d".formatted(contractId), false,
                new ParameterizedTypeReference<List<EsiContractItem>>() { })
                .map(items -> items.stream().map(HttpEsiClient::toPublicContractItem).toList());
    }

    /**
     * Reads a route through all its pages. With {@code cached}, the route is not asked again before {@code Expires}
     * and is asked with {@code If-None-Match} afterwards. A 204 gives an updated, empty list.
     */
    private <E> EsiResult<List<E>> paged(String group, String path, boolean cached,
                                         ParameterizedTypeReference<List<E>> type) {
        var key = path.substring(1);
        var stored = cached ? cache.find(key) : Optional.<EsiCache.Entry>empty();
        if (stored.isPresent() && stored.get().expires().isAfter(clock.instant())) {
            return EsiResult.unchanged(stored.get().expires());
        }
        var separator = path.contains("?") ? "&" : "?";
        // A page set that changes while it is read is read again once, without asking for a 304
        for (int attempt = 0; attempt < 2; attempt++) {
            var etag = attempt == 0 ? stored.map(EsiCache.Entry::etag).orElse(null) : null;
            var first = get(group, path + separator + "page=1", etag, type);
            if (first.problem() != null) {
                return EsiResult.failed(first.problem());
            }
            if (first.status() == 304 && stored.isPresent()) {
                var expires = expiresOf(first.headers());
                cache.save(key, new EsiCache.Entry(stored.get().etag(), stored.get().lastModified(), expires),
                        clock.instant());
                return EsiResult.unchanged(expires);
            }
            if (first.status() == 204) {
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
                var next = get(group, path + separator + "page=" + page, null, type);
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

    private <E> Page<E> get(String group, String uri, String etag, ParameterizedTypeReference<List<E>> type) {
        var wait = limiter.before(group);
        if (wait.isPresent()) {
            return Page.problem(wait.get());
        }
        simultaneous.acquireUninterruptibly();
        try {
            var page = http.get().uri(uri)
                    .headers(headers -> {
                        if (etag != null) {
                            headers.set(HttpHeaders.IF_NONE_MATCH, etag);
                        }
                    })
                    .exchange((request, response) -> new Page<E>(response.getStatusCode().value(),
                            response.getHeaders(),
                            response.getStatusCode().value() == 200 ? response.bodyTo(type) : null, null));
            limiter.record(group, page.status(), page.headers());
            return page;
        } catch (RestClientException e) {
            return Page.problem("ESI could not be reached: " + e.getMessage());
        } finally {
            simultaneous.release();
        }
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
