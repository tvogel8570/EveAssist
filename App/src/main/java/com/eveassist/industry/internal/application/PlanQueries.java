package com.eveassist.industry.internal.application;

import com.eveassist.identity.Viewer;
import com.eveassist.sde.ItemType;
import com.eveassist.sde.SdeService;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PrimaryPort
@Transactional(readOnly = true)
public class PlanQueries {

    private final PlanRepository plans;
    private final SdeService sde;

    PlanQueries(PlanRepository plans, SdeService sde) {
        this.plans = plans;
        this.sde = sde;
    }

    /** The viewer's active plans. */
    public List<PlanOverview> overviews(Viewer viewer) {
        return overviews(viewer, false);
    }

    /** The viewer's plans; with {@code includeArchived} the archived ones too, after the active ones. */
    public List<PlanOverview> overviews(Viewer viewer, boolean includeArchived) {
        return plans.overviews(viewer, sde.version(), includeArchived);
    }

    public List<ItemType> searchProducts(Viewer viewer, String text) {
        return sde.searchBuildable(text, 12);
    }
}
