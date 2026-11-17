package com.eveassist.esi;

/** A blueprint as ESI last gave it, from a character or corporation's blueprint list. */
public record CharacterBlueprint(long itemId, int typeId, long locationId, String locationFlag, long quantity,
        int materialEfficiency, int timeEfficiency, int runs) {
}