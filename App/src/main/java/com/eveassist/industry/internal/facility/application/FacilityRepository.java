package com.eveassist.industry.internal.facility.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.FacilityId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** The viewer's facilities. Another account's facility looks missing. */
@SecondaryPort
public interface FacilityRepository {

    /** The viewer's facilities by name. */
    List<FacilityOverview> list(Viewer viewer);

    Optional<FacilityOverview> find(Viewer viewer, FacilityId id);

    /** For each of the viewer's facilities, how many of their Draft projects use it, as the default or for an item. Quoted projects are not counted. */
    Map<FacilityId, Integer> draftProjectsUsing(Viewer viewer);

    FacilityId insert(Viewer viewer, FacilityValues values);

    /**
     * Changes the facility. {@code updatedAt} moves only when a value changed, so saving a form with nothing changed makes no costing out of date,
     * and {@code indexEnteredAt} only when the cost index did. False when there is no such facility.
     */
    boolean update(Viewer viewer, FacilityId id, FacilityValues values);

    /**
     * Deletes the facility, after adding one to the version of every Draft project that uses it (the references are cleared by the database, which
     * would otherwise leave a costing that looks current), and answers how many that was. Empty when there is no such facility.
     */
    Optional<Integer> delete(Viewer viewer, FacilityId id);
}
