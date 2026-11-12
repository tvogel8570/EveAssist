package com.eveassist.industry.internal.application;

import com.eveassist.identity.Visitor;
import com.eveassist.industry.PlanId;
import com.eveassist.industry.internal.domain.Plan;
import com.eveassist.industry.internal.domain.PlanPrice;
import com.eveassist.industry.internal.domain.PlanProduct;
import com.eveassist.market.JitaQuote;
import com.eveassist.market.MarketService;
import com.eveassist.sde.ItemType;
import com.eveassist.sde.SdeService;
import com.eveassist.sde.TypeId;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;

/**
 * Plans for a visitor who has not signed in. Nothing is stored: every call takes the plan as text, changes it, costs it
 * with the prices the server already holds, and gives the changed text back for the browser to keep. A price that is
 * not stored is fetched, within the limits in {@link PriceFetchLimits}. Prices are never refreshed.
 */
@Service
@PrimaryPort
public class TryPlans {

    /** A plan without an account holds at most this many products, so one visitor cannot ask for unlimited work. */
    public static final int MOST_PRODUCTS = 10;
    /** How old the oldest price may be before the page says so. Provisional: the requirements leave the age open. */
    static final Duration STALE_AFTER = Duration.ofHours(24);
    private static final long MOST_UNITS = 1_000_000;
    private static final PlanId NO_ID = new PlanId(new UUID(0, 0));

    private final PlanCostings costings;
    private final MarketService market;
    private final PriceFetchLimits limits;
    private final SdeService sde;
    private final Clock clock;

    TryPlans(PlanCostings costings, MarketService market, PriceFetchLimits limits, SdeService sde, Clock clock) {
        this.costings = costings;
        this.market = market;
        this.limits = limits;
        this.sde = sde;
        this.clock = clock;
    }

    /** The plan as it is, for the first drawing and for a plan the browser kept. */
    public TryResult open(Visitor visitor, String state) {
        return result(load(state), visitor, null, null);
    }

    public TryResult changeQuantity(Visitor visitor, String state, int type, long quantity) {
        var plan = load(state);
        if (!plan.hasProduct(new TypeId(type))) {
            throw new UnknownProductException();
        }
        plan.changeQuantity(new TypeId(type), quantity);
        return result(plan, visitor, null, null);
    }

    /** Adds units of a product, or the product itself unless the plan already holds the most it may. */
    public TryResult addProduct(Visitor visitor, String state, int type, long quantity) {
        var id = new TypeId(type);
        if (!costings.isBuildable(id)) {
            throw new UnknownProductException();
        }
        var plan = load(state);
        if (!plan.hasProduct(id) && plan.products().size() >= MOST_PRODUCTS) {
            return result(plan, visitor, null, "maxProducts");
        }
        plan.addProduct(id, quantity);
        return result(plan, visitor, null, null);
    }

    public TryResult removeProduct(Visitor visitor, String state, int type) {
        var plan = load(state);
        var id = new TypeId(type);
        var removed = plan.products().stream().filter(product -> product.type().equals(id)).findFirst()
                .orElseThrow(UnknownProductException::new);
        plan.removeProduct(id);
        return result(plan, visitor, removed, null);
    }

    public TryResult choosePartSource(Visitor visitor, String state, int type, boolean buy) {
        var plan = load(state);
        var id = new TypeId(type);
        if (buy) {
            plan.buyPart(id);
        } else {
            plan.buildPart(id);
        }
        return result(plan, visitor, null, null);
    }

    public List<ItemType> searchProducts(Visitor visitor, String text) {
        return sde.searchBuildable(text, 12);
    }

    /**
     * A plan from text. A saved plan may be damaged or out of date, so what cannot be used is left out and the rest is
     * kept: unknown or repeated products, units the plan cannot hold, and more products than a plan may have.
     */
    private Plan load(String state) {
        var parsed = TryPlanState.parse(state);
        var products = new ArrayList<PlanProduct>();
        var seen = new HashSet<Integer>();
        for (var entry : parsed.products()) {
            if (products.size() < MOST_PRODUCTS && seen.add(entry.type()) && entry.quantity() >= 1
                    && entry.quantity() <= MOST_UNITS && costings.isBuildable(new TypeId(entry.type()))) {
                products.add(new PlanProduct(new TypeId(entry.type()), entry.quantity()));
            }
        }
        return new Plan(NO_ID, "", 0, products, parsed.bought(), Map.of());
    }

    private TryResult result(Plan plan, Visitor visitor, PlanProduct removed, String notice) {
        var needed = costings.requiredTypes(plan.products());
        var quotes = new HashMap<TypeId, JitaQuote>(market.storedPrices(needed).quotes());
        var unpriced = new LinkedHashSet<TypeId>();
        var missing = needed.stream().filter(type -> !quotes.containsKey(type))
                .sorted(Comparator.comparingInt(TypeId::value)).toList();
        if (!missing.isEmpty()) {
            int allowed = limits.take(visitor.address(), missing.size());
            if (allowed > 0) {
                var fetched = market.currentPrices(missing.subList(0, allowed));
                quotes.putAll(fetched.quotes());
                unpriced.addAll(fetched.failed().keySet());
            }
            unpriced.addAll(missing.subList(allowed, missing.size()));
        }
        var prices = new HashMap<TypeId, PlanPrice>();
        quotes.forEach((type, quote) -> prices.put(type, new PlanPrice(quote.sell(), quote.buy(), quote.asOf())));
        var kept = new HashSet<>(plan.boughtParts());
        kept.retainAll(needed);
        var priced = new Plan(NO_ID, "", 0, plan.products(), kept, prices);
        var inputs = costings.inputs(priced);
        var details = costings.details(priced, 0, inputs);
        var oldest = details.pricesAsOf();
        boolean stale = oldest != null && oldest.isBefore(clock.instant().minus(STALE_AFTER));
        var state = new TryPlanState(
                priced.products().stream().map(p -> new TryPlanState.ProductEntry(p.type().value(), p.quantity())).toList(),
                Set.copyOf(kept)).format();
        var removal = removed == null ? null : new ProductRemoval(details, removed.type(),
                costings.name(removed.type()), removed.quantity());
        return new TryResult(details, state,
                unpriced.stream().map(costings::name).sorted().toList(), stale, notice, removal);
    }
}
