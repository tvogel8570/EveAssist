package com.eveassist.industry.internal.web;

import org.springframework.stereotype.Component;

/** Formats a length of time for templates, in the two largest units that are not zero, such as "2d 3h". */
@Component("durations")
class Durations {

    public String format(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3_600;
        long minutes = seconds % 3_600 / 60;
        long rest = seconds % 60;
        if (days > 0) {
            return hours > 0 ? days + "d " + hours + "h" : days + "d";
        }
        if (hours > 0) {
            return minutes > 0 ? hours + "h " + minutes + "m" : hours + "h";
        }
        if (minutes > 0) {
            return rest > 0 && minutes < 10 ? minutes + "m " + rest + "s" : minutes + "m";
        }
        return rest + "s";
    }
}
