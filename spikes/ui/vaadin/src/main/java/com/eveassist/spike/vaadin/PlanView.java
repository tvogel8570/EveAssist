package com.eveassist.spike.vaadin;

import com.eveassist.spike.plan.Catalog.Facility;
import com.eveassist.spike.plan.Isk;
import com.eveassist.spike.plan.PlanCosting;
import com.eveassist.spike.plan.PlanCosting.CostedNode;
import com.eveassist.spike.plan.PlanCosting.CostedPlan;
import com.eveassist.spike.plan.PlanState;
import com.eveassist.spike.plan.PlanState.Decision;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The plan editor as one Java class. Vaadin keeps this object on the server for each open browser tab, and every
 * click arrives here as an event; after each change the view recalculates the plan and refills the grid.
 */
@Route("")
@PageTitle("Plan editor: Vaadin")
public class PlanView extends VerticalLayout {

    private final PlanState state = new PlanState();
    private final TreeGrid<CostedNode> grid = new TreeGrid<>();
    private final Set<String> collapsed = new HashSet<>();
    private final IntegerField quantity = new IntegerField("Ishtars to build");
    private final Span totalCost = new Span();
    private final Span productValue = new Span();
    private final Span profit = new Span();
    private final Span margin = new Span();
    private List<Facility> facilities = List.of();

    public PlanView() {
        setMaxWidth("72rem");
        getStyle().set("margin", "0 auto");

        HorizontalLayout header = new HorizontalLayout(new H3("EveAssist"), badge("Spike C: Vaadin", "contrast"));
        header.setAlignItems(FlexComponent.Alignment.BASELINE);

        HorizontalLayout stats = new HorizontalLayout(
                stat("Total cost", totalCost, null),
                stat("Value at Jita sell", productValue, null),
                stat("Profit", profit, margin));
        stats.setWidthFull();
        stats.setWrap(true);

        quantity.setMin(1);
        quantity.setMax(1000);
        quantity.setStepButtonsVisible(true);
        quantity.addValueChangeListener(e -> {
            if (e.isFromClient() && e.getValue() != null) {
                state.setQuantity(e.getValue());
                refresh();
            }
        });
        Button reset = new Button("Reset choices", e -> {
            state.reset();
            refresh();
        });
        reset.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Span hint = new Span("Intermediates follow \"build if cheaper\" unless you override them.");
        hint.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)");
        HorizontalLayout controls = new HorizontalLayout(quantity, reset, hint);
        controls.setAlignItems(FlexComponent.Alignment.BASELINE);
        controls.setWrap(true);

        configureGrid();
        add(header, stats, controls, grid);
        refresh();
    }

    private void configureGrid() {
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setAllRowsVisible(true);
        grid.addComponentHierarchyColumn(this::itemCell).setHeader("Item").setFlexGrow(3);
        grid.addColumn(n -> Isk.quantity(n.quantity())).setHeader("Quantity").setTextAlign(ColumnTextAlign.END);
        grid.addComponentColumn(this::decisionCell).setHeader("Build or buy");
        grid.addComponentColumn(this::facilityCell).setHeader("Facility").setFlexGrow(2);
        grid.addComponentColumn(n -> cost(n.buyCost(), n.buildCost() != null && n.buyCost() <= n.buildCost()))
                .setHeader("Buy").setTextAlign(ColumnTextAlign.END);
        grid.addComponentColumn(n -> n.buildCost() == null ? new Span("—")
                        : cost(n.buildCost(), n.buildCost() < n.buyCost()))
                .setHeader("Build").setTextAlign(ColumnTextAlign.END);
        grid.addComponentColumn(this::chosenCell).setHeader("Cost").setTextAlign(ColumnTextAlign.END);

        // Remember what the user collapsed, so a recalculation does not reopen it.
        grid.addCollapseListener(e -> e.getItems().forEach(n -> collapsed.add(n.path())));
        grid.addExpandListener(e -> e.getItems().forEach(n -> collapsed.remove(n.path())));
    }

    private void refresh() {
        CostedPlan plan = PlanCosting.cost(state);
        facilities = plan.facilities();
        quantity.setValue(plan.quantity());
        totalCost.setText(Isk.format(plan.totalCost()));
        productValue.setText(Isk.format(plan.productValue()));
        profit.setText(Isk.format(plan.profit()));
        profit.getStyle().set("color",
                plan.profit() >= 0 ? "var(--lumo-success-text-color)" : "var(--lumo-error-text-color)");
        margin.setText(String.format(Locale.ROOT, "%.1f %% margin", plan.marginPercent()));

        grid.setItems(List.of(plan.root()), CostedNode::children);
        List<CostedNode> toExpand = new ArrayList<>();
        collectExpanded(plan.root(), toExpand);
        grid.expand(toExpand);
    }

    private void collectExpanded(CostedNode node, List<CostedNode> into) {
        if (!node.children().isEmpty() && !collapsed.contains(node.path())) {
            into.add(node);
            node.children().forEach(c -> collectExpanded(c, into));
        }
    }

    private Component itemCell(CostedNode n) {
        HorizontalLayout cell = new HorizontalLayout(new Span(n.name()));
        cell.setSpacing(false);
        cell.getStyle().set("gap", "0.4rem");
        if (n.buyOnly()) {
            cell.add(badge("reaction, buy only", "small"));
        }
        if (n.overridden()) {
            cell.add(badge("override", "warning small"));
        }
        return cell;
    }

    private Component decisionCell(CostedNode n) {
        if (n.depth() == 0) {
            return badge("product", "small primary");
        }
        if (!n.buildable()) {
            Span buy = new Span("buy");
            buy.getStyle().set("color", "var(--lumo-secondary-text-color)");
            return buy;
        }
        HorizontalLayout toggle = new HorizontalLayout(decisionButton(n, Decision.BUILD, "Build"),
                decisionButton(n, Decision.BUY, "Buy"));
        toggle.setSpacing(false);
        return toggle;
    }

    private Button decisionButton(CostedNode n, Decision decision, String label) {
        Button button = new Button(label, e -> {
            PlanCosting.decide(state, n.path(), decision);
            refresh();
        });
        button.addThemeVariants(ButtonVariant.LUMO_SMALL);
        if (n.decision() == decision) {
            button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        }
        return button;
    }

    private Component facilityCell(CostedNode n) {
        if (!n.buildable() || n.decision() != Decision.BUILD) {
            return new Span();
        }
        Select<Facility> select = new Select<>();
        select.setItems(facilities);
        select.setItemLabelGenerator(Facility::name);
        select.setValue(facilities.stream().filter(f -> f.id().equals(n.facilityId())).findFirst().orElse(null));
        select.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                state.setFacility(n.path(), e.getValue().id());
                refresh();
            }
        });
        select.addThemeName("small");
        return select;
    }

    private Component chosenCell(CostedNode n) {
        Span cost = new Span(Isk.format(n.chosenCost()));
        cost.getStyle().set("font-weight", "600");
        if (n.overridden() && n.savingVersusOther() < 0) {
            Span extra = new Span("+" + Isk.format(-n.savingVersusOther()) + " vs. rule");
            extra.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("color",
                    "var(--lumo-error-text-color)");
            VerticalLayout both = new VerticalLayout(cost, extra);
            both.setPadding(false);
            both.setSpacing(false);
            both.setAlignItems(FlexComponent.Alignment.END);
            return both;
        }
        return cost;
    }

    private static Span cost(double isk, boolean cheaper) {
        Span span = new Span(Isk.format(isk));
        if (cheaper) {
            span.getStyle().set("color", "var(--lumo-success-text-color)");
        }
        return span;
    }

    private static Span badge(String text, String theme) {
        Span badge = new Span(text);
        badge.getElement().getThemeList().add("badge " + theme);
        return badge;
    }

    private static Div stat(String title, Span value, Span description) {
        Span label = new Span(title);
        label.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size",
                "var(--lumo-font-size-s)");
        value.getStyle().set("font-size", "var(--lumo-font-size-xxl)").set("font-weight", "700");
        Div box = new Div(label, new Div(value));
        if (description != null) {
            description.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size",
                    "var(--lumo-font-size-s)");
            box.add(new Div(description));
        }
        box.getStyle().set("padding", "var(--lumo-space-m)").set("flex", "1 1 12rem")
                .set("background", "var(--lumo-contrast-5pct)").set("border-radius", "var(--lumo-border-radius-l)");
        return box;
    }
}
