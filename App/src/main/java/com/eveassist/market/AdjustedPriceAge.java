package com.eveassist.market;

import java.time.Instant;

/**
 * How old CCP's adjusted prices are, in the two senses a screen may want (the developer asked on 2026-11-18 for both to be kept):
 *
 * @param confirmedAt when the stored prices were last confirmed to be ESI's current copy, by a call to ESI or because ESI's own
 *         cache time had not run out. It moves forward each time a project is costed and nothing has changed, so it says how
 *         recently the app checked and not how old the numbers are
 * @param esiModifiedAt ESI's {@code Last-Modified} header for the list, as ESI sent it, or null if it sent none. **Do not read it as
 *         the age of the numbers.** On 2026-11-18 ESI sent a value 20 minutes later than its own {@code Date} header (and an
 *         {@code Expires} exactly an hour after it), and it moved by two hours between two readings, so it behaves as a label for ESI's
 *         cached copy and not as a clock reading. It can be ahead of this machine's clock. It is kept because a screen may want to show
 *         it, and until what ESI means by it is understood, {@code confirmedAt} is the one that is safe to show as an age
 */
public record AdjustedPriceAge(Instant confirmedAt, Instant esiModifiedAt) {
}
