package com.eveassist.inventory.internal;

import java.util.Set;

/** How an item is held, which decides whether it counts as loose stock. Stored as the constant's name. */
enum Held {
    HANGAR, IN_CONTAINER, IN_SHIP, FITTED, DELIVERIES, ASSET_SAFETY;

    private static final Set<String> SHIP_HOLDS = Set.of("Cargo", "DroneBay", "FighterBay", "FleetHangar", "ShipHangar",
            "InfrastructureHangar", "FrigateEscapeBay");
    private static final String[] FITTED_PREFIXES = {"HiSlot", "MedSlot", "LoSlot", "RigSlot", "SubSystem", "ServiceSlot"};

    /**
     * Worked out from the item's location flag and how deep it is. Asset safety and deliveries are decided by the
     * flag at any depth, because items inside an asset safety wrap are still in asset safety.
     */
    static Held of(String locationFlag, int depth) {
        if ("AssetSafety".equals(locationFlag)) {
            return ASSET_SAFETY;
        }
        if ("Deliveries".equals(locationFlag)) {
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
