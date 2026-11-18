package com.eveassist;

import com.eveassist.industry.internal.application.AdjustedPrices;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The adjusted prices that integration tests give costing, since the application has no source of them yet. It knows none until a
 * test gives one, and {@link IntegrationTest} forgets them before each test. It is registered in {@link TestEsiConfig} with the fake
 * ESI, so that every test shares one application context and one connection pool.
 */
public class TestAdjustedPrices implements AdjustedPrices {

    private final Map<TypeId, BigDecimal> prices = new ConcurrentHashMap<>();

    public void reset() {
        prices.clear();
    }

    public void put(TypeId type, String price) {
        prices.put(type, new BigDecimal(price));
    }

    public void remove(TypeId type) {
        prices.remove(type);
    }

    @Override
    public Optional<BigDecimal> adjustedPrice(TypeId type) {
        return Optional.ofNullable(prices.get(type));
    }
}
