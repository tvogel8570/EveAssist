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
     * @param division        the hangar division (1 to 7) of a corporation's item, null for a character's items and
     *                        for items outside an office
     */
    record Placed(CharacterAsset asset, long rootLocationId, int depth, Integer containerTypeId, Held held,
            Integer division) {
    }

    /** A corporation's office, an item that is only a folder for the items in its hangar divisions. */
    static final String OFFICE_FLAG = "OfficeFolder";

    private AssetClassifier() {
    }

    /** A character's assets. */
    static List<Placed> classify(List<CharacterAsset> assets) {
        return classify(assets, false);
    }

    /**
     * A corporation's assets. An office is not a container: it is left out of the result and of the chain, so an item in
     * it is at the station or structure the office stands in, held in a hangar. The flag of the item, or of the container
     * directly in the office that holds it, names the hangar division ({@code CorpSAG1} to {@code CorpSAG7}).
     */
    static List<Placed> classifyCorporation(List<CharacterAsset> assets) {
        return classify(assets, true);
    }

    private static List<Placed> classify(List<CharacterAsset> assets, boolean corporation) {
        Map<Long, CharacterAsset> byItem = new HashMap<>(assets.size() * 2);
        for (var asset : assets) {
            byItem.put(asset.itemId(), asset);
        }
        return assets.stream().filter(asset -> !(corporation && OFFICE_FLAG.equals(asset.locationFlag()))).map(asset -> {
            long at = asset.locationId();
            int depth = 0;
            int steps = 0;
            Integer container = null;
            Integer division = null;
            var child = asset;
            var holder = byItem.get(at);
            while (holder != null && steps++ < LONGEST_CHAIN) {
                if (corporation && OFFICE_FLAG.equals(holder.locationFlag())) {
                    division = divisionOf(child.locationFlag());
                } else {
                    if (container == null) {
                        container = holder.typeId();
                    }
                    depth++;
                }
                child = holder;
                at = holder.locationId();
                holder = byItem.get(at);
            }
            return new Placed(asset, at, depth, container, Held.of(asset.locationFlag(), depth), division);
        }).toList();
    }

    /** The item type of an office. An office is a folder, whatever flag it has (an impounded one is {@code Impounded}). */
    static final int OFFICE_TYPE_ID = 27;

    /**
     * The items whose names are worth asking for: assembled (singleton) items that hold something, such as a container or
     * a ship with cargo. A corporation's office is left out, because it is a folder and cannot be renamed: ESI refuses a
     * whole names call that has one in it ("Invalid IDs in the request"), and an impounded office, which has the flag
     * {@code Impounded}, is not recognised by its flag, so it is recognised by its type.
     */
    static List<Long> namedCandidates(List<CharacterAsset> assets) {
        var holders = new java.util.HashSet<Long>();
        assets.forEach(asset -> holders.add(asset.locationId()));
        return assets.stream()
                .filter(asset -> asset.singleton() && holders.contains(asset.itemId())
                        && !OFFICE_FLAG.equals(asset.locationFlag()) && asset.typeId() != OFFICE_TYPE_ID)
                .map(CharacterAsset::itemId).toList();
    }

    /** 1 to 7 for {@code CorpSAG1} to {@code CorpSAG7}, otherwise null. */
    static Integer divisionOf(String locationFlag) {
        if (locationFlag != null && locationFlag.length() == 8 && locationFlag.startsWith("CorpSAG")) {
            var digit = locationFlag.charAt(7) - '0';
            return digit >= 1 && digit <= 7 ? digit : null;
        }
        return null;
    }
}
