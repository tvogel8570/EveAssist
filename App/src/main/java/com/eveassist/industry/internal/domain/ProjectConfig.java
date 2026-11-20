package com.eveassist.industry.internal.domain;

import com.eveassist.industry.FacilityId;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * What a project is costed and quoted with, apart from what it makes: the rates and the quote, the price side, how runs are split by
 * default, the default facility, the builder, and the buyer the Draft is being worked up for.
 *
 * @param defaultFacility the facility for items that choose none, or null
 * @param buyerName the buyer the Draft is for, kept for the quote that is issued from it, or null
 * @param note a note for that quote, or null
 */
@ValueObject
public record ProjectConfig(
        ProjectSettings settings, PriceSide priceSide, SplitPolicy defaultPolicy, FacilityId defaultFacility,
        ProjectBuilder builder, String buyerName, String note) {

    public ProjectConfig {
        if (settings == null || priceSide == null || defaultPolicy == null || builder == null) {
            throw new IllegalArgumentException("A project needs settings, a price side, a split policy and a builder");
        }
    }

    /** Where a new project starts: the usual rates, Jita sell, least cost, no facility, no skills. */
    public static ProjectConfig defaults() {
        return new ProjectConfig(ProjectSettings.defaults(), PriceSide.SELL, SplitPolicy.LEAST_COST, null,
                ProjectBuilder.NONE, null, null);
    }

    public ProjectConfig withSettings(ProjectSettings value) {
        return new ProjectConfig(value, priceSide, defaultPolicy, defaultFacility, builder, buyerName, note);
    }

    public ProjectConfig withPriceSide(PriceSide value) {
        return new ProjectConfig(settings, value, defaultPolicy, defaultFacility, builder, buyerName, note);
    }

    public ProjectConfig withDefaultPolicy(SplitPolicy value) {
        return new ProjectConfig(settings, priceSide, value, defaultFacility, builder, buyerName, note);
    }

    public ProjectConfig withDefaultFacility(FacilityId value) {
        return new ProjectConfig(settings, priceSide, defaultPolicy, value, builder, buyerName, note);
    }

    public ProjectConfig withBuilder(ProjectBuilder value) {
        return new ProjectConfig(settings, priceSide, defaultPolicy, defaultFacility, value, buyerName, note);
    }

    public ProjectConfig withBuyer(String name, String text) {
        return new ProjectConfig(settings, priceSide, defaultPolicy, defaultFacility, builder, name, text);
    }
}
