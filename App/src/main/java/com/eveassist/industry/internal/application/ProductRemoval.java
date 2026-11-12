package com.eveassist.industry.internal.application;

import com.eveassist.sde.TypeId;

/**
 * What a removal leaves: the whole plan as it is now, and what was taken out, so the page can offer to put it back with
 * the same number of units.
 */
public record ProductRemoval(PlanDetails plan, TypeId type, String name, long quantity) {
}
