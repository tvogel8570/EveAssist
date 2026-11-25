package com.eveassist.market;

import com.eveassist.esi.EsiClient;
import com.eveassist.esi.EsiResult.Outcome;
import com.eveassist.market.internal.AdjustedPriceRepository;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * CCP's adjusted prices, which a job's estimated item value, and so its install cost, is worked out from. Public data shared by all
 * users, so these calls take no viewer. ESI caches the prices for an hour and the client keeps to that, so asking to refresh before
 * then costs no call to ESI. Not transactional: no database connection is held while ESI is being asked.
 */
@Service
public class AdjustedPriceService {

    private final AdjustedPriceRepository prices;
    private final EsiClient esi;
    private final Clock clock;

    AdjustedPriceService(AdjustedPriceRepository prices, EsiClient esi, Clock clock) {
        this.prices = prices;
        this.esi = esi;
        this.clock = clock;
    }

    /**
     * Takes ESI's copy if the stored one may be out of date. A call that fails keeps what is stored, and says why. ESI answering
     * "unchanged" while nothing is stored (the table was emptied, or the client remembers an {@code ETag} from before) would repeat
     * for as long as ESI's copy lasts, so the client is told to forget it and ESI is asked once more for the whole answer.
     */
    public AdjustedPriceRefresh refresh() {
        var result = esi.marketPrices();
        if (result.outcome() == Outcome.UNCHANGED && !prices.hasAny()) {
            esi.forgetMarketPrices();
            result = esi.marketPrices();
        }
        var now = clock.instant();
        String problem = null;
        switch (result.outcome()) {
            case UPDATED -> prices.replaceAll(result.data(), now, result.lastModified());
            case UNCHANGED -> {
                if (prices.hasAny()) {
                    prices.confirm(now, result.lastModified());
                } else {
                    problem = "ESI says its prices have not changed, but none are stored";
                }
            }
            case FAILED -> problem = result.problem();
        }
        return new AdjustedPriceRefresh(prices.age().orElse(null), problem);
    }

    /** The stored adjusted price of each item that has one. An item with none is left out. */
    public Map<TypeId, BigDecimal> of(Collection<TypeId> types) {
        return prices.pricesOf(types);
    }

    /** How old the stored prices are, in both senses (see {@link AdjustedPriceAge}), or empty if there are none. */
    public Optional<AdjustedPriceAge> age() {
        return prices.age();
    }
}
