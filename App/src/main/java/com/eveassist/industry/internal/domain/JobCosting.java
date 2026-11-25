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
        List<MaterialUse> uses = recipe.materials().stream()
                .map(material -> materialUse(material.material(), material.quantity(), runs, terms))
                .toList();
        List<RecipeMaterial> used = uses.stream()
                .map(use -> new RecipeMaterial(use.material(), use.quantity()))
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
        var install = installCostLines(value, terms.facility());
        return new JobCost(recipe.product(), runs, Math.multiplyExact(runs, recipe.unitsPerRun()), used,
                seconds(recipe.secondsPerRun(), runs, terms), value, install.total(), unpriced, uses,
                timeBreakdown(recipe.secondsPerRun(), runs, terms), install);
    }

    /**
     * What a job of that many runs uses of one material: the base amount less the blueprint's ME and the structure's and
     * rigs' bonuses, rounded once for the whole job, and never less than one for each run. The product is first rounded
     * to two decimals and then up, as the game does: a product of 10,553.004 is 10,553 and not 10,554 (read in the game on
     * 2026-11-18, see the requirements). Rounding the exact product up would give one more in such a case.
     */
    public static long materialQuantity(long baseQuantity, long runs, JobTerms terms) {
        return materialUse(null, baseQuantity, runs, terms).quantity();
    }

    /** The same, with the steps that led to it: the exact product, the two-decimal figure and whether the minimum applied. */
    public static MaterialUse materialUse(TypeId material, long baseQuantity, long runs, JobTerms terms) {
        var factor = reduction(terms.me()).multiply(reduction(terms.facility().structureMaterialPercent()))
                .multiply(reduction(terms.facility().rigMaterialPercent()));
        var exact = BigDecimal.valueOf(baseQuantity).multiply(BigDecimal.valueOf(runs)).multiply(factor);
        var twoDecimals = exact.setScale(2, RoundingMode.HALF_UP);
        long reduced = twoDecimals.setScale(0, RoundingMode.CEILING).longValueExact();
        return new MaterialUse(material, baseQuantity, exact, twoDecimals, Math.max(runs, reduced), reduced < runs);
    }

    /**
     * How long a job of that many runs takes, in whole seconds, rounded to the nearest. The game does not round up: a job
     * that is 9,926.03 s long shows 02:45:26 (read in the game on 2026-11-18, see the requirements).
     */
    public static long seconds(long secondsPerRun, long runs, JobTerms terms) {
        return unroundedSeconds(secondsPerRun, terms).multiply(BigDecimal.valueOf(runs))
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** The longest a job may be, 30 days. Inferred from four readings of the window's "Maximum runs" (see the requirements). */
    public static final long MAXIMUM_JOB_SECONDS = 30L * 24 * 3600;

    /**
     * The most runs one job may have: the smallest number of runs whose time reaches {@link #MAXIMUM_JOB_SECONDS}, worked
     * from the time of one run before it is rounded (489.6 s gave 5,295 in the game, not 5,294). It is never below one, so a
     * single run is allowed even when it takes longer than 30 days. Read for originals only; that it holds for copies, and
     * that one run is always allowed, are the developer's recollection and still to be confirmed in the game.
     */
    public static long maximumRuns(long secondsPerRun, JobTerms terms) {
        var perRun = unroundedSeconds(secondsPerRun, terms);
        return Math.max(1, BigDecimal.valueOf(MAXIMUM_JOB_SECONDS).divide(perRun, 0, RoundingMode.CEILING).longValueExact());
    }

    /**
     * The most runs a job may have and still take no longer than the given seconds, at least one. When one run is already
     * longer, the answer is one and the job is still too long: the caller says so.
     */
    public static long maximumRunsWithin(long secondsPerRun, JobTerms terms, long maxSeconds) {
        long runs = Math.max(1, BigDecimal.valueOf(maxSeconds).divide(unroundedSeconds(secondsPerRun, terms), 0, RoundingMode.FLOOR)
                .longValueExact());
        // seconds() rounds to the nearest, so the estimate can be one run off either way
        while (runs > 1 && seconds(secondsPerRun, runs, terms) > maxSeconds) {
            runs--;
        }
        while (seconds(secondsPerRun, runs + 1, terms) <= maxSeconds) {
            runs++;
        }
        return runs;
    }

    /** The time in the shape of the game's duration hover, with the factors that make it. */
    public static TimeBreakdown timeBreakdown(long secondsPerRun, long runs, JobTerms terms) {
        var facility = terms.facility();
        var exact = unroundedSeconds(secondsPerRun, terms).multiply(BigDecimal.valueOf(runs));
        return new TimeBreakdown(secondsPerRun, runs, reduction(terms.te()), terms.builder().timeFactor(),
                reduction(facility.structureTimePercent()), reduction(facility.rigTimePercent()), exact,
                seconds(secondsPerRun, runs, terms), maximumRuns(secondsPerRun, terms));
    }

    /** The time of one run with every bonus applied, not yet rounded. */
    private static BigDecimal unroundedSeconds(long secondsPerRun, JobTerms terms) {
        var facility = terms.facility();
        var factor = reduction(terms.te()).multiply(terms.builder().timeFactor())
                .multiply(reduction(facility.structureTimePercent())).multiply(reduction(facility.rigTimePercent()));
        return BigDecimal.valueOf(secondsPerRun).multiply(factor);
    }

    /**
     * The job fee is the estimated item value times the system cost index, less the structure's bonus. The facility tax
     * and the SCC surcharge are taken from the whole estimated item value, and the bonus does not touch them.
     */
    public static BigDecimal installCost(BigDecimal estimatedItemValue, FacilityTerms facility) {
        return installCostLines(estimatedItemValue, facility).total();
    }

    /** The install cost in the lines of the game's cost hover. */
    public static InstallCostBreakdown installCostLines(BigDecimal estimatedItemValue, FacilityTerms facility) {
        var jobFee = estimatedItemValue.multiply(facility.systemCostIndexPercent()).movePointLeft(2);
        var gross = jobFee.multiply(reduction(facility.structureCostPercent()));
        var tax = estimatedItemValue.multiply(facility.taxPercent()).movePointLeft(2);
        var scc = estimatedItemValue.multiply(facility.sccSurchargePercent()).movePointLeft(2);
        return new InstallCostBreakdown(estimatedItemValue, facility.systemCostIndexPercent(), jobFee,
                gross.subtract(jobFee), gross, tax, scc, gross.add(tax).add(scc));
    }

    /** The share left after a reduction of that many percent. */
    private static BigDecimal reduction(int percent) {
        return reduction(BigDecimal.valueOf(percent));
    }

    private static BigDecimal reduction(BigDecimal percent) {
        return HUNDRED.subtract(percent).movePointLeft(2);
    }
}
