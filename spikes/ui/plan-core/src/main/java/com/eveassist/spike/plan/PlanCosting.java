package com.eveassist.spike.plan;

import com.eveassist.spike.plan.Catalog.Facility;
import com.eveassist.spike.plan.Catalog.Item;
import com.eveassist.spike.plan.PlanState.Decision;

import java.util.ArrayList;
import java.util.List;

/** Costs a plan from its state. Every UI calls this after each change and renders the result. */
public final class PlanCosting {

    /**
     * One node of the costed tree. {@code buildCost} is null for items that can only be bought. {@code children} is
     * empty unless the node is built.
     */
    public record CostedNode(String path, int depth, String itemId, String name, long quantity, boolean buildable,
                             boolean buyOnly, Decision decision, boolean overridden, String facilityId,
                             double buyCost, Double buildCost, double chosenCost, List<CostedNode> children) {
        public Decision ruleDecision() {
            return buildCost != null && buildCost < buyCost ? Decision.BUILD : Decision.BUY;
        }

        /** How much the chosen option saves (positive) or costs extra (negative) compared with the other one. */
        public double savingVersusOther() {
            if (buildCost == null) {
                return 0;
            }
            return decision == Decision.BUILD ? buyCost - buildCost : buildCost - buyCost;
        }
    }

    public record CostedPlan(int quantity, CostedNode root, double totalCost, double productValue, double profit,
                             double marginPercent, List<Facility> facilities) {
    }

    private PlanCosting() {
    }

    public static CostedPlan cost(PlanState state) {
        int quantity = state.quantity();
        CostedNode root = node(state, PlanState.PRODUCT, PlanState.PRODUCT, 0, quantity, true);
        double value = quantity * Catalog.item(PlanState.PRODUCT).jitaSell();
        double profit = value - root.chosenCost();
        return new CostedPlan(quantity, root, root.chosenCost(), value, profit, 100 * profit / value,
                Catalog.facilities());
    }

    private static CostedNode node(PlanState state, String path, String itemId, int depth, long quantity,
                                   boolean isRoot) {
        Item item = Catalog.item(itemId);
        double buyCost = quantity * item.jitaSell();
        if (!item.buildable()) {
            return new CostedNode(path, depth, itemId, item.name(), quantity, false, item.buyOnly(), Decision.BUY,
                    false, null, buyCost, null, buyCost, List.of());
        }

        String facilityId = state.facility(path, itemId);
        Facility facility = Catalog.facility(facilityId);
        List<CostedNode> children = new ArrayList<>();
        double buildCost = quantity * item.jobFeePerRun() * (1 + facility.taxRate());
        for (Catalog.Input input : item.inputs()) {
            long needed = (long) Math.ceil(input.quantityPerRun() * quantity * (1 - facility.materialBonus()));
            CostedNode child = node(state, path + "/" + input.itemId(), input.itemId(), depth + 1,
                    Math.max(needed, quantity), false);
            children.add(child);
            buildCost += child.chosenCost();
        }

        // The product itself is always built; the rule and overrides apply to intermediates.
        Decision rule = buildCost < buyCost ? Decision.BUILD : Decision.BUY;
        Decision override = isRoot ? null : state.override(path);
        Decision decision = isRoot ? Decision.BUILD : (override != null ? override : rule);
        boolean built = decision == Decision.BUILD;
        return new CostedNode(path, depth, itemId, item.name(), quantity, true, false, decision, override != null,
                facilityId, buyCost, buildCost, built ? buildCost : buyCost, built ? children : List.of());
    }

    /** Finds a node by path, for applying a change to it. */
    public static CostedNode find(CostedNode node, String path) {
        if (node.path().equals(path)) {
            return node;
        }
        for (CostedNode child : node.children()) {
            CostedNode found = find(child, path);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Sets a node's decision, clearing the override when it matches the default rule. */
    public static void decide(PlanState state, String path, Decision decision) {
        CostedNode node = find(cost(state).root(), path);
        if (node == null || !node.buildable() || node.depth() == 0) {
            throw new IllegalArgumentException("Cannot decide " + path);
        }
        state.setDecision(path, decision, node.ruleDecision());
    }
}
