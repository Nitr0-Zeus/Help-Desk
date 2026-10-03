package com.helpdesk.sla;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessCalendarTest {

    private final BusinessCalendar calendar = new BusinessCalendar("America/Sao_Paulo");

    // 2026-01-05 é segunda-feira; BRT = UTC-3 (jan, sem DST desde 2019)
    private final Instant monday9 = Instant.parse("2026-01-05T12:00:00Z");

    @Test
    void addsHoursWithinSameDay() {
        Instant result = calendar.addWorkingHours(monday9, 2);
        assertThat(result).isEqualTo(Instant.parse("2026-01-05T14:00:00Z")); // 11:00 BRT
    }

    @Test
    void startsAtOpeningWhenBeforeOpen() {
        Instant monday6 = Instant.parse("2026-01-05T09:00:00Z"); // 06:00 BRT
        Instant result = calendar.addWorkingHours(monday6, 1);
        assertThat(result).isEqualTo(Instant.parse("2026-01-05T13:00:00Z")); // 10:00 BRT
    }

    @Test
    void skipsWeekend() {
        // sexta 17:00 BRT fecha às 18h (1h útil restante) + 3h na segunda = 12:00 BRT segunda
        Instant friday17 = Instant.parse("2026-01-09T20:00:00Z");
        Instant result = calendar.addWorkingHours(friday17, 4);
        assertThat(result).isEqualTo(Instant.parse("2026-01-12T15:00:00Z")); // 12:00 BRT segunda
    }

    @Test
    void rollsOverEndOfBusinessDay() {
        Instant friday17 = Instant.parse("2026-01-09T20:00:00Z"); // 17:00 BRT sexta
        Instant result = calendar.addWorkingHours(friday17, 2);
        assertThat(result).isEqualTo(Instant.parse("2026-01-12T13:00:00Z")); // 10:00 BRT segunda
    }

    @Test
    void countsMinutesBetweenAcrossWeekend() {
        // sex 17:00 BRT (1h até 18h) + seg até 10:00 BRT (1h) = 120 min
        Instant from = Instant.parse("2026-01-09T20:00:00Z");
        Instant to = Instant.parse("2026-01-12T13:00:00Z");
        assertThat(calendar.workingMinutesBetween(from, to)).isEqualTo(120);
    }

    @Test
    void ignoresNightHours() {
        // sex 17:00 -> 18:00 = 60 min; depois é madrugada
        Instant from = Instant.parse("2026-01-09T20:00:00Z");
        Instant to = Instant.parse("2026-01-10T03:00:00Z");
        assertThat(calendar.workingMinutesBetween(from, to)).isEqualTo(60);
    }

    @Test
    void zeroOrNegativeShiftReturnsStart() {
        assertThat(calendar.addWorkingHours(monday9, 0)).isEqualTo(monday9);
        assertThat(calendar.addWorkingHours(monday9, -5)).isEqualTo(monday9);
    }

    @Test
    void fullDayShiftSpansTwoDays() {
        // 9h úteis a partir das 09:00 seg = fecha 18:00 (9h) → mesmo dia 18:00
        Instant result = calendar.addWorkingHours(monday9, 9);
        assertThat(result).isEqualTo(Instant.parse("2026-01-05T21:00:00Z")); // 18:00 BRT
        // 10h = 9h na segunda + 1h na terça às 10:00 BRT
        Instant result10 = calendar.addWorkingHours(monday9, 10);
        assertThat(result10).isEqualTo(Instant.parse("2026-01-06T13:00:00Z"));
    }
}
