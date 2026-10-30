package com.eveassist.spike.industry;

import java.util.List;
import java.util.UUID;

public record PlanDetails(PlanId id, String name, List<Line> lines, long version) {
    public PlanDetails {
        lines = List.copyOf(lines);
    }

    public record Line(UUID lineId, String itemName, int quantity) {
    }
}
