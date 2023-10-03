package com.eveassist.api.sde.pi.impl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

class LocalDateTimeTest {
    @Test
    void givenStringWithT_thenValidDateTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssz");
        String input = "2023-08-07T12:00:37Z";

        LocalDateTime ldt = LocalDateTime.parse(input, formatter);
        Assertions.assertThat(ldt).isNotNull();
    }
}
