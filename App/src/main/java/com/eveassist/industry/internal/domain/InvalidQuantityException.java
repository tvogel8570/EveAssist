package com.eveassist.industry.internal.domain;

public class InvalidQuantityException extends RuntimeException {

    public InvalidQuantityException(long quantity) {
        super("Quantity out of range: " + quantity);
    }
}
