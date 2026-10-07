package de.craftplay.scratchcards.util;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public final class ServerDayUtil {
    private ServerDayUtil() {
    }

    public static long currentServerDayStartMillis() {
        return serverDayStartMillis(System.currentTimeMillis());
    }

    public static long serverDayStartMillis(long timestamp) {
        return serverDayCalendar(timestamp).getTimeInMillis();
    }

    public static long nextServerDayStartMillis(long timestamp) {
        Calendar calendar = serverDayCalendar(timestamp);
        // Ein Kalendertag statt 24 Stunden, damit die Zeitumstellung korrekt bleibt.
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        return calendar.getTimeInMillis();
    }

    public static String countdownUntilNextServerDay(long timestamp) {
        long milliseconds = Math.max(0, nextServerDayStartMillis(timestamp) - timestamp);
        long seconds = (milliseconds + 999) / 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    private static Calendar serverDayCalendar(long timestamp) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTimeInMillis(timestamp);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    public static long previousServerDayStartMillis() {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTimeInMillis(currentServerDayStartMillis());
        calendar.add(Calendar.DAY_OF_YEAR, -1);
        return calendar.getTimeInMillis();
    }
}
