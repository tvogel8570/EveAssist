package com.eveassist.market;

import java.time.Instant;

/**
 * @param listedAt when the region's contract list was last confirmed current, or null if it never was
 * @param pending  contracts whose lines have not been read yet, so prices may miss some offers
 * @param withBlueprintOriginals contracts set aside because they hold a blueprint original
 * @param withAbyssalItems       contracts set aside because they hold an Abyssal item
 */
public record ContractStatus(Instant listedAt, int pending, int withBlueprintOriginals, int withAbyssalItems) {
}
