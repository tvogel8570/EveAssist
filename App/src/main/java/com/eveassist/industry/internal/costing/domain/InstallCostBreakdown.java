package com.eveassist.industry.internal.costing.domain;

import java.math.BigDecimal;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A job's install cost split into the lines of the game's cost hover: the system cost index line, the structure role
 * bonus, the gross cost, then the taxes and the total. The amounts are exact decimals. The window rounds each shown
 * line to whole ISK and adds the rounded gross cost and taxes, so it can differ from these by up to 1 ISK.
 *
 * @param estimatedItemValue the value the lines are worked out from
 * @param systemCostIndexPercent the index used, in percent
 * @param jobFee the estimated item value times the index
 * @param structureBonus the structure's reduction of the job fee, a negative amount
 * @param grossCost the job fee less the bonus
 * @param facilityTax the facility's tax on the estimated item value
 * @param sccSurcharge the SCC surcharge on the estimated item value
 * @param total the gross cost plus the taxes
 */
@ValueObject
public record InstallCostBreakdown(
        BigDecimal estimatedItemValue, BigDecimal systemCostIndexPercent, BigDecimal jobFee, BigDecimal structureBonus,
        BigDecimal grossCost, BigDecimal facilityTax, BigDecimal sccSurcharge, BigDecimal total) {

    public BigDecimal totalTaxes() {
        return facilityTax.add(sccSurcharge);
    }
}
