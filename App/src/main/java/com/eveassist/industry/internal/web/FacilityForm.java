package com.eveassist.industry.internal.web;

import com.eveassist.industry.internal.application.FacilityValues;
import com.eveassist.industry.internal.domain.FacilityTerms;
import com.eveassist.industry.internal.domain.StructureType;
import java.math.BigDecimal;

/**
 * A facility as the form sends it, in whole percents. A percent left empty is 0, except the SCC surcharge (4, which is CCP's rate) and the
 * cost index, which has no sensible default and is refused when it is missing: a facility with no index would cost a job at no index fee.
 * The domain refuses a percent outside 0 to 100 with an IllegalArgumentException.
 */
record FacilityForm(
        String name, String structureType, BigDecimal structureMaterialPercent, BigDecimal rigMaterialPercent,
        BigDecimal structureTimePercent, BigDecimal rigTimePercent, BigDecimal structureCostPercent, BigDecimal systemCostIndexPercent,
        BigDecimal taxPercent, BigDecimal sccSurchargePercent) {

    static final BigDecimal SCC_SURCHARGE = new BigDecimal("4");

    FacilityValues values() {
        if (systemCostIndexPercent == null) {
            throw new IllegalArgumentException("A facility needs its system cost index");
        }
        var terms = new FacilityTerms(orZero(structureMaterialPercent), orZero(rigMaterialPercent), orZero(structureTimePercent),
                orZero(rigTimePercent), orZero(structureCostPercent), systemCostIndexPercent, orZero(taxPercent),
                sccSurchargePercent == null ? SCC_SURCHARGE : sccSurchargePercent);
        return new FacilityValues(name, StructureType.ofKey(structureType).orElse(null), terms);
    }

    private static BigDecimal orZero(BigDecimal percent) {
        return percent == null ? BigDecimal.ZERO : percent;
    }
}
