package de.craftplay.scratchcards.service;

import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.config.LanguageManager;
import de.craftplay.scratchcards.database.DatabaseManager;
import de.craftplay.scratchcards.economy.EconomyManager;
import de.craftplay.scratchcards.model.GroupGoalProgress;
import de.craftplay.scratchcards.model.PassProgress;
import de.craftplay.scratchcards.model.QuestProgress;
import de.craftplay.scratchcards.model.RiskOffer;
import de.craftplay.scratchcards.util.ServerDayUtil;
import de.craftplay.scratchcards.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ProgressionService {
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final DatabaseManager databaseManager;
    private final EconomyManager economyManager;

    public ProgressionService(ConfigManager configManager, LanguageManager languageManager,
                              DatabaseManager databaseManager, EconomyManager economyManager) {
        this.configManager = configManager;
        this.languageManager = languageManager;
        this.databaseManager = databaseManager;
        this.economyManager = economyManager;
    }

    public void onBuy(Player player) {
        addPassXp(player, "buy");
        progressQuests(player, "buy", 1);
        progressGroupGoals("buy", 1);
    }

    public void onOpen(Player player) {
        addPassXp(player, "open");
        progressQuests(player, "open", 1);
        progressGroupGoals("open", 1);
    }

    public void onWin(Player player, boolean jackpot) {
        addPassXp(player, "win");
        progressQuests(player, "win", 1);
        if (jackpot) {
            addPassXp(player, "jackpot");
            progressQuests(player, "jackpot", 1);
            progressGroupGoals("jackpot", 1);
        }
    }

    public void onDaily(Player player) {
        addPassXp(player, "daily");
        progressQuests(player, "daily", 1);
    }

    public PassProgress passProgress(UUID uuid) {
        return databaseManager.getPassProgress(uuid, season());
    }

    public List<QuestProgress> dailyQuests(UUID uuid, String fallbackName) {
        List<QuestProgress> quests = new ArrayList<>();
        ConfigurationSection root = configManager.config().getConfigurationSection("quests.daily");
        if (root == null) {
            return quests;
        }
        long dayStart = ServerDayUtil.currentServerDayStartMillis();
        for (String questId : root.getKeys(false)) {
            ConfigurationSection quest = root.getConfigurationSection(questId);
            if (quest == null || !quest.getBoolean("enabled", true)) {
                continue;
            }
            int target = Math.max(1, quest.getInt("target", 1));
            quests.add(databaseManager.getQuestProgress(uuid, fallbackName, questId.toLowerCase(Locale.ROOT),
                    quest.getString("display_name", questId), dayStart, target));
        }
        return quests;
    }

    public List<GroupGoalProgress> groupGoals() {
        List<GroupGoalProgress> goals = new ArrayList<>();
        ConfigurationSection root = configManager.config().getConfigurationSection("group_goals.goals");
        if (root == null) {
            return goals;
        }
        long dayStart = ServerDayUtil.currentServerDayStartMillis();
        for (String goalId : root.getKeys(false)) {
            ConfigurationSection goal = root.getConfigurationSection(goalId);
            if (goal == null || !goal.getBoolean("enabled", true)) {
                continue;
            }
            long target = Math.max(1L, goal.getLong("target", 1L));
            goals.add(databaseManager.getGroupGoalProgress(goalId.toLowerCase(Locale.ROOT),
                    goal.getString("display_name", goalId), dayStart, target));
        }
        return goals;
    }

    public void addRiskOffer(Player player, double amount, String rewardName) {
        if (!configManager.config().getBoolean("risk.enabled", true) || amount <= 0.0D) {
            return;
        }
        double minimum = configManager.config().getDouble("risk.minimum_money", 1.0D);
        if (amount < minimum) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + Math.max(5, configManager.config().getInt("risk.expire_seconds", 60)) * 1000L;
        databaseManager.saveRiskOffer(new RiskOffer(player.getUniqueId(), player.getName(), amount, rewardName, expiresAt));
        languageManager.send(player, "risk_available", TextUtil.placeholders(
                "%amount%", economyManager.format(amount),
                "%reward%", rewardName
        ));
    }

    public void playRisk(Player player) {
        RiskOffer offer = databaseManager.loadRiskOffer(player.getUniqueId());
        if (offer == null) {
            languageManager.send(player, "risk_none");
            return;
        }
        if (offer.expired(System.currentTimeMillis())) {
            databaseManager.deleteRiskOffer(player.getUniqueId());
            languageManager.send(player, "risk_expired");
            return;
        }
        if (!economyManager.withdraw(player, offer.amount())) {
            languageManager.send(player, "risk_cannot_withdraw", TextUtil.placeholders("%amount%", economyManager.format(offer.amount())));
            return;
        }
        databaseManager.deleteRiskOffer(player.getUniqueId());
        double chance = Math.max(0.0D, Math.min(100.0D, configManager.config().getDouble("risk.win_chance", 50.0D)));
        double multiplier = Math.max(1.0D, configManager.config().getDouble("risk.multiplier", 2.0D));
        if (ThreadLocalRandom.current().nextDouble(100.0D) <= chance) {
            double payout = offer.amount() * multiplier;
            economyManager.deposit(player, payout);
            languageManager.send(player, "risk_win", TextUtil.placeholders(
                    "%amount%", economyManager.format(offer.amount()),
                    "%payout%", economyManager.format(payout),
                    "%multiplier%", formatMultiplier(multiplier)
            ));
            return;
        }
        languageManager.send(player, "risk_lose", TextUtil.placeholders("%amount%", economyManager.format(offer.amount())));
    }

    public int pityLosses(UUID uuid, String typeId) {
        return databaseManager.getPityLosses(uuid, typeId);
    }

    public void updatePity(Player player, String typeId, boolean win) {
        if (!configManager.config().getBoolean("pity.enabled", true)) {
            return;
        }
        if (win) {
            databaseManager.setPityLosses(player.getUniqueId(), typeId, 0);
            return;
        }
        int losses = databaseManager.getPityLosses(player.getUniqueId(), typeId) + 1;
        databaseManager.setPityLosses(player.getUniqueId(), typeId, losses);
        languageManager.send(player, "pity_progress", TextUtil.placeholders(
                "%losses%", String.valueOf(losses),
                "%required%", String.valueOf(pityThreshold())
        ));
    }

    public String guaranteedRewardId() {
        return configManager.config().getString("pity.guaranteed_reward", "");
    }

    public int pityThreshold() {
        return Math.max(1, configManager.config().getInt("pity.after_losses", 10));
    }

    private void addPassXp(Player player, String event) {
        if (!configManager.config().getBoolean("pass.enabled", true)) {
            return;
        }
        int xp = Math.max(0, configManager.config().getInt("pass.xp." + event, 0));
        if (xp <= 0) {
            return;
        }
        PassProgress progress = databaseManager.addPassXp(player.getUniqueId(), player.getName(), season(), xp);
        languageManager.send(player, "pass_xp", TextUtil.placeholders(
                "%xp%", String.valueOf(xp),
                "%total_xp%", String.valueOf(progress.xp()),
                "%season%", configManager.config().getString("pass.season_name", season())
        ));
        claimReachedPassLevels(player, progress);
    }

    private void claimReachedPassLevels(Player player, PassProgress progress) {
        ConfigurationSection levels = configManager.config().getConfigurationSection("pass.levels");
        if (levels == null) {
            return;
        }
        List<Integer> ordered = levels.getKeys(false).stream()
                .map(this::parseInt)
                .filter(level -> level > progress.claimedLevels())
                .sorted()
                .toList();
        int highestClaimed = progress.claimedLevels();
        for (int level : ordered) {
            ConfigurationSection section = levels.getConfigurationSection(String.valueOf(level));
            if (section == null || progress.xp() < section.getInt("required_xp", Integer.MAX_VALUE)) {
                continue;
            }
            rewardPlayer(player, section, Map.of("%level%", String.valueOf(level)));
            highestClaimed = Math.max(highestClaimed, level);
            languageManager.send(player, "pass_level_reward", TextUtil.placeholders(
                    "%level%", String.valueOf(level),
                    "%season%", configManager.config().getString("pass.season_name", season())
            ));
        }
        if (highestClaimed != progress.claimedLevels()) {
            databaseManager.savePassProgress(player.getUniqueId(), player.getName(),
                    new PassProgress(progress.season(), progress.xp(), highestClaimed));
        }
    }

    private void progressQuests(Player player, String event, int amount) {
        if (!configManager.config().getBoolean("quests.enabled", true)) {
            return;
        }
        ConfigurationSection root = configManager.config().getConfigurationSection("quests.daily");
        if (root == null) {
            return;
        }
        long dayStart = ServerDayUtil.currentServerDayStartMillis();
        for (String questId : root.getKeys(false)) {
            ConfigurationSection quest = root.getConfigurationSection(questId);
            if (quest == null || !quest.getBoolean("enabled", true) || !event.equalsIgnoreCase(quest.getString("event", ""))) {
                continue;
            }
            int target = Math.max(1, quest.getInt("target", 1));
            QuestProgress before = databaseManager.getQuestProgress(player.getUniqueId(), player.getName(),
                    questId.toLowerCase(Locale.ROOT), quest.getString("display_name", questId), dayStart, target);
            if (before.completed()) {
                continue;
            }
            QuestProgress progress = databaseManager.addQuestProgress(player.getUniqueId(), player.getName(),
                    questId.toLowerCase(Locale.ROOT), quest.getString("display_name", questId), dayStart, target, amount);
            if (progress.completed() && progress.progress() >= target) {
                rewardPlayer(player, quest.getConfigurationSection("reward"), Map.of("%quest%", progress.displayName()));
                languageManager.send(player, "quest_completed", TextUtil.placeholders("%quest%", progress.displayName()));
            }
        }
    }

    private void progressGroupGoals(String event, long amount) {
        if (!configManager.config().getBoolean("group_goals.enabled", true)) {
            return;
        }
        ConfigurationSection root = configManager.config().getConfigurationSection("group_goals.goals");
        if (root == null) {
            return;
        }
        long dayStart = ServerDayUtil.currentServerDayStartMillis();
        for (String goalId : root.getKeys(false)) {
            ConfigurationSection goal = root.getConfigurationSection(goalId);
            if (goal == null || !goal.getBoolean("enabled", true) || !event.equalsIgnoreCase(goal.getString("event", ""))) {
                continue;
            }
            GroupGoalProgress before = databaseManager.getGroupGoalProgress(goalId.toLowerCase(Locale.ROOT),
                    goal.getString("display_name", goalId), dayStart, Math.max(1L, goal.getLong("target", 1L)));
            if (before.completed()) {
                continue;
            }
            GroupGoalProgress progress = databaseManager.addGroupGoalProgress(goalId.toLowerCase(Locale.ROOT),
                    goal.getString("display_name", goalId), dayStart, Math.max(1L, goal.getLong("target", 1L)), amount);
            if (!progress.completed()) {
                continue;
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                rewardPlayer(online, goal.getConfigurationSection("reward"), Map.of("%goal%", progress.displayName()));
                languageManager.send(online, "group_goal_completed", TextUtil.placeholders(
                        "%goal%", progress.displayName(),
                        "%progress%", String.valueOf(progress.progress()),
                        "%target%", String.valueOf(progress.target())
                ));
            }
        }
    }

    private void rewardPlayer(Player player, ConfigurationSection rewardSection, Map<String, String> extraPlaceholders) {
        if (rewardSection == null) {
            return;
        }
        double money = rewardSection.getDouble("money", 0.0D);
        if (money > 0.0D) {
            economyManager.deposit(player, money);
        }
        for (String command : rewardSection.getStringList("commands")) {
            String prepared = command.replace("%player%", player.getName());
            for (Map.Entry<String, String> entry : extraPlaceholders.entrySet()) {
                prepared = prepared.replace(entry.getKey(), entry.getValue());
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), prepared);
        }
    }

    private String season() {
        return configManager.config().getString("pass.season_id", "default").toLowerCase(Locale.ROOT);
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private String formatMultiplier(double multiplier) {
        if (Math.rint(multiplier) == multiplier) {
            return String.valueOf((int) multiplier);
        }
        return String.format(Locale.GERMANY, "%.2f", multiplier);
    }
}
