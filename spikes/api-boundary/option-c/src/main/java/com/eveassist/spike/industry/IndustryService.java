package com.eveassist.spike.industry;

import com.eveassist.spike.identity.Viewer;
import com.eveassist.spike.industry.internal.application.PlanQueries;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Option C: for other modules and the external API only. Pages call the use cases directly, and the use cases check
 * visibility, so this class has nothing to check.
 */
@Service
public class IndustryService {
    private final PlanQueries queries;

    IndustryService(PlanQueries queries) {
        this.queries = queries;
    }

    public List<PlanSummary> listPlans(Viewer viewer) {
        return queries.summaries(viewer);
    }

    public PlanDetails getPlan(Viewer viewer, PlanId id) {
        return queries.details(viewer, id);
    }

    public PlanCosting getCosting(Viewer viewer, PlanId id) {
        return queries.costing(viewer, id);
    }
}
