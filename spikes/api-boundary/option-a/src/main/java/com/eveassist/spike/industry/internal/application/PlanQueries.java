package com.eveassist.spike.industry.internal.application;

import com.eveassist.spike.industry.EditorRow;
import com.eveassist.spike.industry.PlanCosting;
import com.eveassist.spike.industry.PlanDetails;
import com.eveassist.spike.industry.PlanEditorView;
import com.eveassist.spike.industry.PlanId;
import com.eveassist.spike.industry.PlanListRow;
import com.eveassist.spike.industry.PlanNotFoundException;
import com.eveassist.spike.industry.PlanSummary;
import com.eveassist.spike.industry.internal.domain.Plan;
import com.eveassist.spike.industry.internal.domain.PlanLine;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlanQueries {
    private final PlanRepository plans;

    PlanQueries(PlanRepository plans) {
        this.plans = plans;
    }

    public List<PlanSummary> summariesOwnedBy(String accountId) {
        return plans.findByOwner(accountId).stream()
                .map(p -> new PlanSummary(p.id(), p.name(), p.lines().size(), p.version())).toList();
    }

    public PlanDetails details(PlanId id) {
        Plan plan = load(id);
        return new PlanDetails(plan.id(), plan.name(), plan.lines().stream()
                .map(l -> new PlanDetails.Line(l.id(), l.itemName(), l.quantity())).toList(), plan.version());
    }

    public PlanCosting costing(PlanId id) {
        Plan plan = load(id);
        return new PlanCosting(plan.id(), plan.lines().stream()
                .map(l -> new PlanCosting.LineCost(l.id(), l.unitPrice(), l.lineCost())).toList(),
                plan.totalCost(), plan.version(), Plan.PRICE_SNAPSHOT, Plan.PRICES_AS_OF);
    }

    public List<PlanListRow> listRowsOwnedBy(String accountId) {
        return plans.findByOwner(accountId).stream()
                .map(p -> new PlanListRow(p.id(), p.name(), p.lines().size(), p.totalCost())).toList();
    }

    public PlanEditorView editor(PlanId id) {
        Plan plan = load(id);
        return new PlanEditorView(plan.id(), plan.name(), plan.lines().stream().map(PlanQueries::row).toList(),
                plan.totalCost());
    }

    static EditorRow row(PlanLine line) {
        return new EditorRow(line.id(), line.itemName(), line.quantity(), line.unitPrice(), line.lineCost());
    }

    private Plan load(PlanId id) {
        return plans.findById(id).orElseThrow(() -> new PlanNotFoundException(id));
    }
}
