package com.eveassist.identity;

import java.util.Objects;

/** The signed-in account a use case acts for. A page controller receives it as a method parameter. */
public record Viewer(AccountId accountId) {

    public Viewer {
        Objects.requireNonNull(accountId);
    }
}
