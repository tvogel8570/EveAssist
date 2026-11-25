package com.eveassist.industry.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The duration the way the game's Industry window writes it, as in readings G11 (1D 01:34:05) and G12 (00:41:47). */
class DurationsTest {

    private final Durations durations = new Durations();

    @Test
    void aDurationIsWrittenAsTheGamesWindowWritesIt() {
        assertThat(durations.clock(92_045)).isEqualTo("1D 01:34:05");
        assertThat(durations.clock(2_507)).isEqualTo("00:41:47");
        assertThat(durations.clock(0)).isEqualTo("00:00:00");
        assertThat(durations.clock(86_400)).isEqualTo("1D 00:00:00");
        assertThat(durations.clock(86_399)).isEqualTo("23:59:59");
        // the Arazu of readings G14 and G15
        assertThat(durations.clock(113_165)).isEqualTo("1D 07:26:05");
        assertThat(durations.clock(112_022)).isEqualTo("1D 07:07:02");
    }
}
