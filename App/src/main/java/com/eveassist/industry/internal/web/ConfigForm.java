package com.eveassist.industry.internal.web;

import com.eveassist.industry.FacilityId;
import com.eveassist.industry.internal.domain.PriceSide;
import com.eveassist.industry.internal.domain.ProjectBuilder;
import com.eveassist.industry.internal.domain.ProjectConfig;
import com.eveassist.industry.internal.domain.ProjectSettings;
import com.eveassist.industry.internal.domain.ProjectSettings.QuoteMode;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The project's settings as a form sends them. A form that sends only some of the fields (the quote card sends the mode, the margin and the
 * premium, the settings card the rest) leaves the others as they are, so one address serves both.
 */
record ConfigForm(
        long expectedVersion, BigDecimal iskPerHour, BigDecimal iskPerCubicMeter, BigDecimal contractFee, BigDecimal quoteStep,
        String mode, BigDecimal marginPercent, BigDecimal premiumPercent, String priceSide, String defaultFacility,
        Integer industry, Integer advancedIndustry, BigDecimal implantTimePercent) {

    /**
     * The configuration with what the form sent put in, and the skill levels it sent, which are fields named by their skill. The domain refuses a value
     * outside its range with an IllegalArgumentException.
     */
    ProjectConfig applyTo(ProjectConfig current, Map<TypeId, Integer> sentSkills) {
        var settings = current.settings();
        var changed = new ProjectSettings(
                or(iskPerHour, settings.iskPerHour()), or(iskPerCubicMeter, settings.iskPerCubicMeter()),
                or(contractFee, settings.contractFee()),
                mode == null || mode.isBlank() ? settings.mode() : QuoteMode.valueOf(mode.strip().toUpperCase(Locale.ROOT)),
                or(marginPercent, settings.marginPercent()), or(premiumPercent, settings.premiumPercent()),
                or(quoteStep, settings.quoteStep()));
        var builder = current.builder();
        var skills = new HashMap<>(builder.skillLevels());
        skills.putAll(sentSkills);
        var changedBuilder = new ProjectBuilder(industry == null ? builder.industry() : industry,
                advancedIndustry == null ? builder.advancedIndustry() : advancedIndustry,
                or(implantTimePercent, builder.implantTimePercent()), skills);
        var side = priceSide == null || priceSide.isBlank() ? current.priceSide()
                : PriceSide.valueOf(priceSide.strip().toUpperCase(Locale.ROOT));
        // an absent field keeps the facility, a present but empty one clears it ("no default facility")
        var facility = defaultFacility == null ? current.defaultFacility()
                : defaultFacility.isBlank() ? null : new FacilityId(UUID.fromString(defaultFacility.strip()));
        return current.withSettings(changed).withBuilder(changedBuilder).withPriceSide(side).withDefaultFacility(facility);
    }

    private static BigDecimal or(BigDecimal sent, BigDecimal kept) {
        return sent == null ? kept : sent;
    }
}
