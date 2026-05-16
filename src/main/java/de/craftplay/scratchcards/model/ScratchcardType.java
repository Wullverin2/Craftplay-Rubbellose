package de.craftplay.scratchcards.model;

import org.bukkit.Material;

import java.util.List;

public record ScratchcardType(
        String id,
        String displayName,
        Material material,
        Material shopMaterial,
        double price,
        boolean buyable,
        long availableFromMillis,
        long expiresAtMillis,
        List<Reward> rewards
) {
    public boolean isActive(long nowMillis) {
        return (availableFromMillis <= 0L || nowMillis >= availableFromMillis)
                && (expiresAtMillis <= 0L || nowMillis <= expiresAtMillis);
    }

    public boolean startsInFuture(long nowMillis) {
        return availableFromMillis > 0L && nowMillis < availableFromMillis;
    }

    public boolean isExpired(long nowMillis) {
        return expiresAtMillis > 0L && nowMillis > expiresAtMillis;
    }

    public Reward rewardById(String rewardId) {
        return rewards.stream()
                .filter(reward -> reward.id().equalsIgnoreCase(rewardId))
                .findFirst()
                .orElse(rewards.isEmpty() ? null : rewards.getFirst());
    }
}
