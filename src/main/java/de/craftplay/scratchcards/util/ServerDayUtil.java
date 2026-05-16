package de.craftplay.scratchcards.util;

import java.util.Calendar;
import java.util.TimeZone;

public final class ServerDayUtil {
    private ServerDayUtil() {
    }

    public static long currentServerDayStartMillis() {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    public static long previousServerDayStartMillis() {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTimeInMillis(currentServerDayStartMillis());
        calendar.add(Calendar.DAY_OF_YEAR, -1);
        return calendar.getTimeInMillis();
    }
}
