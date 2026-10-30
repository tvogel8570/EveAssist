package com.eveassist.spike.industry.internal.application;

import com.eveassist.spike.industry.PlanId;
import java.util.UUID;

/** Internal until another module or an API write needs it; it then moves to the module's base package. */
public record ChangeLineQuantityCommand(PlanId planId, UUID lineId, int quantity) {
}
