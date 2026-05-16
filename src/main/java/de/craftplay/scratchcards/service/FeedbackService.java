package de.craftplay.scratchcards.service;

import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.util.TextUtil;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

public final class FeedbackService {
    private final ConfigManager configManager;

    public FeedbackService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void play(Player player, String key) {
        if (!configManager.config().getBoolean("feedback.sounds.enabled", true)) {
            return;
        }
        String path = "feedback.sounds." + key;
        if (!configManager.config().getBoolean(path + ".enabled", true)) {
            return;
        }
        Sound sound = parseSound(configManager.config().getString(path + ".sound", ""));
        if (sound == null) {
            return;
        }
        float volume = (float) configManager.config().getDouble(path + ".volume", 1.0D);
        float pitch = (float) configManager.config().getDouble(path + ".pitch", 1.0D);
        player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
    }

    public void title(Player player, String key, Map<String, String> placeholders) {
        if (!configManager.config().getBoolean("feedback.titles.enabled", true)) {
            return;
        }
        String path = "feedback.titles." + key;
        if (!configManager.config().getBoolean(path + ".enabled", true)) {
            return;
        }
        String title = TextUtil.replace(configManager.config().getString(path + ".title", ""), placeholders);
        String subtitle = TextUtil.replace(configManager.config().getString(path + ".subtitle", ""), placeholders);
        int fadeIn = configManager.config().getInt(path + ".fade_in_ticks", 10);
        int stay = configManager.config().getInt(path + ".stay_ticks", 45);
        int fadeOut = configManager.config().getInt(path + ".fade_out_ticks", 10);
        player.sendTitle(TextUtil.color(title), TextUtil.color(subtitle), fadeIn, stay, fadeOut);
    }

    private Sound parseSound(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Sound.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
