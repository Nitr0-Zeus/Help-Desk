package com.helpdesk.sla;

import com.helpdesk.ticket.Priority;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SlaCalculatorTest {

    private final BusinessCalendar calendar = new BusinessCalendar("America/Sao_Paulo");

    private final SlaCalculator calculator = new SlaCalculator(calendar, Map.of(
            Priority.BAIXA, new SlaCalculator.Policy(8, 72),
            Priority.MEDIA, new SlaCalculator.Policy(4, 48),
            Priority.ALTA, new SlaCalculator.Policy(2, 24),
            Priority.CRITICA, new SlaCalculator.Policy(1, 8)));

    private final Instant monday9 = Instant.parse("2026-01-05T12:00:00Z"); // 09:00 BRT

    @Test
    void calculatesDeadlinesFromPriority() {
        SlaCalculator.SlaDeadlines deadlines = calculator.calculate(Priority.ALTA, monday9);
        assertThat(deadlines.firstResponseDueAt())
                .isEqualTo(Instant.parse("2026-01-05T14:00:00Z")); // +2h = 11:00 BRT
        assertThat(deadlines.dueAt())
                .isEqualTo(Instant.parse("2026-01-07T18:00:00Z")); // +24h uteis = qua 15:00 BRT
    }

    @Test
    void breachOnlyWhenPastDeadlineAndUnresolved() {
        SlaCalculator.SlaDeadlines d = calculator.calculate(Priority.CRITICA, monday9);
        var view = new SlaCalculator.TicketSlaView(
                d.firstResponseDueAt(), d.dueAt(), null, null, null, false);
        assertThat(calculator.isBreached(view, monday9.plusSeconds(30 * 60L))).isFalse();
        assertThat(calculator.isBreached(view, monday9.plusSeconds(2 * 3600L))).isTrue();
    }

    @Test
    void resolvedTicketIsNeverBreached() {
        SlaCalculator.SlaDeadlines d = calculator.calculate(Priority.CRITICA, monday9);
        var view = new SlaCalculator.TicketSlaView(
                d.firstResponseDueAt(), d.dueAt(), monday9.plusSeconds(600),
                monday9.plusSeconds(3600), null, false);
        assertThat(calculator.isBreached(view, monday9.plusSeconds(10 * 3600L))).isFalse();
    }

    @Test
    void firstResponseAlreadyGivenOnlyChecksResolutionDeadline() {
        SlaCalculator.SlaDeadlines d = calculator.calculate(Priority.CRITICA, monday9);
        var view = new SlaCalculator.TicketSlaView(
                d.firstResponseDueAt(), d.dueAt(), monday9.plusSeconds(600),
                null, null, false);
        assertThat(calculator.isBreached(view, monday9.plusSeconds(90 * 60L))).isFalse();
        assertThat(calculator.isBreached(view, monday9.plusSeconds(10 * 3600L))).isTrue();
    }

    @Test
    void shiftsDeadlinesByPausedMinutes() {
        SlaCalculator.SlaDeadlines d = calculator.calculate(Priority.MEDIA, monday9);
        Instant shifted = calculator.shift(d.dueAt(), 90);
        assertThat(shifted).isEqualTo(Instant.parse("2026-01-12T16:30:00Z"));
    }
}
