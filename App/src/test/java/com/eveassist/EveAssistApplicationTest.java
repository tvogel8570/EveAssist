package com.eveassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TimeZone;
import org.junit.jupiter.api.Test;

/** What the application sets before it starts. */
class EveAssistApplicationTest {

    @Test
    void theJvmIsPutInUtcWhateverZoneTheMachineIsIn() {
        var original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));

            EveAssistApplication.useUtc();

            assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
