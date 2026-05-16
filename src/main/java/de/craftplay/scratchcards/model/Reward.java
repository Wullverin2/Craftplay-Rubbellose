package de.craftplay.scratchcards.model;

import org.bukkit.Material;

import java.util.List;

public record Reward(
        String id,
        double chance,
        String rarity,
        String displayName,
        Material symbolMaterial,
        double money,
        List<RewardItem> items,
        List<String> commands,
        boolean broadcast
) {
    public boolean isWin() {
        return money > 0.0D || !items.isEmpty() || !commands.isEmpty();
    }
}
