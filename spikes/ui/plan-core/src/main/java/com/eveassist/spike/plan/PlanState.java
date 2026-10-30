package com.eveassist.spike.plan;

import java.util.HashMap;
import java.util.Map;

/**
 * What the user has chosen in the plan editor: the quantity, and per node any build-or-buy override and facility.
 * A node is identified by its path from the root, such as {@code ishtar/vexor}. Nodes without an override follow the
 * default rule (build if cheaper than buying).
 */
public class PlanState {

    public enum Decision { BUILD, BUY }

    public static final String PRODUCT = "ishtar";

    private int quantity = 10;
    private final Map<String, Decision> overrides = new HashMap<>();
    private final Map<String, String> facilities = new HashMap<>();

    public synchronized int quantity() {
        return quantity;
    }

    public synchronized void setQuantity(int quantity) {
        this.quantity = Math.clamp(quantity, 1, 1_000);
    }

    public synchronized Decision override(String path) {
        return overrides.get(path);
    }

    /** Sets the decision for a node; choosing what the default rule would choose clears the override. */
    public synchronized void setDecision(String path, Decision decision, Decision ruleDecision) {
        if (decision == ruleDecision) {
            overrides.remove(path);
        } else {
            overrides.put(path, decision);
        }
    }

    public synchronized String facility(String path, String itemId) {
        return facilities.getOrDefault(path, Catalog.defaultFacility(itemId));
    }

    public synchronized void setFacility(String path, String facilityId) {
        Catalog.facility(facilityId);
        facilities.put(path, facilityId);
    }

    public synchronized void reset() {
        quantity = 10;
        overrides.clear();
        facilities.clear();
    }
}
