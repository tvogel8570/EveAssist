package com.eveassist.spike.plan;

import java.util.Locale;

/** Formats ISK amounts the way players read them: 1.23B, 45.6M, 7.8k. */
public final class Isk {

    private Isk() {
    }

    public static String format(double isk) {
        double abs = Math.abs(isk);
        String sign = isk < 0 ? "-" : "";
        if (abs >= 1e9) {
            return sign + String.format(Locale.ROOT, "%.2fB", abs / 1e9);
        }
        if (abs >= 1e6) {
            return sign + String.format(Locale.ROOT, "%.1fM", abs / 1e6);
        }
        if (abs >= 1e3) {
            return sign + String.format(Locale.ROOT, "%.1fk", abs / 1e3);
        }
        return sign + String.format(Locale.ROOT, "%.0f", abs);
    }

    public static String quantity(long quantity) {
        return String.format(Locale.ROOT, "%,d", quantity);
    }
}
