package com.eveassist.industry.internal.domain;

import java.math.BigDecimal;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A price for the project and what it earns. The margin and profit are for the developer only.
 *
 * @param price the total price, rounded up to the quote step
 * @param pricePerUnit the price over the units delivered
 * @param profit the price less the delivered cost
 * @param realizedMarginPercent the profit as a percent of the price, a little above the asked margin after rounding up
 */
@ValueObject
public record ProjectQuote(BigDecimal price, BigDecimal pricePerUnit, BigDecimal profit, BigDecimal realizedMarginPercent) {
}
