package de.craftplay.scratchcards.model;

public record RewardHistoryEntry(
        String typeId,
        String rewardId,
        String rewardName,
        double money,
        boolean jackpot,
        long createdAt
) {
}
