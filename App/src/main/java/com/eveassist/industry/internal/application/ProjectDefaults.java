package com.eveassist.industry.internal.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.internal.domain.ProjectBuilder;
import com.eveassist.industry.internal.domain.ProjectConfig;
import com.eveassist.sde.TypeId;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
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

    private final FacilityRepository facilities;
    private final ProjectRecipes recipes;

    ProjectDefaults(ProjectDefaultsRepository defaults, FacilityRepository facilities, ProjectRecipes recipes) {
        this.defaults = defaults;
        this.facilities = facilities;
        this.recipes = recipes;
    }

    /** The defaults with what a page needs to show and change them. */
    @Transactional(readOnly = true)
    public ProjectDefaultsView view(Viewer viewer) {
        var saved = defaults.find(viewer);
        var config = saved.orElseGet(ProjectConfig::defaults);
        var groups = recipes.allTimeSkills().stream()
                .map(skill -> new ProjectDefaultsView.Skill(skill.skill().value(), recipes.name(skill.skill()), skill.percentPerLevel(),
                        config.builder().level(skill.skill())))
                .sorted(Comparator.comparing(ProjectDefaultsView.Skill::name))
                .collect(Collectors.groupingBy(skill -> recipes.groupName(new TypeId(skill.skill())), TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(group -> new ProjectDefaultsView.SkillGroup(group.getKey(), group.getValue()))
                .toList();
        return new ProjectDefaultsView(config, saved.isPresent(), facilities.list(viewer), groups);
    }

    /** The account's defaults: what it saved, or the built-in values. */
    @Transactional(readOnly = true)
    public ProjectConfig of(Viewer viewer) {
        return defaults.find(viewer).orElseGet(ProjectConfig::defaults);
    }

    /** A skill at level 0 is what a skill with no level means, so it is not kept: a new project does not start with a row for every skill there is. */
    public void save(Viewer viewer, ProjectConfig chosen) {
        var builder = chosen.builder();
        var trained = builder.skillLevels().entrySet().stream().filter(skill -> skill.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        defaults.save(viewer, chosen.withBuilder(
                new ProjectBuilder(builder.industry(), builder.advancedIndustry(), builder.implantTimePercent(), trained)));
    }

    /** Returns to the built-in values. */
    public void reset(Viewer viewer) {
        defaults.delete(viewer);
    }
}
