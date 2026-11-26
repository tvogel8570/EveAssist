package com.eveassist.industry.internal.costing.domain;

import java.math.BigDecimal;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * What a facility does to one manufacturing job, already worked out from its structure, rigs and system. All amounts
 * are percent (1.5 means 1.5%), and the system cost index is percent too (ESI gives 0.0321 for 3.21%, so convert it).
 * The structure and rig bonuses are separate because they multiply and do not add. Which structure and rigs give which
 * bonus is not decided here: that table changes with patches and is kept apart (see requirements, Projects).
 *
 * @param structureMaterialPercent the structure's material reduction
 * @param rigMaterialPercent the rigs' material reduction, already scaled for the system's security
 * @param structureTimePercent the structure's time reduction
 * @param rigTimePercent the rigs' time reduction, already scaled for the system's security
 * @param structureCostPercent the structure's reduction of the job fee (the system cost index part, not the taxes)
 * @param systemCostIndexPercent the system's cost index for this activity
 * @param taxPercent the facility's tax
 * @param sccSurchargePercent the SCC surcharge, a rate CCP sets, so it is given and not fixed in code
 */
@ValueObject
public record FacilityTerms(
        BigDecimal structureMaterialPercent, BigDecimal rigMaterialPercent,
        BigDecimal structureTimePercent, BigDecimal rigTimePercent,
        BigDecimal structureCostPercent, BigDecimal systemCostIndexPercent,
        BigDecimal taxPercent, BigDecimal sccSurchargePercent) {

    /** A facility with no bonuses, no index and no taxes, which is what a plan assumes. */
    public static final FacilityTerms NONE = new FacilityTerms(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    public FacilityTerms {
        for (BigDecimal percent : new BigDecimal[] {structureMaterialPercent, rigMaterialPercent, structureTimePercent,
                rigTimePercent, structureCostPercent, systemCostIndexPercent, taxPercent, sccSurchargePercent}) {
            if (percent == null || percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("A facility percentage must be from 0 to 100, was " + percent);
            }
        }
    }
}
