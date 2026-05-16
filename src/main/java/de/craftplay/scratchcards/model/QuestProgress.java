package de.craftplay.scratchcards.model;

public record QuestProgress(
        String questId,
        String displayName,
        int progress,
        int target,
        boolean completed
) {
}
