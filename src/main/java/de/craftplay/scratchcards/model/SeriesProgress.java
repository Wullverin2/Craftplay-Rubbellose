package de.craftplay.scratchcards.model;

public record SeriesProgress(
        String seriesId,
        String displayName,
        int collected,
        int required,
        boolean completed
) {
}
