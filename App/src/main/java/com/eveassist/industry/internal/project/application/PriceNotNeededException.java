package com.eveassist.industry.internal.project.application;

/** The item is not one the project prices: it is not a product, a bought part or a material of what it builds, so there is no price to type for it. */
public class PriceNotNeededException extends RuntimeException {

    public PriceNotNeededException() {
        super("The project does not price this item");
    }
}
