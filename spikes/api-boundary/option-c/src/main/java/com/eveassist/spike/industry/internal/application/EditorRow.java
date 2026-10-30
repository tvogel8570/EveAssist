package com.eveassist.spike.industry.internal.application;

import java.util.UUID;

/** Screen-only. */
public record EditorRow(UUID lineId, String itemName, int quantity, long unitPrice, long lineCost) {
}
