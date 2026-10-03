package com.helpdesk.sla;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class BusinessCalendar {

    private static final LocalTime OPEN = LocalTime.of(9, 0);
    private static final LocalTime CLOSE = LocalTime.of(18, 0);

    private final ZoneId zone;

    public BusinessCalendar(@Value("${helpdesk.sla.zone}") String zoneId) {
        this.zone = ZoneId.of(zoneId);
    }

    public Instant addWorkingHours(Instant start, long hours) {
        return addWorkingMinutes(start, hours * 60);
    }

    public Instant addWorkingMinutes(Instant start, long minutes) {
        if (minutes <= 0) {
            return start;
        }
        LocalDateTime cursor = LocalDateTime.ofInstant(start, zone);
        long remaining = minutes;
        while (remaining > 0) {
            cursor = advanceToBusinessTime(cursor);
            LocalDate day = cursor.toLocalDate();
            LocalDateTime close = day.atTime(CLOSE);
            long available = Duration.between(cursor, close).toMinutes();
            if (available >= remaining) {
                return cursor.plusMinutes(remaining).atZone(zone).toInstant();
            }
            remaining -= available;
            cursor = day.plusDays(1).atStartOfDay();
        }
        return cursor.atZone(zone).toInstant();
    }

    public long workingMinutesBetween(Instant from, Instant to) {
        if (!to.isAfter(from)) {
            return 0;
        }
        LocalDateTime cursor = LocalDateTime.ofInstant(from, zone);
        LocalDateTime end = LocalDateTime.ofInstant(to, zone);
        long total = 0;
        while (cursor.isBefore(end)) {
            if (isBusinessDay(cursor)) {
                LocalDateTime dayStart = cursor.toLocalDate().atTime(OPEN);
                LocalDateTime dayClose = cursor.toLocalDate().atTime(CLOSE);
                LocalDateTime windowStart = cursor.isBefore(dayStart) ? dayStart : cursor;
                LocalDateTime windowEnd = end.isBefore(dayClose) ? end : dayClose;
                if (windowEnd.isAfter(windowStart)) {
                    total += Duration.between(windowStart, windowEnd).toMinutes();
                }
            }
            cursor = cursor.toLocalDate().plusDays(1).atTime(OPEN);
        }
        return total;
    }

    private LocalDateTime advanceToBusinessTime(LocalDateTime cursor) {
        while (!isBusinessDay(cursor)) {
            cursor = cursor.toLocalDate().plusDays(1).atStartOfDay();
        }
        LocalDateTime open = cursor.toLocalDate().atTime(OPEN);
        if (cursor.isBefore(open)) {
            return open;
        }
        if (!cursor.isBefore(cursor.toLocalDate().atTime(CLOSE))) {
            return cursor.toLocalDate().plusDays(1).atStartOfDay();
        }
        return cursor;
    }

    private boolean isBusinessDay(LocalDateTime dateTime) {
        DayOfWeek day = dateTime.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }
}
