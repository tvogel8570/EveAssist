package com.eveassist.industry.internal.application;

import com.eveassist.industry.ProjectId;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The user types the price of one unit of an item for a project, which is for an item the market has no price for, or too little of one (a Jita split needs
 * both sides). The price is to the cent, as Jita's are, and from one cent to a thousand trillion ISK, whatever the page allowed.
 */
public record TypePriceCommand(ProjectId project, TypeId type, BigDecimal price, long expectedVersion) {

    public static final BigDecimal MIN = new BigDecimal("0.01");
    public static final BigDecimal MAX = new BigDecimal("1000000000000000");

    public TypePriceCommand {
        if (price == null) {
            throw new InvalidPriceException();
        }
        price = price.setScale(2, RoundingMode.HALF_UP);
        if (price.compareTo(MIN) < 0 || price.compareTo(MAX) > 0) {
            throw new InvalidPriceException();
        }
    }
}
