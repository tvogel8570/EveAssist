package com.eveassist.industry.internal.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.PlanId;
import com.eveassist.industry.internal.domain.Plan;
import com.eveassist.industry.internal.domain.PlanCost;
import com.eveassist.industry.internal.domain.PlanPrice;
import com.eveassist.industry.internal.domain.PlanProduct;
import com.eveassist.market.JitaQuote;
import com.eveassist.sde.TypeId;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PrimaryPort
@Transactional
public class PlanEditing {

    private final PlanRepository plans;
    private final PlanCostings costings;

    PlanEditing(PlanRepository plans, PlanCostings costings) {
        this.plans = plans;
        this.costings = costings;
    }

    public PlanId create(Viewer viewer, CreatePlanCommand command) {
        return plans.create(viewer, command.name());
    }

    /** Costs before and after and compares, so a line the edit reaches through the material tree cannot be missed. */
    public PlanChangeResult changeQuantity(Viewer viewer, ChangeQuantityCommand command) {
        var plan = loadAt(viewer, command.plan(), command.expectedVersion());
        if (!plan.hasProduct(command.type())) {
            throw new PlanNotFoundException();
        }
        var inputs = costings.inputs(plan);
        var before = inputs.cost(plan);
        plan.changeQuantity(command.type(), command.quantity());
        return saveAndCompare(viewer, plan, inputs, before);
    }

    /** Adds the product with no new prices, so an item the plan has no price for stays unpriced. */
    public PlanChangeResult addProduct(Viewer viewer, AddProductCommand command) {
        return addProduct(viewer, command, Map.of());
    }

    /**
     * Adds the product and takes the given prices for items the plan has none for. The prices the plan already has are
     * not touched, so the rest of the quote stays as it was.
     */
    public PlanChangeResult addProduct(Viewer viewer, AddProductCommand command, Map<TypeId, JitaQuote> newPrices) {
        var plan = load(viewer, command.plan());
        newPrices.forEach((type, quote) -> plan.addPriceIfMissing(type, priceOf(quote)));
        var inputs = costings.inputs(plan);
        var before = inputs.cost(plan);
        plan.addProduct(command.type(), command.quantity());
        return saveAndCompare(viewer, plan, inputs, before);
    }

    /**
     * Takes new prices for the plan, which is the only way its existing prices change. An item with no new price
     * keeps its old one.
     */
    public PlanDetails replacePrices(Viewer viewer, PlanId id, long expectedVersion, Map<TypeId, JitaQuote> prices) {
        var plan = loadAt(viewer, id, expectedVersion);
        prices.forEach((type, quote) -> plan.setPrice(type, priceOf(quote)));
        plan.keepPricesFor(costings.requiredTypes(plan.products()));
        var inputs = costings.inputs(plan);
        long version = plans.save(viewer, plan, costings.total(inputs.cost(plan), inputs))
                .orElseThrow(PlanChangedException::new);
        return costings.details(plan, version, inputs);
    }

    /** Buying a part removes everything below it, so the result is the whole plan rather than changed lines. */
    public PlanDetails choosePartSource(Viewer viewer, ChoosePartSourceCommand command) {
        var plan = loadAt(viewer, command.plan(), command.expectedVersion());
        var inputs = costings.inputs(plan);
        var isPart = inputs.costing().cost(plan.products()).lines().stream()
                .anyMatch(line -> line.type().equals(command.part()) && line.buildable());
        if (!isPart || plan.hasProduct(command.part())) {
            throw new PlanNotFoundException();
        }
        if (command.buy()) {
            plan.buyPart(command.part());
        } else {
            plan.buildPart(command.part());
        }
        var after = inputs.cost(plan);
        long version = plans.save(viewer, plan, costings.total(after, inputs)).orElseThrow(PlanChangedException::new);
        return costings.details(plan, version, inputs);
    }

    private static PlanPrice priceOf(JitaQuote quote) {
        return new PlanPrice(quote.sell(), quote.buy(), quote.asOf());
    }

    private Plan loadAt(Viewer viewer, PlanId id, long expectedVersion) {
        var plan = load(viewer, id);
        if (plan.version() != expectedVersion) {
            throw new PlanChangedException();
        }
        return plan;
    }

    private Plan load(Viewer viewer, PlanId id) {
        return plans.find(viewer, id).orElseThrow(PlanNotFoundException::new);
    }

    private PlanChangeResult saveAndCompare(Viewer viewer, Plan plan, PlanCostings.Inputs inputs, PlanCost before) {
        var after = inputs.cost(plan);
        long version = plans.save(viewer, plan, costings.total(after, inputs)).orElseThrow(PlanChangedException::new);
        return new PlanChangeResult(plan.id(), version,
                after.changedSince(before).stream()
                        .filter(line -> !plan.hasProduct(line.type()))
                        .map(costings::describe)
                        .toList(),
                after.total(), after.unpricedLines(), costings.pricesAsOf(after, inputs));
    }
}
