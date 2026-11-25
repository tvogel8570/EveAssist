package com.eveassist.industry.internal.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.FacilityId;
import com.eveassist.industry.internal.domain.FacilityTerms;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** What a facility does to a job, read for costing. Filters on the viewer, so another account's facility looks missing. */
@SecondaryPort
public interface FacilityTermsLookup {

    Optional<FacilityTerms> termsOf(Viewer viewer, FacilityId id);
}
