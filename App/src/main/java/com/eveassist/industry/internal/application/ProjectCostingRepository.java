package com.eveassist.industry.internal.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.ProjectId;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/**
 * The Draft's latest costing and its trace. Every method filters on the viewer in its SQL, so another account's project looks the same
 * as a missing one. A quote's costing and trace have their own methods when the quote use cases are built.
 */
@SecondaryPort
public interface ProjectCostingRepository {

    enum SaveOutcome {
        /** The costing and its trace replaced what the Draft had. */
        SAVED,
        /** The Draft already has a costing for a later edit of the project, which an older one must not overwrite. */
        STALE,
        /** The project is quoted, so it has no Draft costing to replace. */
        LOCKED,
        /** The viewer has no such project. */
        NOT_FOUND
    }

    /**
     * Replaces the Draft's costing and its trace in one transaction. A costing for the same edit of the project as the stored one
     * replaces it (costing again with newer prices), and one for an earlier edit is refused, so a slow costing cannot overwrite a
     * newer one.
     */
    SaveOutcome saveDraft(Viewer viewer, ProjectId id, StoredCosting costing, TraceDocumentV1 trace);

    Optional<StoredCosting> findDraft(Viewer viewer, ProjectId id);

    /** The trace of the Draft's latest costing. It is kept apart because it is large. */
    Optional<TraceDocumentV1> findDraftTrace(Viewer viewer, ProjectId id);
}
