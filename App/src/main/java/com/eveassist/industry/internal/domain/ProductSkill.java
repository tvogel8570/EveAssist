package com.eveassist.industry.internal.domain;

import java.math.BigDecimal;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A skill that a blueprint requires for manufacturing and that shortens the job for each level the builder has. The
 * Tech II production skills (Nanite Engineering, Hydromagnetic Physics and the like) are these: the SDE gives each
 * `manufactureTimePerLevel` (attribute 1982), which is 1 percent for 25 skills and 2 for one. Which skills apply to a
 * job is read from the blueprint's required skills (`industryActivitySkills`, manufacturing).
 *
 * @param level the builder's level of the skill, 0 to 5
 * @param percentPerLevel the time saved for each level, in percent
 */
@ValueObject
public record ProductSkill(int level, BigDecimal percentPerLevel) {

    public ProductSkill {
        if (level < 0 || level > 5) {
            throw new IllegalArgumentException("A skill level is from 0 to 5, was " + level);
        }
        if (percentPerLevel == null || percentPerLevel.signum() < 0
                || percentPerLevel.multiply(BigDecimal.valueOf(level)).compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new IllegalArgumentException("A skill must leave some of the time, was " + percentPerLevel);
        }
    }

    /** The share of the time that is left after this skill. */
    BigDecimal factor() {
        return BigDecimal.valueOf(100).subtract(percentPerLevel.multiply(BigDecimal.valueOf(level))).movePointLeft(2);
    }
}
