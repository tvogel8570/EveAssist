package com.eveassist.industry.internal.project.domain;

import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.util.Map;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Who does a project's work, as far as the time of its jobs depends on it: the two skills every blueprint uses, the implant, and
 * the levels in the time-saving skills that only some blueprints require. One builder serves the whole project, so each skill has
 * one level however many items need it.
 *
 * @param implantTimePercent the manufacturing time reduction of the stored implants, in percent
 * @param skillLevels the level in each time-saving skill that some blueprint requires, by skill. A skill that is not here is at level 0
 */
@ValueObject
public record ProjectBuilder(int industry, int advancedIndustry, BigDecimal implantTimePercent, Map<TypeId, Integer> skillLevels) {

    /** No skills and no implant, which is where a new project starts. */
    public static final ProjectBuilder NONE = new ProjectBuilder(0, 0, BigDecimal.ZERO);

    public ProjectBuilder {
        if (industry < 0 || industry > 5 || advancedIndustry < 0 || advancedIndustry > 5) {
            throw new IllegalArgumentException("A skill level is from 0 to 5");
        }
        if (implantTimePercent == null || implantTimePercent.signum() < 0
                || implantTimePercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("The implant reduction must be from 0 to 100, was " + implantTimePercent);
        }
        skillLevels = Map.copyOf(skillLevels);
        if (skillLevels.values().stream().anyMatch(level -> level < 0 || level > 5)) {
            throw new IllegalArgumentException("A skill level is from 0 to 5");
        }
    }

    /** A builder with no levels in the skills that only some blueprints require. */
    public ProjectBuilder(int industry, int advancedIndustry, BigDecimal implantTimePercent) {
        this(industry, advancedIndustry, implantTimePercent, Map.of());
    }

    public int level(TypeId skill) {
        return skillLevels.getOrDefault(skill, 0);
    }
}
