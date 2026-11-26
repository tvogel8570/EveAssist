package com.eveassist.industry.internal.project.application;

/** A typed price is missing, below a cent or above a thousand trillion ISK. */
public class InvalidPriceException extends RuntimeException {

    public InvalidPriceException() {
        super("A price is from 0.01 to 1,000,000,000,000,000 ISK");
    }
}
