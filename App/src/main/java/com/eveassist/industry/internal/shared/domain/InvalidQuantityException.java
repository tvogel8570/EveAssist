package com.eveassist.industry.internal.shared.domain;

public class InvalidQuantityException extends RuntimeException {

    public InvalidQuantityException(long quantity) {
        super("Quantity out of range: " + quantity);
    }
}
