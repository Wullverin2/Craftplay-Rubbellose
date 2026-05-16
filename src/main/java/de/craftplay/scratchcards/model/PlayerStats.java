package de.craftplay.scratchcards.model;

import java.util.UUID;

public record PlayerStats(
        UUID playerId,
        String playerName,
        int bought,
        int opened,
        double wonMoney,
        double bestWin,
        int jackpots
) {
    public static PlayerStats empty(UUID playerId, String playerName) {
        return new PlayerStats(playerId, playerName == null ? "Unbekannt" : playerName, 0, 0, 0.0D, 0.0D, 0);
    }
}
