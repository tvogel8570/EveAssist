package com.eveassist.market.internal;

import com.eveassist.market.BpcQuote;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Prices a blueprint copy per run from the offers on public contracts: the volume-weighted average of the cheapest
 * offers that make up a share of the runs on offer, as the market {@link PriceRule} does, with runs as the volume.
 */
final class BpcPricing {

    /** Offers this many times above or below the middle one are taken as joke or mistaken prices and left out. */
    static final BigDecimal OUTLIER_FACTOR = BigDecimal.TEN;
    /** Fewer offers than this have no useful middle, so none are left out. */
    static final int MIN_OFFERS_FOR_OUTLIERS = 3;

    private BpcPricing() {
    }

    /**
     * Copies with exactly the ME and TE are used when there are any. Otherwise copies whose ME and TE are both at least
     * as high are used. Outliers are judged among all offers for the type, whatever their ME and TE.
     *
     * @param offers all offers for the type
     */
    static Optional<BpcQuote> quote(TypeId type, int me, int te, List<BpcOffer> offers, BigDecimal share, Instant asOf) {
        var sane = withoutOutliers(offers);
        var exact = sane.stream().filter(offer -> offer.me() == me && offer.te() == te).toList();
        var match = BpcQuote.Match.EXACT;
        var used = exact;
        if (used.isEmpty()) {
            used = sane.stream().filter(offer -> offer.me() >= me && offer.te() >= te).toList();
            match = BpcQuote.Match.AT_LEAST;
        }
        var book = used.stream().map(offer -> new PriceRule.BookOrder(offer.perRun(), offer.runs())).toList();
        var price = PriceRule.price(book, false, share);
        if (price.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new BpcQuote(type, me, te, price.get(), match, used.size(),
                used.stream().mapToLong(BpcOffer::runs).sum(), asOf));
    }

    static List<BpcOffer> withoutOutliers(List<BpcOffer> offers) {
        if (offers.size() < MIN_OFFERS_FOR_OUTLIERS) {
            return offers;
        }
        var sorted = offers.stream().map(BpcOffer::perRun).sorted(Comparator.naturalOrder()).toList();
        var middle = sorted.size() % 2 == 1
                ? sorted.get(sorted.size() / 2)
                : sorted.get(sorted.size() / 2 - 1).add(sorted.get(sorted.size() / 2))
                        .divide(BigDecimal.TWO, 4, java.math.RoundingMode.HALF_UP);
        var low = middle.divide(OUTLIER_FACTOR, 4, java.math.RoundingMode.HALF_UP);
        var high = middle.multiply(OUTLIER_FACTOR);
        return offers.stream()
                .filter(offer -> offer.perRun().compareTo(low) >= 0 && offer.perRun().compareTo(high) <= 0)
                .toList();
    }
}
