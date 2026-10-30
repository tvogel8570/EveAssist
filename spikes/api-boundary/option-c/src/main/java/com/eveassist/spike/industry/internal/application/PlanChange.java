package com.eveassist.spike.industry.internal.application;

import com.eveassist.spike.industry.PlanId;
import java.util.List;

/** Screen-only: what the editor must redraw after a change. */
public record PlanChange(PlanId id, List<EditorRow> changedRows, long totalCost) {
    public PlanChange {
        changedRows = List.copyOf(changedRows);
    }
}
