package com.eveassist.market;

import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Instant;

public record JitaPrice(TypeId type, BigDecimal sell, Instant asOf) {
}
