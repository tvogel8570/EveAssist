package com.eveassist.spike.industry;

import com.eveassist.spike.identity.Viewer;
import com.eveassist.spike.industry.internal.application.ChangeLineQuantity;
import com.eveassist.spike.industry.internal.application.PlanQueries;
import com.eveassist.spike.industry.internal.application.PlanVisibility;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Option A: every caller comes through here, so visibility is checked here. Pages and the external API share it, and
 * any other module can call the screen methods too.
 */
@Service
public class IndustryService {
    private final PlanVisibility visibility;
    private final PlanQueries queries;
    private final ChangeLineQuantity changeLineQuantity;

    IndustryService(PlanVisibility visibility, PlanQueries queries, ChangeLineQuantity changeLineQuantity) {
        this.visibility = visibility;
        this.queries = queries;
        this.changeLineQuantity = changeLineQuantity;
    }

    public List<PlanSummary> listPlans(Viewer viewer) {
        return queries.summariesOwnedBy(viewer.accountId());
    }

    public PlanDetails getPlan(Viewer viewer, PlanId id) {
        visibility.check(viewer, id);
        return queries.details(id);
    }

    public PlanCosting getCosting(Viewer viewer, PlanId id) {
        visibility.check(viewer, id);
        return queries.costing(id);
    }

    // For screens only, but nothing stops another module or the API calling them.

    public List<PlanListRow> getPlanListRows(Viewer viewer) {
        return queries.listRowsOwnedBy(viewer.accountId());
    }

    public PlanEditorView getPlanEditor(Viewer viewer, PlanId id) {
        visibility.check(viewer, id);
        return queries.editor(id);
    }

    public PlanChange changeLineQuantity(Viewer viewer, ChangeLineQuantityCommand command) {
        visibility.check(viewer, command.planId());
        return changeLineQuantity.handle(command);
    }
}
