package com.eveassist.spike.identity;

import java.util.Objects;

/** Who is asking. Resolved from a session on pages and from a bearer token in the API; faked here. */
public record Viewer(String accountId) {
    public Viewer {
        Objects.requireNonNull(accountId);
    }
}
