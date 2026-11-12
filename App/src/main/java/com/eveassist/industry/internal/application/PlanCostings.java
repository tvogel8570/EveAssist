package com.eveassist.industry.internal.application;

import com.eveassist.industry.internal.domain.Costing;
import com.eveassist.industry.internal.domain.LineCost;
import com.eveassist.industry.internal.domain.Plan;
import com.eveassist.industry.internal.domain.PlanCost;
import com.eveassist.industry.internal.domain.PlanPrice;
import com.eveassist.industry.internal.domain.PlanProduct;
import com.eveassist.sde.SdeService;
import com.eveassist.sde.TypeId;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Gathers what costing needs, once, so that an edit costs twice with the same inputs. Prices come from the plan. */
@Component
class PlanCostings {

    private final SdeService sde;

    PlanCostings(SdeService sde) {
        this.sde = sde;
    }

    /**
     * Everything the products could need, bought or built, because an edit can change which. The plan keeps a price for
     * each, so that buying or building a part never needs a new one.
     */
    Set<TypeId> requiredTypes(List<PlanProduct> products) {
        return new Costing(sde::recipeFor, type -> Optional.empty()).cost(products).lines().stream()
                .map(LineCost::type).collect(Collectors.toUnmodifiableSet());
    }

    /** Costs with the prices the plan holds now, and never with anything fresher. */
    Inputs inputs(Plan plan) {
        var prices = plan.prices();
        return new Inputs(
                new Costing(sde::recipeFor, type -> Optional.ofNullable(prices.get(type)).map(PlanPrice::sell)),
                prices);
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
                runs(plan, cost),
                cost.total(), cost.unpricedLines(), pricesAsOf(cost, inputs), sde.version(), plan.archived());
    }

    /** Everything the plan builds, the products first, each with the runs of its blueprint. */
    List<PlanRunLine> runs(Plan plan, PlanCost cost) {
        return cost.lines().stream().filter(LineCost::built)
                .map(line -> new PlanRunLine(line.type().value(), name(line.type()), plan.hasProduct(line.type()),
                        line.quantity(), line.runs(), line.unitsMade(), line.seconds()))
                .sorted(Comparator.comparing(PlanRunLine::product).reversed().thenComparing(PlanRunLine::name))
                .toList();
    }

    PlanLineCosting describe(LineCost line) {
        var type = sde.itemType(line.type());
        return new PlanLineCosting(line.type().value(),
                type.map(item -> item.name()).orElse("#" + line.type().value()),
                type.map(item -> item.groupName()).orElse(""),
                line.quantity(), line.buildable(), line.built(), line.unitPrice(), line.cost());
    }

    PlanProductLine describe(PlanProduct product) {
        return new PlanProductLine(product.type().value(), name(product.type()), product.quantity());
    }

    String name(TypeId type) {
        return sde.itemType(type).map(item -> item.name()).orElse("#" + type.value());
    }

    PlanTotal total(PlanCost cost, Inputs inputs) {
        return new PlanTotal(cost.total(), sde.version(), pricesAsOf(cost, inputs));
    }

    /** The oldest price among the items that are bought, which is when the quote's prices were last taken. */
    Instant pricesAsOf(PlanCost cost, Inputs inputs) {
        return cost.lines().stream().filter(line -> !line.built())
                .map(line -> inputs.prices().get(line.type())).filter(java.util.Objects::nonNull)
                .map(PlanPrice::asOf).min(Instant::compareTo).orElse(null);
    }

    /** Only an item with a blueprint can be a product. */
    boolean isBuildable(TypeId type) {
        return sde.recipeFor(type).isPresent();
    }

    String sdeVersion() {
        return sde.version();
    }

    record Inputs(Costing costing, Map<TypeId, PlanPrice> prices) {

        PlanCost cost(Plan plan) {
            return costing.cost(plan.products(), plan.boughtParts());
        }
    }
}
