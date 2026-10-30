package com.eveassist.spike.htmx;

import com.eveassist.spike.plan.Isk;
import org.springframework.stereotype.Component;

/** Lets templates format numbers as {@code ${@isk.format(x)}}. */
@Component("isk")
public class IskFormat {

    public String format(double isk) {
        return Isk.format(isk);
    }

    public String quantity(long quantity) {
        return Isk.quantity(quantity);
    }
}
