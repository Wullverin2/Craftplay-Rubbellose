package de.craftplay.scratchcards.service;

import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.config.LanguageManager;
import de.craftplay.scratchcards.database.DatabaseManager;
import de.craftplay.scratchcards.economy.EconomyManager;
import de.craftplay.scratchcards.model.DailyStreak;
import de.craftplay.scratchcards.model.Reward;
import de.craftplay.scratchcards.model.ScratchcardType;
import de.craftplay.scratchcards.model.SeriesProgress;
import de.craftplay.scratchcards.util.ServerDayUtil;
import de.craftplay.scratchcards.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class FeatureService {
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final DatabaseManager databaseManager;
    private final EconomyManager economyManager;

    public FeatureService(ConfigManager configManager, LanguageManager languageManager,
                          DatabaseManager databaseManager, EconomyManager economyManager) {
        this.configManager = configManager;
        this.languageManager = languageManager;
        this.databaseManager = databaseManager;
        this.economyManager = economyManager;
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

    public DailyStreak updateDailyStreak(Player player) {
        long today = ServerDayUtil.currentServerDayStartMillis();
        long yesterday = ServerDayUtil.previousServerDayStartMillis();
        DailyStreak previous = databaseManager.getDailyStreak(player.getUniqueId());
        int current = previous.lastDayStart() == yesterday ? previous.current() + 1 : 1;
        int best = Math.max(previous.best(), current);
        DailyStreak updated = new DailyStreak(current, best, today);
        databaseManager.saveDailyStreak(player.getUniqueId(), player.getName(), updated);
        languageManager.send(player, "streak_progress", TextUtil.placeholders(
                "%streak%", String.valueOf(updated.current()),
                "%best_streak%", String.valueOf(updated.best())
        ));
        handleStreakReward(player, updated);
        return updated;
    }

    public List<SeriesProgress> seriesProgress(UUID uuid) {
        List<SeriesProgress> progress = new ArrayList<>();
        ConfigurationSection sets = configManager.config().getConfigurationSection("series.sets");
        if (sets == null) {
            return progress;
        }
        for (String seriesId : sets.getKeys(false)) {
            ConfigurationSection set = sets.getConfigurationSection(seriesId);
            if (set == null || !set.getBoolean("enabled", true)) {
                continue;
            }
            int required = set.getStringList("required_rewards").size();
            int collected = Math.min(required, databaseManager.countSeriesSymbols(uuid, seriesId.toLowerCase(Locale.ROOT)));
            progress.add(new SeriesProgress(
                    seriesId.toLowerCase(Locale.ROOT),
                    set.getString("display_name", seriesId),
                    collected,
                    required,
                    databaseManager.hasSeriesClaim(uuid, seriesId.toLowerCase(Locale.ROOT))
            ));
        }
        return progress;
    }

    public void handleSeries(Player player, ScratchcardType type, Reward reward) {
        if (!configManager.config().getBoolean("series.enabled", true)) {
            return;
        }
        ConfigurationSection sets = configManager.config().getConfigurationSection("series.sets");
        if (sets == null) {
            return;
        }
        for (String seriesId : sets.getKeys(false)) {
            ConfigurationSection set = sets.getConfigurationSection(seriesId);
            if (set == null || !set.getBoolean("enabled", true)) {
                continue;
            }
            String configuredType = set.getString("type", "all");
            if (!configuredType.equalsIgnoreCase("all") && !configuredType.equalsIgnoreCase(type.id())) {
                continue;
            }
            String matchedSymbol = matchedSeriesSymbol(set.getStringList("required_rewards"), type, reward);
            if (matchedSymbol == null) {
                continue;
            }
            String normalizedSeries = seriesId.toLowerCase(Locale.ROOT);
            boolean collected = databaseManager.collectSeriesSymbol(player.getUniqueId(), player.getName(), normalizedSeries, matchedSymbol);
            if (collected) {
                int current = databaseManager.countSeriesSymbols(player.getUniqueId(), normalizedSeries);
                int required = set.getStringList("required_rewards").size();
                languageManager.send(player, "series_symbol_collected", TextUtil.placeholders(
                        "%series%", set.getString("display_name", seriesId),
                        "%symbol%", reward.displayName(),
                        "%collected%", String.valueOf(Math.min(current, required)),
                        "%required%", String.valueOf(required)
                ));
            }
            maybeCompleteSeries(player, normalizedSeries, set);
        }
    }

    public void handleServerGoal() {
        if (!configManager.config().getBoolean("server_goal.enabled", true)) {
            return;
        }
        String goalId = configManager.config().getString("server_goal.id", "default").toLowerCase(Locale.ROOT);
        if (databaseManager.isServerGoalCompleted(goalId)) {
            return;
        }
        long opened = databaseManager.countTotalOpens();
        long target = Math.max(1L, configManager.config().getLong("server_goal.target_opens", 500L));
        if (opened < target) {
            return;
        }
        databaseManager.markServerGoalCompleted(goalId, opened);
        double rewardMoney = configManager.config().getDouble("server_goal.reward_money_online", 0.0D);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (rewardMoney > 0.0D) {
                economyManager.deposit(online, rewardMoney);
            }
            languageManager.send(online, "server_goal_completed", TextUtil.placeholders(
                    "%opened%", String.valueOf(opened),
                    "%target%", String.valueOf(target),
                    "%money%", economyManager.format(rewardMoney)
            ));
        }
        for (String command : configManager.config().getStringList("server_goal.commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                    .replace("%opened%", String.valueOf(opened))
                    .replace("%target%", String.valueOf(target)));
        }
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

    private String matchedSeriesSymbol(List<String> required, ScratchcardType type, Reward reward) {
        String typed = (type.id() + ":" + reward.id()).toLowerCase(Locale.ROOT);
        String plain = reward.id().toLowerCase(Locale.ROOT);
        for (String entry : required) {
            String normalized = entry.toLowerCase(Locale.ROOT);
            if (normalized.equals(typed) || normalized.equals(plain)) {
                return normalized;
            }
        }
        return null;
    }

    private void maybeCompleteSeries(Player player, String seriesId, ConfigurationSection set) {
        int required = set.getStringList("required_rewards").size();
        if (required <= 0 || databaseManager.hasSeriesClaim(player.getUniqueId(), seriesId)) {
            return;
        }
        int collected = databaseManager.countSeriesSymbols(player.getUniqueId(), seriesId);
        if (collected < required) {
            return;
        }
        databaseManager.markSeriesClaim(player.getUniqueId(), player.getName(), seriesId);
        double money = set.getDouble("completion.money", 0.0D);
        if (money > 0.0D) {
            economyManager.deposit(player, money);
        }
        for (String command : set.getStringList("completion.commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
        }
        languageManager.send(player, "series_completed", TextUtil.placeholders(
                "%series%", set.getString("display_name", seriesId),
                "%money%", economyManager.format(money)
        ));
        if (set.getBoolean("completion.broadcast", true)) {
            Bukkit.broadcastMessage(TextUtil.color(languageManager.message("series_broadcast", TextUtil.placeholders(
                    "%player%", player.getName(),
                    "%series%", set.getString("display_name", seriesId),
                    "%money%", economyManager.format(money)
            ))));
        }
    }

    private void handleStreakReward(Player player, DailyStreak streak) {
        if (!configManager.config().getBoolean("streak.enabled", true)) {
            return;
        }
        int everyDays = Math.max(1, configManager.config().getInt("streak.reward_every_days", 7));
        if (streak.current() % everyDays != 0) {
            return;
        }
        double money = configManager.config().getDouble("streak.reward.money", 0.0D);
        if (money > 0.0D) {
            economyManager.deposit(player, money);
        }
        for (String command : configManager.config().getStringList("streak.reward.commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                    .replace("%player%", player.getName())
                    .replace("%streak%", String.valueOf(streak.current())));
        }
        languageManager.send(player, "streak_reward", TextUtil.placeholders(
                "%streak%", String.valueOf(streak.current()),
                "%money%", economyManager.format(money)
        ));
    }

    private String formatMultiplier(double multiplier) {
        if (Math.rint(multiplier) == multiplier) {
            return String.valueOf((int) multiplier);
        }
        return String.format(Locale.GERMANY, "%.2f", multiplier);
    }
}
