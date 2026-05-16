package de.craftplay.scratchcards.model;

import java.util.List;

public record ServerStats(
        long totalBought,
        double totalIncome,
        double totalPaid,
        List<PlayerStats> topWinners
) {
}
