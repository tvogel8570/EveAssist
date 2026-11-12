package com.eveassist.industry.internal.domain;

import com.eveassist.sde.Recipe;
import com.eveassist.sde.RecipeMaterial;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Works out what a plan needs. Anything with a blueprint is built unless the plan buys it, and everything else is
 * bought. Every blueprint is taken at {@link #ME} and {@link #TE}, with no skills, facilities, taxes or surplus credit
 * yet. Demand for an item is added up across the plan before its runs are rounded up, so shared intermediates are
 * built together.
 */
public final class Costing {

    /** The material efficiency a plan assumes for every blueprint, in percent. */
    public static final int ME = 10;
    /** The time efficiency a plan assumes for every blueprint, in percent. */
    public static final int TE = 20;

    private final Function<TypeId, Optional<Recipe>> recipes;
    private final Function<TypeId, Optional<BigDecimal>> prices;

    public Costing(Function<TypeId, Optional<Recipe>> recipes, Function<TypeId, Optional<BigDecimal>> prices) {
        this.recipes = recipes;
        this.prices = prices;
    }

    public PlanCost cost(List<PlanProduct> products) {
        return cost(products, Set.of());
    }

    /** A product is always built, even if it is also a part that the plan buys elsewhere. */
    public PlanCost cost(List<PlanProduct> products, Set<TypeId> boughtParts) {
        var bought = new HashSet<>(boughtParts);
        products.forEach(product -> bought.remove(product.type()));
        Map<TypeId, Long> demand = new LinkedHashMap<>();
        products.forEach(product -> demand.merge(product.type(), product.quantity(), Long::sum));
        List<LineCost> lines = new ArrayList<>();
        var total = BigDecimal.ZERO;
        int unpriced = 0;
        for (TypeId type : parentsFirst(products, bought)) {
            long quantity = demand.get(type);
            var recipe = recipes.apply(type);
            if (recipe.isPresent() && !bought.contains(type)) {
                long runs = Math.ceilDiv(quantity, recipe.get().unitsPerRun());
                for (RecipeMaterial material : recipe.get().materials()) {
                    demand.merge(material.material(), materialsNeeded(material.quantity(), runs), Long::sum);
                }
                lines.add(new LineCost(type, quantity, true, true, null, null, runs,
                        Math.multiplyExact(runs, recipe.get().unitsPerRun()), secondsNeeded(recipe.get(), runs)));
            } else {
                var price = prices.apply(type);
                var cost = price.map(unit -> unit.multiply(BigDecimal.valueOf(quantity))).orElse(null);
                if (cost == null) {
                    unpriced++;
                } else {
                    total = total.add(cost);
                }
                lines.add(LineCost.bought(type, quantity, recipe.isPresent(), price.orElse(null), cost));
            }
        }
        return new PlanCost(lines, total, unpriced);
    }

    /**
     * What a job of that many runs uses of one material: the base amount less ME, rounded up once for the whole job, and
     * never less than one for each run.
     */
    static long materialsNeeded(long baseQuantity, long runs) {
        long reduced = Math.ceilDiv(Math.multiplyExact(Math.multiplyExact(baseQuantity, runs), 100L - ME), 100L);
        return Math.max(runs, reduced);
    }

    /** How long a job of that many runs takes at {@link #TE}, in whole seconds. */
    static long secondsNeeded(Recipe recipe, long runs) {
        return Math.ceilDiv(Math.multiplyExact(Math.multiplyExact(recipe.secondsPerRun(), runs), 100L - TE), 100L);
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
