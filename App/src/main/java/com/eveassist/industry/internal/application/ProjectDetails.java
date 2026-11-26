package com.eveassist.industry.internal.application;

import com.eveassist.industry.FacilityId;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.domain.ProjectConfig;
import com.eveassist.industry.internal.domain.ProjectStatus;
import com.eveassist.industry.internal.costing.domain.SplitPolicy;
import com.eveassist.industry.internal.facility.application.FacilityOverview;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Everything a project page shows, in one read: the project with names, the items it builds and how each is made, the facilities it can
 * use, its quotes and its costing. It is read with no side effect and never recalculates the costing, which is on request only.
 *
 * @param sourcePlanName the plan it started from, or null if it was started without one or that plan has gone
 * @param costing null if there is nothing to show: a Draft that was never costed, or a Draft made from a quote that is not costed yet
 */
public record ProjectDetails(
        ProjectId id, String name, long version, ProjectStatus status, boolean archived, int lastQuoteNumber, Instant startedAt,
        String sourcePlanName, ProjectConfig config, List<ProductLine> products, List<PartLine> parts, List<ItemLine> items,
        List<FacilityOverview> facilities, List<ProjectQuoteRepository.QuoteSummary> quotes, CostingView costing,
        Map<Integer, String> warningTypeNames, List<TypedPrice> typedPrices,
        List<SkillLine> skills) {

    public ProjectDetails {
        products = List.copyOf(products);
        parts = List.copyOf(parts);
        items = List.copyOf(items);
        facilities = List.copyOf(facilities);
        quotes = List.copyOf(quotes);
        warningTypeNames = Map.copyOf(warningTypeNames);
        typedPrices = List.copyOf(typedPrices);
        skills = List.copyOf(skills);
    }

    /** A price the user typed for an item: its name, the price of one unit, and when it was typed. */
    public record TypedPrice(int type, String name, java.math.BigDecimal price, Instant asOf) {
    }

    /** The typed price of an item, or null when its price is the market's (or it has none). */
    public TypedPrice typedPrice(int type) {
        return typedPrices.stream().filter(typed -> typed.type() == type).findFirst().orElse(null);
    }

    public record ProductLine(int type, String name, long quantity) {
    }

    /** A part of the products with a blueprint, and whether the project buys it. */
    public record PartLine(int type, String name, boolean bought) {
    }

    /**
     * An item the project builds (a product or a part that is not bought) and how it is made. {@code configured} is false for an item the
     * developer has not chosen anything for, which is shown at the plan's ME 10 and TE 20 in the default facility.
     */
    public record ItemLine(
            int type, String name, String group, boolean product, boolean configured, String blueprintSource, int me, int te,
            BigDecimal blueprintCostPerRun, FacilityId facility, SplitPolicy policy) {
    }

    /** A skill that some built item's blueprint requires and that shortens its time: the builder's level (0 if not typed in) and what each level saves. */
    public record SkillLine(int skill, String name, int level, BigDecimal percentPerLevel) {
    }
}
