package de.craftplay.scratchcards.model;

import java.util.UUID;

public record RiskOffer(
        UUID playerId,
        String playerName,
        double amount,
        String rewardName,
        long expiresAt
) {
    public boolean expired(long now) {
        return expiresAt > 0L && now > expiresAt;
    }
}
