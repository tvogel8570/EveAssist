package com.eveassist.identity;

import java.util.Objects;

/**
 * Who a use case acts for when nobody is signed in, such as a plan tried without an account. It says only where the
 * request came from, so that what a visitor may ask for can be limited. It gives access to nothing that belongs to an
 * account, which is why no repository takes one.
 *
 * @param address the network address the request came from
 */
public record Visitor(String address) {

    public Visitor {
        Objects.requireNonNull(address);
    }
}
