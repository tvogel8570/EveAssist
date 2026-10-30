package com.eveassist.spike.industry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlanCosting(PlanId id, List<LineCost> lines, long totalCost, long planVersion, String priceSnapshot,
                          Instant pricesAsOf) {
    public PlanCosting {
        lines = List.copyOf(lines);
    }

    public record LineCost(UUID lineId, long unitPrice, long lineCost) {
    }
}
