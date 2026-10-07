package de.craftplay.scratchcards.service;

import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.config.LanguageManager;
import de.craftplay.scratchcards.model.Reward;
import de.craftplay.scratchcards.model.ScratchcardType;
import de.craftplay.scratchcards.util.TextUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class FeatureService {
    private final ConfigManager configManager;
    private final LanguageManager languageManager;

    public FeatureService(ConfigManager configManager, LanguageManager languageManager) {
        this.configManager = configManager;
        this.languageManager = languageManager;
    }

    public double luckyWinChanceMultiplier() {
        ConfigurationSection active = activeLuckyHour();
        return active == null ? 1.0D : Math.max(0.0D, active.getDouble("win_chance_multiplier", 1.0D));
    }

    public double luckyMoneyMultiplier() {
        ConfigurationSection active = activeLuckyHour();
        return active == null ? 1.0D : Math.max(0.0D, active.getDouble("money_multiplier", 1.0D));
    }

    public String luckyHourName() {
        ConfigurationSection active = activeLuckyHour();
        return active == null ? "-" : active.getString("display_name", active.getName());
    }

    public double rollMysteryMultiplier(Player player, Reward reward) {
        if (!configManager.config().getBoolean("mystery_multiplier.enabled", true)) {
            return 1.0D;
        }
        if (configManager.config().getBoolean("mystery_multiplier.money_rewards_only", true) && reward.money() <= 0.0D) {
            return 1.0D;
        }
        ConfigurationSection section = configManager.config().getConfigurationSection("mystery_multiplier.multipliers");
        if (section == null) {
            return 1.0D;
        }
        double total = 0.0D;
        for (String key : section.getKeys(false)) {
            total += Math.max(0.0D, section.getDouble(key + ".chance", 0.0D));
        }
        if (total <= 0.0D) {
            return 1.0D;
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        double current = 0.0D;
        for (String key : section.getKeys(false)) {
            current += Math.max(0.0D, section.getDouble(key + ".chance", 0.0D));
            if (roll <= current) {
                double value = Math.max(0.0D, section.getDouble(key + ".value", 1.0D));
                if (value > 1.0D) {
                    languageManager.send(player, "mystery_multiplier_hit", TextUtil.placeholders(
                            "%multiplier%", formatMultiplier(value),
                            "%reward%", reward.displayName()
                    ));
                }
                return value;
            }
        }
        return 1.0D;
    }

    public boolean isTypeAvailable(ScratchcardType type) {
        return type.isActive(System.currentTimeMillis());
    }

    public void sendTypeUnavailable(Player player, ScratchcardType type) {
        String key = type.startsInFuture(System.currentTimeMillis()) ? "event_not_started" : "event_expired";
        languageManager.send(player, key, TextUtil.placeholders(
                "%type%", type.displayName(),
                "%type_id%", type.id()
        ));
    }

    public String rarityDisplay(Reward reward) {
        String path = "rarities." + reward.rarity() + ".display_name";
        return configManager.config().getString(path, reward.rarity());
    }

    private ConfigurationSection activeLuckyHour() {
        if (!configManager.config().getBoolean("lucky_hour.enabled", true)) {
            return null;
        }
        ConfigurationSection windows = configManager.config().getConfigurationSection("lucky_hour.windows");
        if (windows == null) {
            return null;
        }
        LocalTime now = LocalTime.now();
        for (String key : windows.getKeys(false)) {
            ConfigurationSection section = windows.getConfigurationSection(key);
            if (section != null && section.getBoolean("enabled", true) && isTimeInWindow(now, section)) {
                return section;
            }
        }
        return null;
    }

    private boolean isTimeInWindow(LocalTime now, ConfigurationSection section) {
        try {
            LocalTime start = LocalTime.parse(section.getString("start", "18:00"));
            LocalTime end = LocalTime.parse(section.getString("end", "19:00"));
            if (start.equals(end)) {
                return true;
            }
            if (start.isBefore(end)) {
                return !now.isBefore(start) && now.isBefore(end);
            }
            return !now.isBefore(start) || now.isBefore(end);
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private String formatMultiplier(double multiplier) {
        if (Math.rint(multiplier) == multiplier) {
            return String.valueOf((int) multiplier);
        }
        return String.format(Locale.GERMANY, "%.2f", multiplier);
    }
}
