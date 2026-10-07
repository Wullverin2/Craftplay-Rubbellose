package de.craftplay.scratchcards.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

class ServerDayUtilTest {
    @Test
    void countdownEndsAtServerMidnightRatherThanTwentyFourHoursAfterClaiming() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Vienna"));
            long now = time("2026-10-07T22:15:30");
            assertEquals(time("2026-10-07T00:00:00"), ServerDayUtil.serverDayStartMillis(now));
            assertEquals(time("2026-10-08T00:00:00"), ServerDayUtil.nextServerDayStartMillis(now));
            assertEquals("01:44:30", ServerDayUtil.countdownUntilNextServerDay(now));
            assertEquals("00:00:01", ServerDayUtil.countdownUntilNextServerDay(time("2026-10-07T23:59:59.999")));
            assertEquals("24:00:00", ServerDayUtil.countdownUntilNextServerDay(time("2026-10-08T00:00:00")));
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "2026-03-29T00:00:00, 2026-03-30T00:00:00, 23:00:00",
            "2026-10-25T00:00:00, 2026-10-26T00:00:00, 25:00:00",
            "2026-12-31T23:30:00, 2027-01-01T00:00:00, 00:30:00"
    })
    void respectsDaylightSavingAndYearChanges(String now, String midnight, String countdown) {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Vienna"));
            assertEquals(time(midnight), ServerDayUtil.nextServerDayStartMillis(time(now)));
            assertEquals(countdown, ServerDayUtil.countdownUntilNextServerDay(time(now)));
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void followsTheJvmTimezoneRatherThanAFixedEuropeanTimezone() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            long timestamp = LocalDateTime.parse("2026-10-07T23:00:00")
                    .atZone(ZoneId.of("Asia/Tokyo")).toInstant().toEpochMilli();
            long next = LocalDateTime.parse("2026-10-08T00:00:00")
                    .atZone(ZoneId.of("Asia/Tokyo")).toInstant().toEpochMilli();
            assertEquals(next, ServerDayUtil.nextServerDayStartMillis(timestamp));
            assertEquals("01:00:00", ServerDayUtil.countdownUntilNextServerDay(timestamp));
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    private long time(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZoneId.of("Europe/Vienna")).toInstant().toEpochMilli();
    }
}
