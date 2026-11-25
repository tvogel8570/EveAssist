package com.eveassist.industry.internal.domain;

import com.eveassist.industry.FacilityId;
import com.eveassist.industry.ProjectId;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

/**
 * One piece of work for a buyer: what to make, how each built item is made, the facility, the builder and the rates, and the prices
 * it costs with. A project is a Draft until a quote is issued from it, and then it is Quoted and locked until a new version is made
 * from a quote. A plan gives it its first products and prices and after that the project owns them (architecture/projects.md).
 *
 * <p>Every edit goes through this class, and an edit of a quoted project is refused. Renaming, archiving and costing are not edits
 * of the inputs: costing and quotes are kept apart from this class, and renaming and archiving are statuses that the repository keeps.
 */
@AggregateRoot
public class Project {

    private static final long MAX_QUANTITY = 1_000_000;

    @Identity
    private final ProjectId id;
    private final String name;
    private final long version;
    private ProjectStatus status;
    private final int lastQuoteNumber;
    private final boolean archived;
    private ProjectConfig config;
    private final List<PlanProduct> products;
    private final Set<TypeId> boughtParts;
    private final Map<TypeId, ProjectItem> items;
    private final Map<TypeId, PlanPrice> prices;
    /** The items whose price the user typed. Their price is the same on both sides, and a refresh of the market prices leaves it as it is. */
    private final Set<TypeId> typedPrices;

    public Project(
            ProjectId id, String name, long version, ProjectStatus status, int lastQuoteNumber, boolean archived,
            ProjectConfig config, List<PlanProduct> products, Set<TypeId> boughtParts, Map<TypeId, ProjectItem> items,
            Map<TypeId, PlanPrice> prices) {
        this(id, name, version, status, lastQuoteNumber, archived, config, products, boughtParts, items, prices, Set.of());
    }

    public Project(
            ProjectId id, String name, long version, ProjectStatus status, int lastQuoteNumber, boolean archived,
            ProjectConfig config, List<PlanProduct> products, Set<TypeId> boughtParts, Map<TypeId, ProjectItem> items,
            Map<TypeId, PlanPrice> prices, Set<TypeId> typedPrices) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.status = status;
        this.lastQuoteNumber = lastQuoteNumber;
        this.archived = archived;
        this.config = config;
        this.products = new ArrayList<>(products);
        this.boughtParts = new HashSet<>(boughtParts);
        this.items = new HashMap<>(items);
        this.prices = new HashMap<>(prices);
        this.typedPrices = new HashSet<>(typedPrices);
        this.typedPrices.retainAll(prices.keySet());
    }

    public ProjectId id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** Counts edits, for optimistic concurrency. A stale edit is refused when it is saved. */
    public long version() {
        return version;
    }

    public ProjectStatus status() {
        return status;
    }

    public boolean draft() {
        return status == ProjectStatus.DRAFT;
    }

    /** The number of the last quote issued, which is never reused after a quote is deleted. 0 before the first. */
    public int lastQuoteNumber() {
        return lastQuoteNumber;
    }

    /** True for a project the developer archived: hidden from the list, still there to open and restore. */
    public boolean archived() {
        return archived;
    }

    public ProjectConfig config() {
        return config;
    }

    public List<PlanProduct> products() {
        return List.copyOf(products);
    }

    public Set<TypeId> boughtParts() {
        return Set.copyOf(boughtParts);
    }

    public Map<TypeId, ProjectItem> items() {
        return Map.copyOf(items);
    }

    public Map<TypeId, PlanPrice> prices() {
        return Map.copyOf(prices);
    }

    /** The items whose price was typed by the user and not taken from the market. */
    public Set<TypeId> typedPrices() {
        return Set.copyOf(typedPrices);
    }

    public boolean hasProduct(TypeId type) {
        return products.stream().anyMatch(product -> product.type().equals(type));
    }

    /** A quote has been issued from this Draft: it can no longer be edited. */
    public void lock() {
        requireDraft();
        this.status = ProjectStatus.QUOTED;
    }

    /** A new version is made from a quote: the project is a Draft again. */
    public void reopen() {
        if (status != ProjectStatus.QUOTED) {
            throw new IllegalStateException("Only a quoted project can be made a Draft again");
        }
        this.status = ProjectStatus.DRAFT;
    }

    /**
     * Replaces every input with the given ones, which is how a new version is made from a quote: what the quote was issued from becomes the
     * Draft's inputs. The project must be a Draft, so call {@link #reopen()} first. The caller has made sure the facilities exist.
     */
    public void replaceInputs(
            List<PlanProduct> newProducts, Set<TypeId> newBoughtParts, Map<TypeId, ProjectItem> newItems,
            Map<TypeId, PlanPrice> newPrices, ProjectConfig newConfig) {
        requireDraft();
        products.clear();
        products.addAll(newProducts);
        boughtParts.clear();
        boughtParts.addAll(newBoughtParts);
        items.clear();
        items.putAll(newItems);
        prices.clear();
        prices.putAll(newPrices);
        config = newConfig;
    }

    /** Adding a product the project already has adds to its quantity. A product the plan did not have is fine. */
    public void addProduct(TypeId type, long quantity) {
        requireDraft();
        check(quantity);
        var existing = products.stream().filter(product -> product.type().equals(type)).findFirst();
        if (existing.isPresent()) {
            replaceQuantity(type, existing.get().quantity() + quantity);
        } else {
            products.add(new PlanProduct(type, quantity));
        }
    }

    public void changeQuantity(TypeId type, long quantity) {
        requireDraft();
        check(quantity);
        replaceQuantity(type, quantity);
    }

    /** Takes a product out. Dropping the prices and items nothing needs any more is for the caller, which knows the recipes. */
    public void removeProduct(TypeId type) {
        requireDraft();
        products.removeIf(product -> product.type().equals(type));
    }

    public void buyPart(TypeId type) {
        requireDraft();
        boughtParts.add(type);
    }

    public void buildPart(TypeId type) {
        requireDraft();
        boughtParts.remove(type);
    }

    /** Sets how an item is made, replacing what the project had for it. */
    public void setItem(ProjectItem item) {
        requireDraft();
        items.put(item.type(), item);
    }

    /** Drops the items nothing builds any more. */
    public void keepItemsFor(Set<TypeId> needed) {
        requireDraft();
        items.keySet().retainAll(needed);
    }

    /** Takes a new price for an item, replacing the one the project had. */
    public void setPrice(TypeId type, PlanPrice price) {
        requireDraft();
        prices.put(type, price);
    }

    /**
     * Takes a market price for an item, unless the user typed one for it, which stays until they ask for the market price again. This is how a refresh of
     * the prices changes the project's prices.
     */
    public void setMarketPrice(TypeId type, PlanPrice price) {
        requireDraft();
        if (!typedPrices.contains(type)) {
            prices.put(type, price);
        }
    }

    /**
     * Takes a price the user typed for an item: one number, the same on both sides, so it is itself under any price basis (a Jita split of two equal prices is
     * that price). It replaces the market price, and stays through refreshes until {@link #useMarketPrice}.
     */
    public void typePrice(TypeId type, BigDecimal price, Instant asOf) {
        requireDraft();
        prices.put(type, new PlanPrice(price, price, asOf));
        typedPrices.add(type);
    }

    /** Gives up the typed price of an item, and takes the market price in its place, or none when there is none to take. */
    public void useMarketPrice(TypeId type, PlanPrice market) {
        requireDraft();
        typedPrices.remove(type);
        if (market == null) {
            prices.remove(type);
        } else {
            prices.put(type, market);
        }
    }

    /** Takes a price only for an item the project has none for, so that the prices it already has stay as they were. */
    public void addPriceIfMissing(TypeId type, PlanPrice price) {
        requireDraft();
        prices.putIfAbsent(type, price);
    }

    /** Drops prices of items the project no longer needs. */
    public void keepPricesFor(Set<TypeId> needed) {
        requireDraft();
        prices.keySet().retainAll(needed);
        typedPrices.retainAll(needed);
    }

    public void settings(ProjectSettings settings) {
        requireDraft();
        config = config.withSettings(settings);
    }

    public void priceSide(PriceSide side) {
        requireDraft();
        config = config.withPriceSide(side);
    }

    public void defaultPolicy(SplitPolicy policy) {
        requireDraft();
        config = config.withDefaultPolicy(policy);
    }

    public void defaultFacility(FacilityId facility) {
        requireDraft();
        config = config.withDefaultFacility(facility);
    }

    public void builder(ProjectBuilder builder) {
        requireDraft();
        config = config.withBuilder(builder);
    }

    public void buyer(String buyerName, String note) {
        requireDraft();
        config = config.withBuyer(buyerName, note);
    }

    private void replaceQuantity(TypeId type, long quantity) {
        products.replaceAll(product -> product.type().equals(type) ? new PlanProduct(type, quantity) : product);
    }

    private void requireDraft() {
        if (status != ProjectStatus.DRAFT) {
            throw new ProjectLockedException();
        }
    }

    private static void check(long quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new InvalidQuantityException(quantity);
        }
    }
}
