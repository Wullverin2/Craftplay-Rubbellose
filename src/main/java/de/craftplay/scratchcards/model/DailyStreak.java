package de.craftplay.scratchcards.model;

public record DailyStreak(
        int current,
        int best,
        long lastDayStart
) {
    public static DailyStreak empty() {
        return new DailyStreak(0, 0, 0L);
    }
}
