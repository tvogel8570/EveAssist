package com.eveassist.industry.internal.application;

import com.eveassist.industry.internal.domain.ProjectConfig;
import com.eveassist.industry.internal.facility.application.FacilityOverview;
import java.math.BigDecimal;
import java.util.List;

/**
 * What the project defaults page shows.
 *
 * @param config the account's defaults: what it saved, or the built-in values
 * @param saved whether the account saved defaults
 * @param facilities the account's facilities, to choose the default one from
 * @param skillGroups every skill that shortens the time of some blueprint, under the game's group for it, with the account's level
 */
public record ProjectDefaultsView(ProjectConfig config, boolean saved, List<FacilityOverview> facilities, List<SkillGroup> skillGroups) {

    public ProjectDefaultsView {
        facilities = List.copyOf(facilities);
        skillGroups = List.copyOf(skillGroups);
    }

    public record SkillGroup(String name, List<Skill> skills) {

        public SkillGroup {
            skills = List.copyOf(skills);
        }
    }

    public record Skill(int skill, String name, BigDecimal percentPerLevel, int level) {
    }

    public long skillCount() {
        return skillGroups.stream().mapToLong(group -> group.skills().size()).sum();
    }

    /** How many skills have a level above 0. */
    public long skillsSet() {
        return skillGroups.stream().flatMap(group -> group.skills().stream()).filter(skill -> skill.level() > 0).count();
    }
}
