package com.eveassist.industry.internal.application;

import com.eveassist.industry.internal.domain.Costing;
import com.eveassist.industry.internal.domain.LineCost;
import com.eveassist.industry.internal.domain.Plan;
import com.eveassist.industry.internal.domain.PlanCost;
import com.eveassist.industry.internal.domain.PlanProduct;
import com.eveassist.market.JitaPrice;
import com.eveassist.market.MarketService;
import com.eveassist.sde.SdeService;
import com.eveassist.sde.TypeId;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Gathers what costing needs from other modules, once, so that an edit costs twice with the same inputs. */
@Component
class PlanCostings {

    private final SdeService sde;
    private final MarketService market;

    PlanCostings(SdeService sde, MarketService market) {
        this.sde = sde;
        this.market = market;
    }

    /**
     * Prices are read for everything the products could need, bought or built, because an edit can change which. The
     * products given must include any the edit adds.
     */
    Inputs inputs(List<PlanProduct> products) {
        var everything = new Costing(sde::recipeFor, type -> Optional.empty()).cost(products);
        var prices = market.pricesFor(everything.lines().stream().map(LineCost::type).toList());
        return new Inputs(
                new Costing(sde::recipeFor, type -> Optional.ofNullable(prices.get(type)).map(JitaPrice::sell)),
                oldest(prices));
    }

    /** A plan's products have their own table, so the part and material lists leave them out. */
    PlanDetails details(Plan plan, long version, Inputs inputs) {
        return details(plan, version, inputs, inputs.cost(plan));
    }

    /** For a caller that has already costed the plan, so that it is not costed twice. */
    PlanDetails details(Plan plan, long version, Inputs inputs, PlanCost cost) {
        var lines = cost.lines().stream()
                .filter(line -> !plan.hasProduct(line.type()))
                .map(this::describe)
                .toList();
        return new PlanDetails(plan.id(), plan.name(), version,
                plan.products().stream().map(this::describe).sorted(Comparator.comparing(PlanProductLine::name)).toList(),
                lines.stream().filter(line -> !line.buildable())
                        .sorted(Comparator.comparing(PlanLineCosting::cost,
                                Comparator.nullsFirst(Comparator.reverseOrder()))
                                .thenComparing(PlanLineCosting::name)).toList(),
                lines.stream().filter(PlanLineCosting::buildable)
                        .sorted(Comparator.comparing(PlanLineCosting::name)).toList(),
                cost.total(), cost.unpricedLines(), inputs.pricesAsOf(), sde.version());
    }

    PlanLineCosting describe(LineCost line) {
        var type = sde.itemType(line.type());
        return new PlanLineCosting(line.type().value(),
                type.map(item -> item.name()).orElse("#" + line.type().value()),
                type.map(item -> item.groupName()).orElse(""),
                line.quantity(), line.buildable(), line.built(), line.unitPrice(), line.cost());
    }

    PlanProductLine describe(PlanProduct product) {
        var name = sde.itemType(product.type()).map(item -> item.name()).orElse("#" + product.type().value());
        return new PlanProductLine(product.type().value(), name, product.quantity());
    }

    PlanTotal total(PlanCost cost, Inputs inputs) {
        var priced = cost.lines().stream().filter(line -> !line.built()).map(LineCost::type)
                .collect(Collectors.toUnmodifiableSet());
        return new PlanTotal(cost.total(), sde.version(), inputs.pricesAsOf(), priced);
    }

    /** Only an item with a blueprint can be a product. */
    boolean isBuildable(TypeId type) {
        return sde.recipeFor(type).isPresent();
    }

    String sdeVersion() {
        return sde.version();
    }

    private static Instant oldest(Map<TypeId, JitaPrice> prices) {
        return prices.values().stream().map(JitaPrice::asOf).min(Instant::compareTo).orElse(null);
    }

    record Inputs(Costing costing, Instant pricesAsOf) {

        PlanCost cost(Plan plan) {
            return costing.cost(plan.products(), plan.boughtParts());
        }
    }
}
