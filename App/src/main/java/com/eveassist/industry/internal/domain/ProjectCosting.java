package com.eveassist.industry.internal.domain;

import com.eveassist.industry.internal.domain.CostLine.Component;
import com.eveassist.industry.internal.domain.ProjectSettings.QuoteMode;
import com.eveassist.industry.internal.domain.ProjectWarning.Kind;
import com.eveassist.sde.Recipe;
import com.eveassist.sde.RecipeMaterial;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Costs a project: the jobs of every built item through {@link JobCosting}, then the items bought, the blueprints, the
 * builder's time, hauling, the contract fee and the surplus, ending in a delivered cost and a quote. It reads nothing:
 * recipes, prices and volumes arrive as functions, as in {@link Costing}.
 *
 * <p>Parents come before their parts, so an item's demand is complete when it is reached: it is the sum of what every
 * job above it uses, after those jobs' own rounding. The surplus is the units the jobs make beyond that demand.
 */
public final class ProjectCosting {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 6;

    private final Function<TypeId, Optional<Recipe>> recipes;
    private final Function<TypeId, Optional<BigDecimal>> prices;
    private final Function<TypeId, Optional<BigDecimal>> adjustedPrices;
    private final Function<TypeId, Optional<BigDecimal>> volumes;

    /**
     * @param prices the unit price at the project's price basis, used for bought items, the surplus and the market quote
     * @param adjustedPrices ESI's adjusted prices, for each job's estimated item value
     * @param volumes the volume of one unit in cubic meters, the packaged volume where there is one
     */
    public ProjectCosting(
            Function<TypeId, Optional<Recipe>> recipes, Function<TypeId, Optional<BigDecimal>> prices,
            Function<TypeId, Optional<BigDecimal>> adjustedPrices, Function<TypeId, Optional<BigDecimal>> volumes) {
        this.recipes = recipes;
        this.prices = prices;
        this.adjustedPrices = adjustedPrices;
        this.volumes = volumes;
    }

    public ProjectCost cost(ProjectDefinition project) {
        var settings = project.settings();
        var bought = new HashSet<>(project.boughtParts());
        project.products().forEach(product -> bought.remove(product.type()));
        Map<TypeId, Long> demand = new LinkedHashMap<>();
        project.products().forEach(product -> demand.merge(product.type(), product.quantity(), Long::sum));

        Set<ProjectWarning> warnings = new LinkedHashSet<>();
        List<ProjectJobCost> jobs = new ArrayList<>();
        List<ItemDemand> demands = new ArrayList<>();
        List<BoughtMaterial> boughtItems = new ArrayList<>();
        var materials = BigDecimal.ZERO;
        var install = BigDecimal.ZERO;
        var blueprints = BigDecimal.ZERO;
        var surplusCredit = BigDecimal.ZERO;
        var inboundVolume = BigDecimal.ZERO;
        long seconds = 0;
        long longestJob = 0;

        for (TypeId type : parentsFirst(project.products(), bought)) {
            long needed = demand.get(type);
            var recipe = recipes.apply(type);
            if (recipe.isPresent() && !bought.contains(type)) {
                var build = project.builds().get(type);
                if (build == null) {
                    throw new IllegalArgumentException("The project has no blueprint choice for item " + type.value());
                }
                long runsNeeded = Math.ceilDiv(needed, recipe.get().unitsPerRun());
                long unitsMade = 0;
                int jobCount = 0;
                for (ProjectJob planned : build.jobsFor(recipe.get(), runsNeeded)) {
                    var job = JobCosting.cost(recipe.get(), planned.runs(), planned.terms(), adjustedPrices);
                    var blueprintCost = planned.blueprintCostPerRun().multiply(BigDecimal.valueOf(planned.runs()));
                    jobs.add(new ProjectJobCost(job, blueprintCost, planned.terms(), planned.kind()));
                    jobCount++;
                    for (RecipeMaterial material : job.materials()) {
                        demand.merge(material.material(), material.quantity(), Long::sum);
                    }
                    // the notice names the material that has no adjusted price, and the set keeps it once however many jobs use it
                    for (RecipeMaterial material : job.materials()) {
                        if (adjustedPrices.apply(material.material()).isEmpty()) {
                            warnings.add(new ProjectWarning(Kind.MISSING_ADJUSTED_PRICE, material.material()));
                        }
                    }
                    if (build.timeBound().isPresent() && job.seconds() > build.timeBound().getAsLong()) {
                        warnings.add(new ProjectWarning(Kind.TIME_BOUND_NOT_MET, type));
                    }
                    if (planned.kind() == BlueprintKind.ORIGINAL) {
                        warnings.add(new ProjectWarning(Kind.USES_ORIGINAL, type));
                    }
                    install = install.add(job.installCost());
                    blueprints = blueprints.add(blueprintCost);
                    seconds += job.seconds();
                    longestJob = Math.max(longestJob, job.seconds());
                    unitsMade += job.unitsMade();
                }
                demands.add(new ItemDemand(type, needed, runsNeeded, jobCount, unitsMade));
                if (unitsMade < needed) {
                    warnings.add(new ProjectWarning(Kind.TOO_FEW_RUNS, type));
                } else if (unitsMade > needed) {
                    var price = prices.apply(type);
                    if (price.isPresent()) {
                        surplusCredit = surplusCredit.add(price.get().multiply(BigDecimal.valueOf(unitsMade - needed)));
                    } else {
                        warnings.add(new ProjectWarning(Kind.UNPRICED_SURPLUS, type));
                    }
                }
            } else {
                var price = prices.apply(type);
                var cost = price.map(unit -> unit.multiply(BigDecimal.valueOf(needed))).orElse(null);
                if (cost == null) {
                    warnings.add(new ProjectWarning(Kind.UNPRICED_MATERIAL, type));
                } else {
                    materials = materials.add(cost);
                }
                boughtItems.add(new BoughtMaterial(type, needed, price.orElse(null), cost));
                inboundVolume = inboundVolume.add(volumeOf(type, needed, settings, warnings));
            }
        }

        // Only what the buyer gets is carried out: the surplus stays with the builder and is credited, not hauled.
        var outboundVolume = BigDecimal.ZERO;
        for (PlanProduct product : project.products()) {
            outboundVolume = outboundVolume.add(volumeOf(product.type(), product.quantity(), settings, warnings));
        }

        var timeValue = BigDecimal.valueOf(seconds).multiply(settings.iskPerHour())
                .divide(BigDecimal.valueOf(3600), SCALE, RoundingMode.HALF_UP);
        var hauling = inboundVolume.add(outboundVolume).multiply(settings.iskPerCubicMeter());
        List<CostLine> lines = List.of(
                new CostLine(Component.MATERIALS, materials), new CostLine(Component.INSTALL_COST, install),
                new CostLine(Component.BLUEPRINT, blueprints), new CostLine(Component.TIME_VALUE, timeValue),
                new CostLine(Component.HAULING, hauling), new CostLine(Component.CONTRACT_FEE, settings.contractFee()),
                new CostLine(Component.SURPLUS_CREDIT, surplusCredit.negate()));
        var delivered = lines.stream().map(CostLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long units = project.products().stream().mapToLong(PlanProduct::quantity).sum();
        var perUnit = units == 0 ? BigDecimal.ZERO : delivered.divide(BigDecimal.valueOf(units), SCALE, RoundingMode.HALF_UP);

        // a market-referenced quote is the products' Jita prices, so a product with none leaves no quote. That is said and not left silent
        if (settings.mode() == QuoteMode.MARKET) {
            for (PlanProduct product : project.products()) {
                if (prices.apply(product.type()).isEmpty()) {
                    warnings.add(new ProjectWarning(Kind.NO_MARKET_PRICE, product.type()));
                }
            }
        }
        var margin = quote(delivered, delivered.multiply(HUNDRED)
                .divide(HUNDRED.subtract(settings.marginPercent()), SCALE, RoundingMode.HALF_UP), units, settings);
        var market = marketPrice(project.products(), settings)
                .map(price -> quote(delivered, price, units, settings)).orElse(null);
        boolean marginMode = settings.mode() == QuoteMode.MARGIN;
        return new ProjectCost(jobs, boughtItems, lines, delivered, perUnit, seconds, longestJob, new ArrayList<>(warnings),
                marginMode ? margin : market, marginMode ? market : margin, demands);
    }

    /**
     * Costs the project under its own split policies and again at least cost, and reports what the difference costs and
     * what it buys in time. Builds that do not split are the same in both, so they add nothing to the difference.
     */
    public SplitComparison compareToLeastCost(ProjectDefinition project) {
        Map<TypeId, ProjectBuild> baseline = new LinkedHashMap<>();
        project.builds().forEach((type, build) -> baseline.put(type, build.withPolicy(SplitPolicy.LEAST_COST)));
        var chosen = cost(project);
        var leastCost = cost(new ProjectDefinition(project.products(), project.boughtParts(), baseline, project.settings()));
        List<CostLine> deltas = new ArrayList<>();
        for (Component component : Component.values()) {
            deltas.add(new CostLine(component, chosen.amount(component).subtract(leastCost.amount(component))));
        }
        var delivered = chosen.deliveredCost().subtract(leastCost.deliveredCost());
        long saved = leastCost.longestJobSeconds() - chosen.longestJobSeconds();
        var perHour = saved > 0 ? delivered.multiply(BigDecimal.valueOf(3600))
                .divide(BigDecimal.valueOf(saved), SCALE, RoundingMode.HALF_UP) : null;
        return new SplitComparison(chosen, leastCost, deltas, delivered, chosen.totalSeconds() - leastCost.totalSeconds(),
                saved, perHour);
    }

    private BigDecimal volumeOf(TypeId type, long quantity, ProjectSettings settings, Set<ProjectWarning> warnings) {
        if (settings.iskPerCubicMeter().signum() == 0) {
            return BigDecimal.ZERO;
        }
        var volume = volumes.apply(type);
        if (volume.isEmpty()) {
            warnings.add(new ProjectWarning(Kind.MISSING_VOLUME, type));
            return BigDecimal.ZERO;
        }
        return volume.get().multiply(BigDecimal.valueOf(quantity));
    }

    /** The market price of everything delivered with the premium on it, or empty when a product has no price. */
    private Optional<BigDecimal> marketPrice(List<PlanProduct> products, ProjectSettings settings) {
        var total = BigDecimal.ZERO;
        for (PlanProduct product : products) {
            var price = prices.apply(product.type());
            if (price.isEmpty()) {
                return Optional.empty();
            }
            total = total.add(price.get().multiply(BigDecimal.valueOf(product.quantity())));
        }
        return Optional.of(total.multiply(HUNDRED.add(settings.premiumPercent())).movePointLeft(2));
    }

    /** The price is rounded up to the quote step, so the profit is never below what the price formula gave. */
    private static ProjectQuote quote(BigDecimal delivered, BigDecimal exactPrice, long units, ProjectSettings settings) {
        var step = settings.quoteStep();
        var price = step.signum() == 0 ? exactPrice.setScale(2, RoundingMode.HALF_UP)
                : exactPrice.divide(step, 0, RoundingMode.CEILING).multiply(step);
        var profit = price.subtract(delivered);
        var realized = price.signum() == 0 ? BigDecimal.ZERO
                : profit.multiply(HUNDRED).divide(price, SCALE, RoundingMode.HALF_UP);
        var perUnit = units == 0 ? BigDecimal.ZERO : price.divide(BigDecimal.valueOf(units), SCALE, RoundingMode.HALF_UP);
        return new ProjectQuote(price, perUnit, profit, realized);
    }

    /** Every item's demand is complete by the time it is reached, because all that use it come earlier. */
    private List<TypeId> parentsFirst(List<PlanProduct> products, Set<TypeId> bought) {
        List<TypeId> finished = new ArrayList<>();
        Set<TypeId> seen = new HashSet<>();
        products.forEach(product -> visit(product.type(), bought, seen, finished));
        Collections.reverse(finished);
        return finished;
    }

    private void visit(TypeId type, Set<TypeId> bought, Set<TypeId> seen, List<TypeId> finished) {
        if (seen.add(type)) {
            if (!bought.contains(type)) {
                recipes.apply(type).ifPresent(recipe -> recipe.materials()
                        .forEach(material -> visit(material.material(), bought, seen, finished)));
            }
            finished.add(type);
        }
    }
}
