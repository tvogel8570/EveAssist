package com.eveassist.characters;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * What is known about the in-game corporation roles of linked characters. The {@code characters} module owns this port
 * and the module that learns the roles implements it, so that {@code characters} depends on nothing new.
 */
@org.jmolecules.architecture.hexagonal.SecondaryPort
public interface CharacterRoles {

    /**
     * For each of the characters whose roles are known, whether it is a Director of its corporation. A character whose
     * roles have not been read is left out, so that "not known" is never shown as "not a Director".
     */
    Map<UUID, Boolean> directors(Collection<UUID> characterIds);
}
