package com.eveassist.industry.internal.domain;

import java.math.BigDecimal;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The builder's levels that apply to every job of a project: the two skills every blueprint uses, and the implant. The skills that
 * only some blueprints require are kept for each item (see {@link ProjectItem}).
 *
 * @param implantTimePercent the manufacturing time reduction of the stored implants, in percent
 */
@ValueObject
public record ProjectBuilder(int industry, int advancedIndustry, BigDecimal implantTimePercent) {

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
    }
}
