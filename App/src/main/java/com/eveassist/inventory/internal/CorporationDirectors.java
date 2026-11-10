package com.eveassist.inventory.internal;

import com.eveassist.characters.CharacterRoles;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Tells the Characters page which linked characters are Directors, from what the corporation fetch learned. */
@Component
class CorporationDirectors implements CharacterRoles {

    private final CorporationRepository corporations;

    CorporationDirectors(CorporationRepository corporations) {
        this.corporations = corporations;
    }

    @Override
    public Map<UUID, Boolean> directors(Collection<UUID> characterIds) {
        var result = new HashMap<UUID, Boolean>();
        corporations.memberships(characterIds).forEach(m -> result.put(m.characterId(), m.director()));
        return result;
    }
}
