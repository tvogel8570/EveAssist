package com.eveassist.inventory.internal;

import java.util.Set;

/** How an item is held, which decides whether it counts as loose stock. Stored as the constant's name. */
enum Held {
    HANGAR, IN_CONTAINER, IN_SHIP, FITTED, DELIVERIES, ASSET_SAFETY, IMPOUNDED;

    /** Every flag ESI gives to something that has been delivered and not yet taken: to a character, a corporation or a goal. */
    private static final Set<String> DELIVERY_FLAGS = Set.of("Deliveries", "CorpDeliveries", "CapsuleerDeliveries",
            "CorporationGoalDeliveries");
    private static final Set<String> SHIP_HOLDS = Set.of("Cargo", "DroneBay", "FighterBay", "FleetHangar", "ShipHangar",
            "InfrastructureHangar", "FrigateEscapeBay");
    private static final String[] FITTED_PREFIXES = {"HiSlot", "MedSlot", "LoSlot", "RigSlot", "SubSystem", "ServiceSlot"};

    /**
     * Worked out from the item's location flag and how deep it is. Asset safety and deliveries are decided by the
     * flag at any depth, because items inside an asset safety wrap are still in asset safety.
     */
    static Held of(String locationFlag, int depth) {
        return of(locationFlag, depth, false);
    }

    /**
     * As above. An item in an impounded office (the corporation lost the office, and its things wait there) is
     * {@link #IMPOUNDED}, and so is anything whose own flag is {@code Impounded}.
     */
    static Held of(String locationFlag, int depth, boolean inImpoundedOffice) {
        if ("AssetSafety".equals(locationFlag)) {
            return ASSET_SAFETY;
        }
        if (inImpoundedOffice || "Impounded".equals(locationFlag)) {
            return IMPOUNDED;
        }
        if (DELIVERY_FLAGS.contains(locationFlag)) {
            return DELIVERIES;
        }
        if (depth == 0) {
            return HANGAR;
        }
        for (var prefix : FITTED_PREFIXES) {
            if (locationFlag.startsWith(prefix)) {
                return FITTED;
            }
        }
        if (SHIP_HOLDS.contains(locationFlag) || locationFlag.startsWith("Specialized")
                || locationFlag.startsWith("FighterTube")) {
            return IN_SHIP;
        }
        return IN_CONTAINER;
    }
}
