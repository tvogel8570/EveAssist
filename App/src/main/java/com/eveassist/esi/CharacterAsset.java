package com.eveassist.esi;

/**
 * One item in a character's assets, from {@code GET /characters/{character_id}/assets}.
 *
 * @param itemId        unique among all items, so a container's contents point at it with their {@code locationId}
 * @param locationId    a station or structure id for an item at the top, a solar system id for an item in space, or
 *                      the {@code itemId} of the container, ship or similar that holds it
 * @param locationType  {@code station}, {@code solar_system}, {@code item} or {@code other} (a structure)
 * @param locationFlag  where in the location it is: {@code Hangar}, {@code Cargo}, {@code HiSlot0}, {@code Unlocked}...
 * @param singleton     true for an assembled item (a ship or container that has been unpackaged), false for a stack
 * @param blueprintCopy true or false for blueprints, null for everything else
 */
public record CharacterAsset(long itemId, int typeId, long quantity, long locationId, String locationType,
        String locationFlag, boolean singleton, Boolean blueprintCopy) {
}
