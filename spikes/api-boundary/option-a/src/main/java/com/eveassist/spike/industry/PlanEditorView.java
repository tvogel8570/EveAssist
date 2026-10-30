package com.eveassist.spike.industry;

import java.util.List;

/** Screen-only. */
public record PlanEditorView(PlanId id, String name, List<EditorRow> rows, long totalCost) {
    public PlanEditorView {
        rows = List.copyOf(rows);
    }
}
