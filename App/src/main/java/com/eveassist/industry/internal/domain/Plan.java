package com.eveassist.industry.internal.domain;

import com.eveassist.industry.PlanId;
import com.eveassist.sde.TypeId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

/** One or more products to build, each with a number of finished units, and the parts to buy rather than build. */
@AggregateRoot
public class Plan {

    private static final long MAX_QUANTITY = 1_000_000;

    @Identity
    private final PlanId id;
    private final String name;
    private final long version;
    private final List<PlanProduct> products;
    private final Set<TypeId> boughtParts;

    public Plan(PlanId id, String name, long version, List<PlanProduct> products, Set<TypeId> boughtParts) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.products = new ArrayList<>(products);
        this.boughtParts = new HashSet<>(boughtParts);
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

    public List<PlanProduct> products() {
        return List.copyOf(products);
    }

    public Set<TypeId> boughtParts() {
        return Set.copyOf(boughtParts);
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
