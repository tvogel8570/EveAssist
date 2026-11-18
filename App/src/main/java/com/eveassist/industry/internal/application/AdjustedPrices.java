package com.eveassist.industry.internal.application;

import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/**
 * CCP's adjusted price of an item (ESI {@code GET /markets/prices}), which the estimated item value of a job, and so its install cost,
 * is worked out from. Public data shared by all users. Empty for an item with none, which the costing reports as a warning.
 */
@SecondaryPort
public interface AdjustedPrices {

    Optional<BigDecimal> adjustedPrice(TypeId type);
}
