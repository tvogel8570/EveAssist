package com.eveassist.industry.internal.project.defaults.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.internal.project.domain.ProjectConfig;
import com.eveassist.industry.internal.facility.application.FacilityNotFoundException;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** The project defaults the viewer's account saved. Every method is about the viewer's own, there is no way to name another account's. */
@SecondaryPort
public interface ProjectDefaultsRepository {

    /** What the account saved, or empty when it saved nothing. A default facility that has been deleted since is none. */
    Optional<ProjectConfig> find(Viewer viewer);

    /**
     * Saves the defaults, replacing what the account had, the skill levels included. A buyer or a note is not kept. Throws
     * {@link FacilityNotFoundException} for a default facility that is not the viewer's.
     */
    void save(Viewer viewer, ProjectConfig defaults);

    /** Forgets what the account saved. Nothing happens when it saved nothing. */
    void delete(Viewer viewer);
}
