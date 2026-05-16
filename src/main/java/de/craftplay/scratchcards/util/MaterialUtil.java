package de.craftplay.scratchcards.util;

import org.bukkit.Material;

public final class MaterialUtil {
    private MaterialUtil() {
    }

    public static Material parse(String value, Material fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            Material material = Material.valueOf(value.toUpperCase());
            return material.isAir() ? fallback : material;
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
