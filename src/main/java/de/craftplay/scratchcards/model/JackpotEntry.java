package de.craftplay.scratchcards.model;

public record JackpotEntry(
        String playerName,
        String typeId,
        String rewardId,
        String rewardName,
        double money,
        long createdAt
) {
}
