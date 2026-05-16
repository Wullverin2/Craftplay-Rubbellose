package de.craftplay.scratchcards.model;

import org.bukkit.Material;

import java.util.List;

public record RewardItem(
        Material material,
        int amount,
        String name,
        List<String> lore
) {
}
