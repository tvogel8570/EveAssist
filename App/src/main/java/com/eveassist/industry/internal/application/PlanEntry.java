package com.eveassist.industry.internal.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.PlanId;
import com.eveassist.industry.internal.domain.Plan;
import com.eveassist.industry.internal.domain.PlanProduct;
import com.eveassist.market.JitaQuotes;
import com.eveassist.market.MarketService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Page actions that may start an ESI fetch. A plan's prices change only when the user asks for it, or when a product
 * is added whose items the plan has no price for. The fetch runs between two short transactions, so no database
 * connection is held while ESI is being asked.
 */
@Service
@PrimaryPort
public class PlanEntry {

    private final PlanRepository plans;
    private final PlanCostings costings;
    private final PlanEditing editing;
    private final MarketService market;
    private final TransactionTemplate reading;

    PlanEntry(PlanRepository plans, PlanCostings costings, PlanEditing editing, MarketService market,
            PlatformTransactionManager transactions) {
        this.plans = plans;
        this.costings = costings;
        this.editing = editing;
        this.market = market;
        this.reading = new TransactionTemplate(transactions);
        this.reading.setReadOnly(true);
    }

    /**
     * The plan is costed to draw the page anyway, so an out-of-date stored total is replaced at the same time. Opening
     * a plan never changes its prices.
     */
    @Transactional
    public PlanDetails open(Viewer viewer, PlanId id) {
        var plan = plans.find(viewer, id).orElseThrow(PlanNotFoundException::new);
        var inputs = costings.inputs(plan);
        var cost = inputs.cost(plan);
        if (!plan.products().isEmpty()) {
            plans.recordCosting(viewer, plan, costings.total(cost, inputs));
        }
        return costings.details(plan, plan.version(), inputs);
    }

    /** Fetches prices for the items the new product brings in that the plan has none for, and only those. */
    public PlanChangeResult addProduct(Viewer viewer, AddProductCommand command) {
        var plan = load(viewer, command.plan());
        var products = new ArrayList<>(plan.products());
        products.add(new PlanProduct(command.type(), command.quantity()));
        var missing = new HashSet<>(costings.requiredTypes(products));
        missing.removeAll(plan.prices().keySet());
        var fetched = missing.isEmpty() ? new JitaQuotes(Map.of(), Map.of()) : market.currentPrices(missing);
        return editing.addProduct(viewer, command, fetched.quotes());
    }

    /** Fetches current prices for everything the plan could need. Items ESI cannot answer for keep their old price. */
    public PriceRefresh refreshPrices(Viewer viewer, PlanId id) {
        var plan = load(viewer, id);
        var fetched = market.currentPrices(costings.requiredTypes(plan.products()));
        var details = editing.replacePrices(viewer, id, plan.version(), fetched.quotes());
        return new PriceRefresh(details, fetched.failed().keySet().stream().map(costings::name).sorted().toList());
    }

    private Plan load(Viewer viewer, PlanId id) {
        return reading.execute(status -> plans.find(viewer, id)).orElseThrow(PlanNotFoundException::new);
    }
}
