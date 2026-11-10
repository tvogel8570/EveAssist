package com.eveassist.inventory.internal;

import com.eveassist.esi.CharacterAsset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds where each asset really is. ESI lists an item with the id of what holds it, and that may be a container or ship
 * that is itself in the list. The top of the chain is the place: a station, a solar system or a structure. Real ESI types
 * an item in a structure as {@code item}, so the type of a location is never used (see esi.md); an item is at the top
 * when its location is not the id of another item of the same character.
 */
final class AssetClassifier {

    /** A chain longer than this is broken data, and the item is treated as being where the walk stopped. */
    private static final int LONGEST_CHAIN = 64;

    /**
     * @param rootLocationId  the place at the top of the chain
     * @param depth           0 for an item at the top
     * @param containerTypeId the type of what holds the item directly, null at the top
     */
    record Placed(CharacterAsset asset, long rootLocationId, int depth, Integer containerTypeId, Held held) {
    }

    private AssetClassifier() {
    }

    static List<Placed> classify(List<CharacterAsset> assets) {
        Map<Long, CharacterAsset> byItem = new HashMap<>(assets.size() * 2);
        for (var asset : assets) {
            byItem.put(asset.itemId(), asset);
        }
        return assets.stream().map(asset -> {
            long at = asset.locationId();
            int depth = 0;
            Integer container = null;
            var holder = byItem.get(at);
            while (holder != null && depth < LONGEST_CHAIN) {
                if (container == null) {
                    container = holder.typeId();
                }
                depth++;
                at = holder.locationId();
                holder = byItem.get(at);
            }
            return new Placed(asset, at, depth, container, Held.of(asset.locationFlag(), depth));
        }).toList();
    }
}
