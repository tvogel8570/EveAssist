package com.eveassist.spike.industry.internal.domain;

import com.eveassist.spike.identity.Viewer;
import com.eveassist.spike.industry.PlanId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class Plan {
    public static final String PRICE_SNAPSHOT = "prices-1";
    public static final Instant PRICES_AS_OF = Instant.parse("2026-10-30T00:00:00Z");

    private final PlanId id;
    private final String ownerAccountId;
    private final String name;
    private final List<PlanLine> lines;
    private long version = 1;

    public Plan(PlanId id, String ownerAccountId, String name, List<PlanLine> lines) {
        this.id = id;
        this.ownerAccountId = ownerAccountId;
        this.name = name;
        this.lines = List.copyOf(lines);
    }

    public boolean isVisibleTo(Viewer viewer) {
        return ownerAccountId.equals(viewer.accountId());
    }

    public PlanLine changeLineQuantity(UUID lineId, int quantity) {
        PlanLine line = lines.stream().filter(l -> l.id().equals(lineId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No line " + lineId));
        line.changeQuantity(quantity);
        version++;
        return line;
    }

    public long totalCost() {
        return lines.stream().mapToLong(PlanLine::lineCost).sum();
    }

    public PlanId id() {
        return id;
    }

    public String ownerAccountId() {
        return ownerAccountId;
    }

    public String name() {
        return name;
    }

    public List<PlanLine> lines() {
        return lines;
    }

    public long version() {
        return version;
    }
}
