package com.eveassist.market.internal;

import com.eveassist.market.JitaPrice;
import com.eveassist.sde.SdeService;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Made-up prices that stay the same for each item, until the ESI client exists. Dev only: they mean nothing. */
@Component
@Profile("dev")
class DevPriceSeeder implements ApplicationRunner {

    private final SdeService sde;
    private final PriceRepository prices;

    DevPriceSeeder(SdeService sde, PriceRepository prices) {
        this.sde = sde;
        this.prices = prices;
    }

    @Override
    public void run(ApplicationArguments args) {
        var now = Instant.now();
        var existing = prices.pricesFor(sde.materialTypes());
        List<JitaPrice> missing = sde.materialTypes().stream()
                .filter(type -> !existing.containsKey(type))
                .map(type -> new JitaPrice(type, stablePrice(type), now))
                .toList();
        prices.store(missing);
    }

    private static BigDecimal stablePrice(TypeId type) {
        return BigDecimal.valueOf(Math.floorMod(type.value() * 2654435761L, 9_000_000L) + 500L, 2);
    }
}
