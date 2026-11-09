package com.eveassist.characters.internal;

import java.util.Optional;

/** The image server as a test sees it: a few bytes for every character, or nothing. */
class FakePortraitSource implements PortraitSource {

    static final byte[] IMAGE = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};

    boolean available = true;
    int fetches;

    void reset() {
        available = true;
        fetches = 0;
    }

    @Override
    public Optional<byte[]> fetch(long eveCharacterId) {
        fetches++;
        return available ? Optional.of(IMAGE) : Optional.empty();
    }
}
