package com.eveassist.spike.industry;

import java.util.UUID;

public record ChangeLineQuantityCommand(PlanId planId, UUID lineId, int quantity) {
}
