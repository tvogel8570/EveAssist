package com.eveassist.industry.internal.domain;

import com.eveassist.sde.Recipe;
import com.eveassist.sde.RecipeMaterial;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The arithmetic of one manufacturing job. Every reduction multiplies the others, nothing is added together, and the
 * result is rounded up once for the whole job. The sums are exact decimals, so no floating-point noise creeps in. The material quantity is the exception that the
 * game makes: it rounds to two decimals before it rounds up.
 *
 * <p>The rules here were checked against jobs read in the game on 2026-11-18 (see the requirements, Costing components).
 * Rig scaling, the facility tax, invention and copying have not been read in the game yet.
 */
public final class JobCosting {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private JobCosting() {
    }

    public static JobCost cost(
            Recipe recipe, long runs, JobTerms terms, Function<TypeId, Optional<BigDecimal>> adjustedPrices) {
        if (runs < 1) {
            throw new IllegalArgumentException("A job has at least one run, was " + runs);
        }
        List<RecipeMaterial> used = recipe.materials().stream()
                .map(material -> new RecipeMaterial(material.material(),
                        materialQuantity(material.quantity(), runs, terms)))
                .toList();
        var value = BigDecimal.ZERO;
        int unpriced = 0;
        for (RecipeMaterial material : recipe.materials()) {
            var price = adjustedPrices.apply(material.material());
            if (price.isPresent()) {
                value = value.add(price.get().multiply(BigDecimal.valueOf(material.quantity()))
                        .multiply(BigDecimal.valueOf(runs)));
            } else {
                unpriced++;
            }
        }
        return new JobCost(recipe.product(), runs, Math.multiplyExact(runs, recipe.unitsPerRun()), used,
                seconds(recipe.secondsPerRun(), runs, terms), value, installCost(value, terms.facility()), unpriced);
    }

    /**
     * What a job of that many runs uses of one material: the base amount less the blueprint's ME and the structure's and
     * rigs' bonuses, rounded once for the whole job, and never less than one for each run. The product is first rounded
     * to two decimals and then up, as the game does: a product of 10,553.004 is 10,553 and not 10,554 (read in the game on
     * 2026-11-18, see the requirements). Rounding the exact product up would give one more in such a case.
     */
    public static long materialQuantity(long baseQuantity, long runs, JobTerms terms) {
        var factor = reduction(terms.me()).multiply(reduction(terms.facility().structureMaterialPercent()))
                .multiply(reduction(terms.facility().rigMaterialPercent()));
        long reduced = BigDecimal.valueOf(baseQuantity).multiply(BigDecimal.valueOf(runs)).multiply(factor)
                .setScale(2, RoundingMode.HALF_UP).setScale(0, RoundingMode.CEILING).longValueExact();
        return Math.max(runs, reduced);
    }

    /**
     * How long a job of that many runs takes, in whole seconds, rounded to the nearest. The game does not round up: a job
     * that is 9,926.03 s long shows 02:45:26 (read in the game on 2026-11-18, see the requirements).
     */
    public static long seconds(long secondsPerRun, long runs, JobTerms terms) {
        var facility = terms.facility();
        var factor = reduction(terms.te()).multiply(terms.builder().timeFactor())
                .multiply(reduction(facility.structureTimePercent())).multiply(reduction(facility.rigTimePercent()));
        return BigDecimal.valueOf(secondsPerRun).multiply(BigDecimal.valueOf(runs)).multiply(factor)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * The job fee is the estimated item value times the system cost index, less the structure's bonus. The facility tax
     * and the SCC surcharge are taken from the whole estimated item value, and the bonus does not touch them.
     */
    public static BigDecimal installCost(BigDecimal estimatedItemValue, FacilityTerms facility) {
        var fee = estimatedItemValue.multiply(facility.systemCostIndexPercent()).movePointLeft(2)
                .multiply(reduction(facility.structureCostPercent()));
        var taxes = estimatedItemValue.multiply(facility.taxPercent().add(facility.sccSurchargePercent()))
                .movePointLeft(2);
        return fee.add(taxes);
    }

    /** The share left after a reduction of that many percent. */
    private static BigDecimal reduction(int percent) {
        return reduction(BigDecimal.valueOf(percent));
    }

    private static BigDecimal reduction(BigDecimal percent) {
        return HUNDRED.subtract(percent).movePointLeft(2);
    }
}
