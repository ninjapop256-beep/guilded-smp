package com.guildedsmp.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationsTest {

    @Test
    void parsesSingleUnits() {
        assertEquals(Optional.of(Duration.ofSeconds(30)), Durations.parse("30s"));
        assertEquals(Optional.of(Duration.ofMinutes(10)), Durations.parse("10m"));
        assertEquals(Optional.of(Duration.ofHours(2)), Durations.parse("2h"));
        assertEquals(Optional.of(Duration.ofDays(7)), Durations.parse("7d"));
        assertEquals(Optional.of(Duration.ofDays(14)), Durations.parse("2w"));
    }

    @Test
    void parsesCombinationsAndCase() {
        assertEquals(Optional.of(Duration.ofHours(36).plusMinutes(30)), Durations.parse("1d12h30m"));
        assertEquals(Optional.of(Duration.ofHours(1)), Durations.parse(" 1H "));
    }

    @Test
    void rejectsInvalidInput() {
        for (String bad : new String[]{null, "", "abc", "10", "10x", "-5m", "0s", "m10", "1d 2h", "5m!", "99999999999999999999s"}) {
            assertTrue(Durations.parse(bad).isEmpty(), "should reject: " + bad);
        }
    }

    @Test
    void formatsAtMostThreeUnits() {
        assertEquals("0s", Durations.format(Duration.ZERO));
        assertEquals("1d 12h 30m", Durations.format(Duration.ofHours(36).plusMinutes(30).plusSeconds(5)));
        assertEquals("45s", Durations.format(Duration.ofSeconds(45)));
        assertEquals("1w", Durations.format(Duration.ofDays(7)));
    }
}
