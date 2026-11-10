package com.eveassist.industry.internal.domain;

import com.eveassist.industry.PlanId;
import com.eveassist.sde.TypeId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

/**
 * One or more products to build, each with a number of finished units, the parts to buy rather than build, and the
 * prices it was costed with.
 */
@AggregateRoot
public class Plan {

    private static final long MAX_QUANTITY = 1_000_000;

    @Identity
    private final PlanId id;
    private final String name;
    private final long version;
    private final List<PlanProduct> products;
    private final Set<TypeId> boughtParts;
    private final Map<TypeId, PlanPrice> prices;
    private final boolean archived;

    public Plan(PlanId id, String name, long version, List<PlanProduct> products, Set<TypeId> boughtParts,
            Map<TypeId, PlanPrice> prices) {
        this(id, name, version, products, boughtParts, prices, false);
    }

    public Plan(PlanId id, String name, long version, List<PlanProduct> products, Set<TypeId> boughtParts,
            Map<TypeId, PlanPrice> prices, boolean archived) {
        this.archived = archived;
        this.id = id;
        this.name = name;
        this.version = version;
        this.products = new ArrayList<>(products);
        this.boughtParts = new HashSet<>(boughtParts);
        this.prices = new HashMap<>(prices);
    }

    public PlanId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public long version() {
        return version;
    }

    /** True for a plan the user archived: hidden from the list, still there to open and restore. */
    public boolean archived() {
        return archived;
    }

    public List<PlanProduct> products() {
        return List.copyOf(products);
    }

    public Set<TypeId> boughtParts() {
        return Set.copyOf(boughtParts);
    }

    public Map<TypeId, PlanPrice> prices() {
        return Map.copyOf(prices);
    }

    /** Takes a new price for an item, replacing the one the plan had. */
    public void setPrice(TypeId type, PlanPrice price) {
        prices.put(type, price);
    }

    /** Takes a price only for an item the plan has none for, so that the prices it already has stay as they were. */
    public void addPriceIfMissing(TypeId type, PlanPrice price) {
        prices.putIfAbsent(type, price);
    }

    /** Drops prices of items the plan no longer needs. */
    public void keepPricesFor(Set<TypeId> needed) {
        prices.keySet().retainAll(needed);
    }

    public void buyPart(TypeId type) {
        boughtParts.add(type);
    }

    public void buildPart(TypeId type) {
        boughtParts.remove(type);
    }

    public boolean hasProduct(TypeId type) {
        return products.stream().anyMatch(product -> product.type().equals(type));
    }

    public void changeQuantity(TypeId type, long quantity) {
        check(quantity);
        products.replaceAll(product -> product.type().equals(type) ? new PlanProduct(type, quantity) : product);
    }

    /** Takes a product out. What it needed is not forgotten: prices stay, so putting it back is quoted as it was. */
    public void removeProduct(TypeId type) {
        products.removeIf(product -> product.type().equals(type));
    }

    /** Adding a product the plan already has adds to its quantity. */
    public void addProduct(TypeId type, long quantity) {
        check(quantity);
        var existing = products.stream().filter(product -> product.type().equals(type)).findFirst();
        if (existing.isPresent()) {
            changeQuantity(type, existing.get().quantity() + quantity);
        } else {
            products.add(new PlanProduct(type, quantity));
        }
    }

    private static void check(long quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new InvalidQuantityException(quantity);
        }
    }
}
