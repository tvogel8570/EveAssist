package com.eveassist.spike.industry.internal.application;

import com.eveassist.spike.identity.Viewer;
import com.eveassist.spike.industry.PlanCosting;
import com.eveassist.spike.industry.PlanDetails;
import com.eveassist.spike.industry.PlanId;
import com.eveassist.spike.industry.PlanSummary;
import com.eveassist.spike.industry.internal.domain.Plan;
import com.eveassist.spike.industry.internal.domain.PlanLine;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlanQueries {
    private final VisiblePlans plans;

    PlanQueries(VisiblePlans plans) {
        this.plans = plans;
    }

    public List<PlanSummary> summaries(Viewer viewer) {
        return plans.all(viewer).stream()
                .map(p -> new PlanSummary(p.id(), p.name(), p.lines().size(), p.version())).toList();
    }

    public PlanDetails details(Viewer viewer, PlanId id) {
        Plan plan = plans.require(viewer, id);
        return new PlanDetails(plan.id(), plan.name(), plan.lines().stream()
                .map(l -> new PlanDetails.Line(l.id(), l.itemName(), l.quantity())).toList(), plan.version());
    }

    public PlanCosting costing(Viewer viewer, PlanId id) {
        Plan plan = plans.require(viewer, id);
        return new PlanCosting(plan.id(), plan.lines().stream()
                .map(l -> new PlanCosting.LineCost(l.id(), l.unitPrice(), l.lineCost())).toList(),
                plan.totalCost(), plan.version(), Plan.PRICE_SNAPSHOT, Plan.PRICES_AS_OF);
    }

    public List<PlanListRow> listRows(Viewer viewer) {
        return plans.all(viewer).stream()
                .map(p -> new PlanListRow(p.id(), p.name(), p.lines().size(), p.totalCost())).toList();
    }

    public PlanEditorView editor(Viewer viewer, PlanId id) {
        Plan plan = plans.require(viewer, id);
        return new PlanEditorView(plan.id(), plan.name(), plan.lines().stream().map(PlanQueries::row).toList(),
                plan.totalCost());
    }

    static EditorRow row(PlanLine line) {
        return new EditorRow(line.id(), line.itemName(), line.quantity(), line.unitPrice(), line.lineCost());
    }
}
