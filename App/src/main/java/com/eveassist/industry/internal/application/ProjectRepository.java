package com.eveassist.industry.internal.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.PlanId;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.domain.Project;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/**
 * Every method filters on the viewer in its SQL, so another account's project looks the same as a missing one. Quotes, costings
 * and traces have their own methods when their use cases are built.
 */
@SecondaryPort
public interface ProjectRepository {

    /** Makes an empty Draft with the usual defaults. The plan it starts from may be null, and must be the viewer's. */
    ProjectId create(Viewer viewer, String name, PlanId sourcePlan);

    Optional<Project> find(Viewer viewer, ProjectId id);

    /**
     * Saves the project and its inputs if its version is unchanged, replacing what was stored. Returns the new version, or empty if
     * the version had changed. Throws {@link FacilityNotFoundException} if the project refers to a facility that is not the viewer's.
     */
    Optional<Long> save(Viewer viewer, Project project);

    /**
     * Renames a project, in either state. The name is not an input of the costing, so this is not an edit: the version stays the
     * same and a stored costing stays current. False when the viewer has no such project.
     */
    boolean rename(Viewer viewer, ProjectId id, String name);

    /**
     * Archives or restores a project, in either state. It is a status, not an edit: the version stays the same, so it cannot
     * conflict with someone editing the project. False when the viewer has no such project.
     */
    boolean setArchived(Viewer viewer, ProjectId id, boolean archived);

    /** Deletes the project with everything under it: its inputs, quotes, costings and traces. False when the viewer has none. */
    boolean delete(Viewer viewer, ProjectId id);
}
