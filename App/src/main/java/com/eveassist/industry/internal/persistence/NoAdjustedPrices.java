package com.eveassist.industry.internal.persistence;

import com.eveassist.industry.internal.application.AdjustedPrices;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Until ESI's adjusted prices are read and stored (step 2 of the project backend), no item has one. Every job then warns that its
 * estimated item value is missing, and no quote can be issued, which makes the missing read visible and not silently wrong.
 */
@Component
class NoAdjustedPrices implements AdjustedPrices {

    @Override
    public Optional<BigDecimal> adjustedPrice(TypeId type) {
        return Optional.empty();
    }
}
