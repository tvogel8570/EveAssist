package archfixture.clean.industry.internal.facility.application;

import archfixture.clean.identity.Viewer;
import archfixture.clean.industry.internal.facility.domain.Facility;

public class FacilityQueries {
    public Facility find(Viewer viewer) {
        return new Facility();
    }
}
