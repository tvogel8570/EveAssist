package com.eveassist.industry.internal.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.internal.domain.ProjectConfig;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An account's project defaults: what a new project starts from. Every setting of a project is one, except the buyer and the note
 * (requirements/projects.md, Project defaults). A project owns its values once started, so changing a default reaches no project that exists.
 */
@Service
@PrimaryPort
@Transactional
public class ProjectDefaults {

    private final ProjectDefaultsRepository defaults;

    ProjectDefaults(ProjectDefaultsRepository defaults) {
        this.defaults = defaults;
    }

    /** The account's defaults: what it saved, or the built-in values. */
    @Transactional(readOnly = true)
    public ProjectConfig of(Viewer viewer) {
        return defaults.find(viewer).orElseGet(ProjectConfig::defaults);
    }

    public void save(Viewer viewer, ProjectConfig chosen) {
        defaults.save(viewer, chosen);
    }

    /** Returns to the built-in values. */
    public void reset(Viewer viewer) {
        defaults.delete(viewer);
    }
}
