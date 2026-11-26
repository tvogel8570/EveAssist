package com.eveassist.industry.internal.facility.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.FacilityId;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The facilities page's reads. */
@Service
@PrimaryPort
@Transactional(readOnly = true)
public class FacilityQueries {

    private final FacilityRepository facilities;

    FacilityQueries(FacilityRepository facilities) {
        this.facilities = facilities;
    }

    /** The viewer's facilities by name, each with the number of Draft projects that use it. */
    public List<FacilityListing> list(Viewer viewer) {
        var usage = facilities.draftProjectsUsing(viewer);
        return facilities.list(viewer).stream()
                .map(facility -> new FacilityListing(facility, usage.getOrDefault(facility.id(), 0))).toList();
    }

    public FacilityOverview find(Viewer viewer, FacilityId id) {
        return facilities.find(viewer, id).orElseThrow(FacilityNotFoundException::new);
    }
}
