package com.eveassist.industry.internal.costing.domain;

import java.math.BigDecimal;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * What the builder's character does to the time of a manufacturing job. Skills and implants change time, never the
 * materials. The Industry skill takes 4% off per level and Advanced Industry 3% per level, and the skills that the
 * blueprint requires (see {@link ProductSkill}) take their own share. They all multiply: the game shows them as one
 * "Skills and Implants" line, and Industry V, Advanced Industry V and two Tech II skills at V gave 38.6%, which is
 * 1 - 0.80 x 0.85 x 0.95 x 0.95.
 *
 * @param industry the Industry skill level, 0 to 5
 * @param advancedIndustry the Advanced Industry skill level, 0 to 5
 * @param productSkills the other skills that this job's blueprint requires and that save time
 * @param implantTimePercent the manufacturing time reduction of the stored implants, in percent
 */
@ValueObject
public record BuilderTerms(
        int industry, int advancedIndustry, List<ProductSkill> productSkills, BigDecimal implantTimePercent) {

    /** A builder with no skills and no implants, which is what a plan assumes. */
    public static final BuilderTerms NONE = new BuilderTerms(0, 0, BigDecimal.ZERO);

    /** A builder whose job's blueprint requires no skill that saves time besides Industry, as for most Tech I items. */
    public BuilderTerms(int industry, int advancedIndustry, BigDecimal implantTimePercent) {
        this(industry, advancedIndustry, List.of(), implantTimePercent);
    }

    public BuilderTerms {
        productSkills = List.copyOf(productSkills);
        if (industry < 0 || industry > 5 || advancedIndustry < 0 || advancedIndustry > 5) {
            throw new IllegalArgumentException("A skill level is from 0 to 5");
        }
        if (implantTimePercent == null || implantTimePercent.signum() < 0
                || implantTimePercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("The implant reduction must be from 0 to 100, was " + implantTimePercent);
        }
    }

    /**
     * The share of the time that is left after the skills and the implants, which is what the game's "Skills and Implants"
     * line shows (as one minus this). The implant multiplies with the skills: Industry V, Advanced Industry V and a
     * BX-804 (4%) gave 34.7%, which is 1 - 0.68 x 0.96, where adding them would have shown 36.0%.
     */
    BigDecimal timeFactor() {
        return skillFactor().multiply(BigDecimal.valueOf(100).subtract(implantTimePercent).movePointLeft(2));
    }

    /** The share of the time that is left after the skills alone, 1 meaning none is saved. */
    BigDecimal skillFactor() {
        var factor = BigDecimal.valueOf(100 - 4L * industry).movePointLeft(2)
                .multiply(BigDecimal.valueOf(100 - 3L * advancedIndustry).movePointLeft(2));
        for (ProductSkill skill : productSkills) {
            factor = factor.multiply(skill.factor());
        }
        return factor;
    }
}
