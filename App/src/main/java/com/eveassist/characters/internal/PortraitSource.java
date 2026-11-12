package com.eveassist.characters.internal;

import java.util.Optional;

/** Where a character's portrait comes from. Fetched once, when the character is linked. */
interface PortraitSource {

    /** Empty when the image server does not answer or answers with something that is not a small image. */
    Optional<byte[]> fetch(long eveCharacterId);
}
