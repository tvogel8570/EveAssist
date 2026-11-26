package com.eveassist.industry.internal.facility.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.FacilityId;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adding, changing and deleting the viewer's facilities. A facility belongs to the account and is shared by its projects. Changing one makes
 * the costing of a Draft that uses it out of date (the project page says so, from the facility's {@code updatedAt}), and a quote already issued
 * keeps the values it was costed with. Deleting one clears it from the Drafts that use it and adds one to their version, so their costing is out
 * of date as well. The last write wins: a facility has no version of its own.
 */
@Service
@PrimaryPort
@Transactional
public class FacilityEditing {

    private final FacilityRepository facilities;

    FacilityEditing(FacilityRepository facilities) {
        this.facilities = facilities;
    }

    public FacilityId add(Viewer viewer, FacilityValues values) {
        return facilities.insert(viewer, values);
    }

    public void change(Viewer viewer, FacilityId id, FacilityValues values) {
        if (!facilities.update(viewer, id, values)) {
            throw new FacilityNotFoundException();
        }
    }

    public FacilityDeleted delete(Viewer viewer, FacilityId id) {
        return new FacilityDeleted(facilities.delete(viewer, id).orElseThrow(FacilityNotFoundException::new));
    }
}
