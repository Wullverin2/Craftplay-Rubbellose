package de.craftplay.scratchcards.model;

public record GroupGoalProgress(
        String goalId,
        String displayName,
        long progress,
        long target,
        boolean completed
) {
}
