package com.eveassist.esi.internal;

import com.eveassist.esi.EsiClient;
import com.eveassist.esi.EsiResult;
import com.eveassist.esi.MarketOrder;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
        var key = "markets/%d/orders?type_id=%d".formatted(regionId, typeId);
        var stored = cache.find(key);
        if (stored.isPresent() && stored.get().expires().isAfter(clock.instant())) {
            return EsiResult.unchanged(stored.get().expires());
        }
        // A page set that changes while it is read is read again once, without asking for a 304
        for (int attempt = 0; attempt < 2; attempt++) {
            var etag = attempt == 0 ? stored.map(EsiCache.Entry::etag).orElse(null) : null;
            var first = get(MARKET_ORDER_GROUP, "/markets/%d/orders?type_id=%d&page=1".formatted(regionId, typeId), etag);
            if (first.problem() != null) {
                return EsiResult.failed(first.problem());
            }
            if (first.status() == 304) {
                var expires = expiresOf(first.headers());
                cache.save(key, new EsiCache.Entry(stored.get().etag(), stored.get().lastModified(), expires),
                        clock.instant());
                return EsiResult.unchanged(expires);
            }
            if (first.status() != 200) {
                return EsiResult.failed("ESI answered " + first.status() + " for " + key);
            }
            var orders = new ArrayList<>(first.body());
            var lastModified = first.headers().getLastModified();
            var pages = (int) number(first.headers().getFirst("X-Pages"), 1);
            var consistent = true;
            for (int page = 2; page <= pages && consistent; page++) {
                var next = get(MARKET_ORDER_GROUP,
                        "/markets/%d/orders?type_id=%d&page=%d".formatted(regionId, typeId, page), null);
                if (next.problem() != null) {
                    return EsiResult.failed(next.problem());
                }
                if (next.status() != 200) {
                    return EsiResult.failed("ESI answered " + next.status() + " for page " + page + " of " + key);
                }
                consistent = next.headers().getLastModified() == lastModified;
                orders.addAll(next.body());
            }
            if (consistent) {
                var expires = expiresOf(first.headers());
                cache.save(key, new EsiCache.Entry(first.headers().getETag(),
                        lastModified < 0 ? null : Instant.ofEpochMilli(lastModified), expires), clock.instant());
                return EsiResult.updated(orders.stream().map(HttpEsiClient::toMarketOrder).toList(), expires);
            }
        }
        return EsiResult.failed("The pages of " + key + " changed while they were being read");
    }

    private Page get(String group, String uri, String etag) {
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
                    .exchange((request, response) -> new Page(response.getStatusCode().value(), response.getHeaders(),
                            response.getStatusCode().value() == 200
                                    ? response.bodyTo(new ParameterizedTypeReference<List<EsiOrder>>() { })
                                    : null,
                            null));
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

    private record Page(int status, HttpHeaders headers, List<EsiOrder> body, String problem) {
        static Page problem(String problem) {
            return new Page(0, null, null, problem);
        }
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
}
