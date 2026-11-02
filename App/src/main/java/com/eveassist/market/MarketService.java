package com.eveassist.market;

import com.eveassist.market.internal.PriceRepository;
import com.eveassist.sde.TypeId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Stored Jita prices. Prices are public data, shared by all users, so these calls take no viewer. */
@Service
public class MarketService {

    private final PriceRepository prices;

    MarketService(PriceRepository prices) {
        this.prices = prices;
    }

    public Map<TypeId, JitaPrice> pricesFor(Collection<TypeId> types) {
        return prices.pricesFor(types);
    }

    public void store(List<JitaPrice> newPrices) {
        prices.store(newPrices);
    }
}
