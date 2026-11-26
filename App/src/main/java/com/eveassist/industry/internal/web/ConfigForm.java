package com.eveassist.industry.internal.web;

import com.eveassist.industry.FacilityId;
import com.eveassist.industry.internal.domain.PriceSide;
import com.eveassist.industry.internal.domain.ProjectBuilder;
import com.eveassist.industry.internal.domain.ProjectConfig;
import com.eveassist.industry.internal.domain.ProjectSettings;
import com.eveassist.industry.internal.domain.ProjectSettings.QuoteMode;
import com.eveassist.industry.internal.costing.domain.SplitPolicy;
import com.eveassist.industry.internal.costing.domain.SplitStrategy;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The project's settings as a form sends them. A form that sends only some of the fields (the quote card sends the mode, the margin and the
 * premium, the settings card the rest) leaves the others as they are, so one address serves both.
 */
record ConfigForm(
        Long expectedVersion, BigDecimal iskPerHour, BigDecimal iskPerCubicMeter, BigDecimal contractFee, BigDecimal quoteStep,
        String mode, BigDecimal marginPercent, BigDecimal premiumPercent, String priceSide, String defaultFacility,
        Integer industry, Integer advancedIndustry, BigDecimal implantTimePercent, String splitStrategy, String maxJobDays) {

    private static final BigDecimal SECONDS_PER_DAY = BigDecimal.valueOf(86_400);
    /** Far above the game's 30 days for one job: a bound above this is a mistake, and a huge one would not fit the seconds. */
    private static final BigDecimal MOST_DAYS = BigDecimal.valueOf(3_650);

    /** The longest job as the form shows it, in days, or an empty string for no bound. Five decimals give back the same seconds. */
    static String days(Long seconds) {
        return seconds == null ? ""
                : BigDecimal.valueOf(seconds).divide(SECONDS_PER_DAY, 5, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static Long seconds(String days) {
        if (days.isBlank()) {
            return null;
        }
        var typed = new BigDecimal(days.strip());
        if (typed.signum() <= 0 || typed.compareTo(MOST_DAYS) > 0) {
            throw new IllegalArgumentException("The longest job is above 0 and at most " + MOST_DAYS + " days, was " + days);
        }
        return typed.multiply(SECONDS_PER_DAY).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

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
        var policy = current.defaultPolicy();
        var strategy = splitStrategy == null || splitStrategy.isBlank() ? policy.strategy()
                : SplitStrategy.valueOf(splitStrategy.strip().toUpperCase(Locale.ROOT));
        // an absent field keeps the bound, a present but empty one clears it
        var longest = maxJobDays == null ? policy.maxJobSeconds() : seconds(maxJobDays);
        return current.withSettings(changed).withBuilder(changedBuilder).withPriceSide(side).withDefaultFacility(facility)
                .withDefaultPolicy(new SplitPolicy(strategy, longest));
    }

    private static BigDecimal or(BigDecimal sent, BigDecimal kept) {
        return sent == null ? kept : sent;
    }
}
