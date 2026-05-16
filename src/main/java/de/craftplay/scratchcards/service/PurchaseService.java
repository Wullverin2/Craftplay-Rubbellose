package de.craftplay.scratchcards.service;

import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.config.LanguageManager;
import de.craftplay.scratchcards.database.DatabaseManager;
import de.craftplay.scratchcards.economy.EconomyManager;
import de.craftplay.scratchcards.model.ScratchcardType;
import de.craftplay.scratchcards.util.ServerDayUtil;
import de.craftplay.scratchcards.util.TextUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PurchaseService {
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final DatabaseManager databaseManager;
    private final EconomyManager economyManager;
    private final ScratchcardItemFactory itemFactory;
    private final FeedbackService feedbackService;
    private final FeatureService featureService;
    private final ProgressionService progressionService;
    private final Map<UUID, Long> buyCooldowns = new HashMap<>();

    public PurchaseService(ConfigManager configManager, LanguageManager languageManager, DatabaseManager databaseManager,
                           EconomyManager economyManager, ScratchcardItemFactory itemFactory,
                           FeedbackService feedbackService, FeatureService featureService,
                           ProgressionService progressionService) {
        this.configManager = configManager;
        this.languageManager = languageManager;
        this.databaseManager = databaseManager;
        this.economyManager = economyManager;
        this.itemFactory = itemFactory;
        this.feedbackService = feedbackService;
        this.featureService = featureService;
        this.progressionService = progressionService;
    }

    public void buy(Player player, ScratchcardType type) {
        if (!player.hasPermission("craftplay.scratchcards.buy")) {
            languageManager.send(player, "no_permission");
            return;
        }
        if (!type.buyable()) {
            languageManager.send(player, "not_buyable");
            return;
        }
        if (!featureService.isTypeAvailable(type)) {
            featureService.sendTypeUnavailable(player, type);
            return;
        }
        if (isBuyCooldown(player)) {
            languageManager.send(player, "buy_cooldown");
            return;
        }
        int perDay = configManager.config().getInt("limits.max_purchases_per_day", 25);
        int boughtToday = dailyPurchaseCount(player);
        if (configManager.config().getBoolean("limits.enabled", true)) {
            if (perDay > 0 && boughtToday >= perDay) {
                languageManager.send(player, "purchase_limit_day", dailyLimitPlaceholders(boughtToday, perDay));
                return;
            }
            int maxOwned = configManager.config().getInt("limits.max_owned_scratchcards", 64);
            int owned = itemFactory.countOwned(player);
            if (maxOwned > 0 && owned >= maxOwned) {
                languageManager.send(player, "owned_limit", ownedLimitPlaceholders(owned, maxOwned));
                return;
            }
        }
        if (!itemFactory.canFit(player, type, 1)) {
            languageManager.send(player, "inventory_full");
            return;
        }
        if (!economyManager.ensureSetup()) {
            languageManager.send(player, "economy_unavailable");
            return;
        }
        if (!economyManager.has(player, type.price())) {
            languageManager.send(player, "not_enough_money", TextUtil.placeholders("%price%", economyManager.format(type.price())));
            return;
        }
        if (!economyManager.withdraw(player, type.price())) {
            languageManager.send(player, "not_enough_money", TextUtil.placeholders("%price%", economyManager.format(type.price())));
            return;
        }

        ItemStack item = itemFactory.create(type, 1);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            economyManager.deposit(player, type.price());
            languageManager.send(player, "inventory_full");
            return;
        }
        databaseManager.recordPurchase(player.getUniqueId(), player.getName(), type.id(), type.price());
        buyCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        int boughtTodayAfterPurchase = dailyPurchaseCount(player);
        languageManager.send(player, "purchase_success", TextUtil.placeholders(
                "%type%", type.displayName(),
                "%price%", economyManager.format(type.price()),
                "%daily_bought%", String.valueOf(boughtTodayAfterPurchase),
                "%daily_limit%", perDay > 0 ? String.valueOf(perDay) : "-",
                "%daily_remaining%", perDay > 0 ? String.valueOf(Math.max(0, perDay - boughtTodayAfterPurchase)) : "-"
        ));
        feedbackService.play(player, "buy");
        progressionService.onBuy(player);
    }

    public GiveResult give(Player target, ScratchcardType type, int amount) {
        int requested = Math.max(1, amount);
        int ownedBefore = itemFactory.countOwned(target);
        int maxOwned = configManager.config().getBoolean("limits.enabled", true)
                ? configManager.config().getInt("limits.max_owned_scratchcards", 64)
                : 0;
        int allowedByOwnership = maxOwned > 0 ? Math.max(0, maxOwned - ownedBefore) : requested;
        int freeCapacity = itemFactory.freeCapacity(target, type);
        int given = Math.min(requested, Math.min(allowedByOwnership, freeCapacity));
        addExact(target, type, given);
        return new GiveResult(requested, given, ownedBefore, itemFactory.countOwned(target), maxOwned);
    }

    public void claimDaily(Player player, ScratchcardType type) {
        if (!player.hasPermission("craftplay.scratchcards.daily") && !player.hasPermission("craftplay.scratchcards.admin")) {
            languageManager.send(player, "no_permission");
            return;
        }
        if (!configManager.config().getBoolean("daily.enabled", true)) {
            languageManager.send(player, "daily_disabled");
            return;
        }
        if (!featureService.isTypeAvailable(type)) {
            featureService.sendTypeUnavailable(player, type);
            return;
        }
        long dayStart = ServerDayUtil.currentServerDayStartMillis();
        if (databaseManager.countDailyClaimsSince(player.getUniqueId(), dayStart) > 0) {
            languageManager.send(player, "daily_already_claimed");
            return;
        }
        int amount = Math.max(1, configManager.config().getInt("daily.amount", 1));
        if (configManager.config().getBoolean("limits.enabled", true)
                && configManager.config().getBoolean("daily.respect_owned_limit", true)) {
            int maxOwned = configManager.config().getInt("limits.max_owned_scratchcards", 64);
            int owned = itemFactory.countOwned(player);
            if (maxOwned > 0 && owned + amount > maxOwned) {
                languageManager.send(player, "owned_limit", ownedLimitPlaceholders(owned, maxOwned));
                return;
            }
        }
        if (configManager.config().getBoolean("daily.require_inventory_space", true)
                && !itemFactory.canFit(player, type, amount)) {
            languageManager.send(player, "inventory_full");
            return;
        }
        addExact(player, type, amount);
        databaseManager.recordDailyClaim(player.getUniqueId(), player.getName(), type.id(), amount);
        featureService.updateDailyStreak(player);
        languageManager.send(player, "daily_claimed", TextUtil.placeholders(
                "%type%", type.displayName(),
                "%amount%", String.valueOf(amount)
        ));
        feedbackService.play(player, "daily");
        progressionService.onDaily(player);
    }

    public boolean canClaimDaily(Player player) {
        return configManager.config().getBoolean("daily.enabled", true)
                && databaseManager.countDailyClaimsSince(player.getUniqueId(), ServerDayUtil.currentServerDayStartMillis()) <= 0;
    }

    private void addExact(Player target, ScratchcardType type, int amount) {
        int remaining = Math.max(0, amount);
        while (remaining > 0) {
            int stackAmount = Math.min(64, remaining);
            Map<Integer, ItemStack> overflow = target.getInventory().addItem(itemFactory.create(type, stackAmount));
            for (ItemStack stack : overflow.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), stack);
            }
            remaining -= stackAmount;
        }
    }

    private boolean isBuyCooldown(Player player) {
        int seconds = configManager.config().getInt("cooldown.buy_seconds", 1);
        if (seconds <= 0) {
            return false;
        }
        long last = buyCooldowns.getOrDefault(player.getUniqueId(), 0L);
        return System.currentTimeMillis() - last < seconds * 1000L;
    }

    private int dailyPurchaseCount(Player player) {
        return databaseManager.countPurchasesSince(player.getUniqueId(), ServerDayUtil.currentServerDayStartMillis());
    }

    private Map<String, String> dailyLimitPlaceholders(int boughtToday, int perDay) {
        return TextUtil.placeholders(
                "%daily_bought%", String.valueOf(boughtToday),
                "%daily_limit%", String.valueOf(perDay),
                "%daily_remaining%", String.valueOf(Math.max(0, perDay - boughtToday))
        );
    }

    private Map<String, String> ownedLimitPlaceholders(int owned, int maxOwned) {
        return TextUtil.placeholders(
                "%owned%", String.valueOf(owned),
                "%owned_limit%", String.valueOf(maxOwned),
                "%owned_remaining%", String.valueOf(Math.max(0, maxOwned - owned))
        );
    }

    public record GiveResult(int requested, int given, int ownedBefore, int ownedAfter, int maxOwned) {
        public boolean limited() {
            return given < requested;
        }
    }
}
