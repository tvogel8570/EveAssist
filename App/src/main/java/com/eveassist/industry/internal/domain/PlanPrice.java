package com.eveassist.industry.internal.domain;

import java.math.BigDecimal;
import java.time.Instant;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * What one item cost when the plan last took its price. A side with no orders is null. The plan keeps this until the
 * user asks for new prices, so a quote can be kept as it was.
 */
@ValueObject
public record PlanPrice(BigDecimal sell, BigDecimal buy, Instant asOf) {
}
