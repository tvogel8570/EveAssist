package com.eveassist.industry.internal.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.PlanId;
import com.eveassist.industry.internal.domain.Plan;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Every method filters on the viewer in its SQL, so another account's plan looks the same as a missing one. */
@SecondaryPort
public interface PlanRepository {

    PlanId create(Viewer viewer, String name);

    Optional<Plan> find(Viewer viewer, PlanId id);

    /** Saves the plan and its total if its version is unchanged. Returns the new version, or empty if it changed. */
    Optional<Long> save(Viewer viewer, Plan plan, PlanTotal total);

    /**
     * Stores a costing that is not an edit, if the stored one is out of date. The version stays the same, so this cannot
     * conflict with someone editing the plan, and an older result cannot overwrite a newer one.
     */
    void recordCosting(Viewer viewer, Plan plan, PlanTotal total);

    List<PlanOverview> overviews(Viewer viewer, String currentSdeVersion);
}
