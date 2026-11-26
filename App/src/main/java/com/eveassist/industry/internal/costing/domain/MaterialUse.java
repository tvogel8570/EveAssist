package com.eveassist.industry.internal.costing.domain;

import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * How one material's quantity for a job came about, so a difference with the game's window can be traced to a step.
 *
 * @param baseQuantity the recipe's amount for one run at ME 0
 * @param exactProduct base × runs × the ME, structure and rig factors, before any rounding (2,722.005 for a reading where
 *         the game showed 2,723)
 * @param roundedToTwoDecimals the product rounded half up to two decimals, which the game does before rounding up
 * @param quantity what the job uses: the two-decimal figure rounded up, and never below one for each run
 * @param raisedToMinimum true when the one-per-run minimum, not the reduced amount, set the quantity
 */
@ValueObject
public record MaterialUse(
        TypeId material, long baseQuantity, BigDecimal exactProduct, BigDecimal roundedToTwoDecimals, long quantity,
        boolean raisedToMinimum) {
}
