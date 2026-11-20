package com.eveassist.industry.internal.application;

import com.eveassist.identity.Viewer;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** The viewer's facilities. Every method filters on the viewer in its SQL. Adding, changing and deleting come with the facility use cases. */
@SecondaryPort
public interface FacilityRepository {

    /** The viewer's facilities by name. */
    List<FacilityOverview> list(Viewer viewer);
}
