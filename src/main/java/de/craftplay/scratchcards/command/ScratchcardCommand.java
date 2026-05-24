package de.craftplay.scratchcards.command;

import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.config.LanguageManager;
import de.craftplay.scratchcards.database.DatabaseManager;
import de.craftplay.scratchcards.diagnostic.DiagnosticLogger;
import de.craftplay.scratchcards.economy.EconomyManager;
import de.craftplay.scratchcards.gui.GuiManager;
import de.craftplay.scratchcards.model.PlayerStats;
import de.craftplay.scratchcards.model.PassProgress;
import de.craftplay.scratchcards.model.QuestProgress;
import de.craftplay.scratchcards.model.Reward;
import de.craftplay.scratchcards.model.ServerStats;
import de.craftplay.scratchcards.model.SeriesProgress;
import de.craftplay.scratchcards.model.ScratchcardType;
import de.craftplay.scratchcards.service.FeatureService;
import de.craftplay.scratchcards.service.PurchaseService;
import de.craftplay.scratchcards.service.ProgressionService;
import de.craftplay.scratchcards.service.RewardManager;
import de.craftplay.scratchcards.service.ScratchcardItemFactory;
import de.craftplay.scratchcards.service.ScratchcardSessionManager;
import de.craftplay.scratchcards.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ScratchcardCommand implements CommandExecutor, TabCompleter {
    private final Runnable reloadAction;
    private final String pluginVersion;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final DiagnosticLogger diagnosticLogger;
    private final EconomyManager economyManager;
    private final RewardManager rewardManager;
    private final PurchaseService purchaseService;
    private final ScratchcardSessionManager sessionManager;
    private final GuiManager guiManager;
    private final DatabaseManager databaseManager;
    private final FeatureService featureService;
    private final ProgressionService progressionService;
    private final ScratchcardItemFactory itemFactory;

    public ScratchcardCommand(Runnable reloadAction, String pluginVersion, ConfigManager configManager, LanguageManager languageManager,
                              DiagnosticLogger diagnosticLogger,
                              EconomyManager economyManager,
                              RewardManager rewardManager, PurchaseService purchaseService,
                              ScratchcardSessionManager sessionManager, GuiManager guiManager,
                              DatabaseManager databaseManager, FeatureService featureService,
                              ProgressionService progressionService, ScratchcardItemFactory itemFactory) {
        this.reloadAction = reloadAction;
        this.pluginVersion = pluginVersion;
        this.configManager = configManager;
        this.languageManager = languageManager;
        this.diagnosticLogger = diagnosticLogger;
        this.economyManager = economyManager;
        this.rewardManager = rewardManager;
        this.purchaseService = purchaseService;
        this.sessionManager = sessionManager;
        this.guiManager = guiManager;
        this.databaseManager = databaseManager;
        this.featureService = featureService;
        this.progressionService = progressionService;
        this.itemFactory = itemFactory;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(sender, command.getName(), label, args);
    }

    public boolean execute(CommandSender sender, String commandName, String label, String[] args) {
        try {
            return executeCommand(sender, commandName, label, args);
        } catch (Throwable throwable) {
            diagnosticLogger.error("Fehler beim Befehl /" + label + " " + String.join(" ", args), throwable);
            languageManager.send(sender, "internal_error");
            return true;
        }
    }

    private boolean executeCommand(CommandSender sender, String commandName, String label, String[] args) {
        if (commandName.equalsIgnoreCase("cpscratchdiag")) {
            commandDiagnostics(sender);
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("shop")) {
            openShop(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "claim" -> claim(sender);
            case "give" -> give(sender, args);
            case "reload" -> reload(sender);
            case "stats" -> stats(sender);
            case "info" -> info(sender, args);
            case "list" -> list(sender);
            case "debug" -> debug(sender);
            case "jackpots" -> jackpots(sender);
            case "history" -> history(sender);
            case "daily" -> daily(sender);
            case "series" -> series(sender);
            case "pass" -> pass(sender);
            case "quests" -> quests(sender);
            case "board" -> board(sender);
            case "risk" -> risk(sender);
            case "gift" -> gift(sender, args);
            case "simulate" -> simulate(sender, args);
            case "resetpending" -> resetPending(sender, args);
            case "help" -> languageManager.sendList(sender, "help", Map.of());
            default -> languageManager.send(sender, "unknown_command");
        }
        return true;
    }

    private void openShop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            languageManager.send(sender, "only_players");
            return;
        }
        if (!player.hasPermission("craftplay.scratchcards.shop")) {
            languageManager.send(player, "no_permission");
            return;
        }
        guiManager.openShop(player);
        languageManager.send(player, "shop_opened");
    }

    private void claim(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            languageManager.send(sender, "only_players");
            return;
        }
        sessionManager.claim(player);
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("craftplay.scratchcards.give") && !sender.hasPermission("craftplay.scratchcards.admin")) {
            languageManager.send(sender, "no_permission");
            return;
        }
        if (args.length < 4) {
            languageManager.sendList(sender, "help", Map.of());
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            languageManager.send(sender, "player_not_found", TextUtil.placeholders("%player%", args[1]));
            return;
        }
        ScratchcardType type = rewardManager.type(args[2]).orElse(null);
        if (type == null) {
            languageManager.send(sender, "type_not_found", TextUtil.placeholders("%type%", args[2]));
            return;
        }
        if (sender instanceof Player player && !featureService.isTypeAvailable(type)) {
            featureService.sendTypeUnavailable(player, type);
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException exception) {
            languageManager.send(sender, "invalid_amount");
            return;
        }
        if (amount <= 0) {
            languageManager.send(sender, "invalid_amount");
            return;
        }
        PurchaseService.GiveResult result = purchaseService.give(target, type, amount);
        Map<String, String> placeholders = TextUtil.placeholders(
                "%player%", target.getName(),
                "%type%", type.displayName(),
                "%amount%", String.valueOf(result.given()),
                "%requested%", String.valueOf(result.requested()),
                "%given%", String.valueOf(result.given()),
                "%owned%", String.valueOf(result.ownedAfter()),
                "%owned_limit%", result.maxOwned() > 0 ? String.valueOf(result.maxOwned()) : "-"
        );
        if (result.given() <= 0) {
            languageManager.send(sender, "give_limited", placeholders);
            return;
        }
        languageManager.send(sender, result.limited() ? "give_limited" : "given", placeholders);
        languageManager.send(target, "received", placeholders);
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("craftplay.scratchcards.reload") && !sender.hasPermission("craftplay.scratchcards.admin")) {
            languageManager.send(sender, "no_permission");
            return;
        }
        reloadAction.run();
        languageManager.send(sender, "reload_success");
    }

    private void stats(CommandSender sender) {
        if (!sender.hasPermission("craftplay.scratchcards.stats") && !sender.hasPermission("craftplay.scratchcards.admin")) {
            languageManager.send(sender, "no_permission");
            return;
        }
        int limit = configManager.config().getInt("stats.top_winners_limit", 10);
        ServerStats stats = databaseManager.getServerStats(limit);
        int jackpotLimit = configManager.config().getInt("stats.latest_jackpots_limit", 10);
        String top = stats.topWinners().isEmpty()
                ? "-"
                : String.join(", ", stats.topWinners().stream()
                .map(player -> player.playerName() + " (" + TextUtil.money(player.wonMoney()) + ")")
                .toList());
        List<String> jackpots = databaseManager.getLatestJackpots(jackpotLimit);
        languageManager.send(sender, "stats_header");
        languageManager.send(sender, "stats_line_total_bought", TextUtil.placeholders("%total_bought%", String.valueOf(stats.totalBought())));
        languageManager.send(sender, "stats_line_total_income", TextUtil.placeholders("%total_income%", TextUtil.money(stats.totalIncome())));
        languageManager.send(sender, "stats_line_total_paid", TextUtil.placeholders("%total_paid%", TextUtil.money(stats.totalPaid())));
        languageManager.send(sender, "stats_line_profit", TextUtil.placeholders("%profit%", TextUtil.money(stats.totalIncome() - stats.totalPaid())));
        languageManager.send(sender, "stats_line_top", TextUtil.placeholders("%top%", top));
        languageManager.send(sender, "stats_line_jackpots", TextUtil.placeholders("%jackpots%", jackpots.isEmpty() ? "-" : String.join(", ", jackpots)));
    }

    private void info(CommandSender sender, String[] args) {
        if (!sender.hasPermission("craftplay.scratchcards.stats") && !sender.hasPermission("craftplay.scratchcards.admin")) {
            languageManager.send(sender, "no_permission");
            return;
        }
        if (args.length < 2) {
            languageManager.sendList(sender, "help", Map.of());
            return;
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(args[1]);
        if (!offlinePlayer.hasPlayedBefore() && offlinePlayer.getPlayer() == null) {
            languageManager.send(sender, "player_not_found", TextUtil.placeholders("%player%", args[1]));
            return;
        }
        PlayerStats stats = databaseManager.getPlayerStats(offlinePlayer.getUniqueId(), offlinePlayer.getName());
        languageManager.send(sender, "player_info_header", TextUtil.placeholders("%player%", stats.playerName()));
        languageManager.send(sender, "player_info_bought", TextUtil.placeholders("%bought%", String.valueOf(stats.bought())));
        languageManager.send(sender, "player_info_opened", TextUtil.placeholders("%opened%", String.valueOf(stats.opened())));
        languageManager.send(sender, "player_info_won_money", TextUtil.placeholders("%won_money%", TextUtil.money(stats.wonMoney())));
        languageManager.send(sender, "player_info_best_win", TextUtil.placeholders("%best_win%", TextUtil.money(stats.bestWin())));
        languageManager.send(sender, "player_info_jackpots", TextUtil.placeholders("%jackpots%", String.valueOf(stats.jackpots())));
    }

    private void list(CommandSender sender) {
        if (!sender.hasPermission("craftplay.scratchcards.admin")) {
            languageManager.send(sender, "no_permission");
            return;
        }
        languageManager.send(sender, "list_header");
        for (ScratchcardType type : rewardManager.types()) {
            languageManager.send(sender, "list_line", TextUtil.placeholders(
                    "%type_id%", type.id(),
                    "%type%", type.displayName(),
                    "%price%", TextUtil.money(type.price()),
                    "%buyable%", String.valueOf(type.buyable()),
                    "%rewards%", String.valueOf(type.rewards().size())
            ));
        }
    }

    private void debug(CommandSender sender) {
        if (!sender.hasPermission("craftplay.scratchcards.admin")) {
            languageManager.send(sender, "no_permission");
            return;
        }
        languageManager.send(sender, "debug_header");
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "Version", "%value%", pluginVersion));
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "Datenbank", "%value%", databaseManager.isMysql() ? "MySQL" : "SQLite"));
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "Vault-Economy", "%value%", economyManager.isAvailable() ? economyManager.providerName() : "nicht gefunden"));
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "Tabellenprefix", "%value%", databaseManager.tablePrefix()));
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "Geladene Typen", "%value%", String.valueOf(rewardManager.types().size())));
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "Aktive Sessions", "%value%", String.valueOf(sessionManager.activeSessionCount())));
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "Offene DB-Lose", "%value%", String.valueOf(databaseManager.countPendingScratchcards())));
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "Geoeffnete Lose gesamt", "%value%", String.valueOf(databaseManager.countTotalOpens())));
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "Limits", "%value%", String.valueOf(configManager.config().getBoolean("limits.enabled", true))));
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "Kaeufe pro Tag", "%value%", String.valueOf(configManager.config().getInt("limits.max_purchases_per_day", 25))));
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "Oeffnungen pro Tag", "%value%", String.valueOf(configManager.config().getInt("limits.max_opens_per_day", 25))));
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "Besitzlimit", "%value%", String.valueOf(configManager.config().getInt("limits.max_owned_scratchcards", 64))));
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "Daily-Los", "%value%", configManager.config().getBoolean("daily.enabled", true)
                + " typ=" + configManager.config().getString("daily.type", "small")));
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "Sprache", "%value%", configManager.config().getString("language.default", "de")));
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "Debug-Datei", "%value%", diagnosticLogger.isEnabled() ? diagnosticLogger.debugFile().toString() : "deaktiviert"));
    }

    private void commandDiagnostics(CommandSender sender) {
        languageManager.send(sender, "debug_header");
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "Diese Plugin-Version", "%value%", pluginVersion));
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "/rubbellos Besitzer", "%value%", commandOwner("rubbellos")));
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "/rubellos Legacy", "%value%", commandOwner("rubellos")));
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "/scratchcard Besitzer", "%value%", commandOwner("scratchcard")));
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "/cpscratchdiag Besitzer", "%value%", commandOwner("cpscratchdiag")));
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "Plugin aktiv", "%value%", "true"));
        languageManager.send(sender, "debug_line", TextUtil.placeholders("%key%", "Debug-Datei", "%value%", diagnosticLogger.isEnabled() ? diagnosticLogger.debugFile().toString() : "deaktiviert"));
    }

    private String commandOwner(String commandName) {
        PluginCommand pluginCommand = Bukkit.getPluginCommand(commandName);
        if (pluginCommand == null) {
            return "nicht registriert";
        }
        return pluginCommand.getPlugin().getName() + " v" + pluginCommand.getPlugin().getDescription().getVersion()
                + " aktiv=" + pluginCommand.getPlugin().isEnabled();
    }

    private void jackpots(CommandSender sender) {
        if (!sender.hasPermission("craftplay.scratchcards.stats") && !sender.hasPermission("craftplay.scratchcards.admin")) {
            languageManager.send(sender, "no_permission");
            return;
        }
        if (sender instanceof Player player) {
            guiManager.openJackpotHistory(player);
            return;
        }
        int jackpotLimit = configManager.config().getInt("stats.latest_jackpots_limit", 10);
        List<String> jackpots = databaseManager.getLatestJackpots(jackpotLimit);
        languageManager.send(sender, "stats_line_jackpots", TextUtil.placeholders("%jackpots%", jackpots.isEmpty() ? "-" : String.join(", ", jackpots)));
    }

    private void history(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            languageManager.send(sender, "only_players");
            return;
        }
        guiManager.openPlayerHistory(player);
    }

    private void daily(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            languageManager.send(sender, "only_players");
            return;
        }
        String typeId = configManager.config().getString("daily.type", "small");
        ScratchcardType type = rewardManager.type(typeId).orElse(null);
        if (type == null) {
            languageManager.send(player, "type_not_found", TextUtil.placeholders("%type%", typeId));
            return;
        }
        purchaseService.claimDaily(player, type);
    }

    private void series(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            languageManager.send(sender, "only_players");
            return;
        }
        languageManager.send(player, "series_header");
        List<SeriesProgress> progress = featureService.seriesProgress(player.getUniqueId());
        if (progress.isEmpty()) {
            languageManager.send(player, "series_empty");
            return;
        }
        for (SeriesProgress entry : progress) {
            String status = languageManager.message(entry.completed() ? "series_status_completed" : "series_status_open", Map.of());
            languageManager.send(player, "series_line", TextUtil.placeholders(
                    "%series%", entry.displayName(),
                    "%collected%", String.valueOf(entry.collected()),
                    "%required%", String.valueOf(entry.required()),
                    "%status%", status
            ));
        }
    }

    private void pass(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            languageManager.send(sender, "only_players");
            return;
        }
        PassProgress progress = progressionService.passProgress(player.getUniqueId());
        languageManager.send(player, "pass_info", TextUtil.placeholders(
                "%season%", configManager.config().getString("pass.season_name", progress.season()),
                "%xp%", String.valueOf(progress.xp()),
                "%claimed_levels%", String.valueOf(progress.claimedLevels())
        ));
    }

    private void quests(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            languageManager.send(sender, "only_players");
            return;
        }
        languageManager.send(player, "quests_header");
        List<QuestProgress> quests = progressionService.dailyQuests(player.getUniqueId(), player.getName());
        if (quests.isEmpty()) {
            languageManager.send(player, "quests_empty");
            return;
        }
        for (QuestProgress quest : quests) {
            languageManager.send(player, "quests_line", TextUtil.placeholders(
                    "%quest%", quest.displayName(),
                    "%progress%", String.valueOf(quest.progress()),
                    "%target%", String.valueOf(quest.target()),
                    "%status%", languageManager.message(quest.completed() ? "quest_status_completed" : "quest_status_open", Map.of())
            ));
        }
    }

    private void board(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            languageManager.send(sender, "only_players");
            return;
        }
        guiManager.openBoard(player);
    }

    private void risk(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            languageManager.send(sender, "only_players");
            return;
        }
        progressionService.playRisk(player);
    }

    private void gift(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            languageManager.send(sender, "only_players");
            return;
        }
        if (args.length < 4) {
            languageManager.sendList(sender, "help", Map.of());
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            languageManager.send(sender, "player_not_found", TextUtil.placeholders("%player%", args[1]));
            return;
        }
        ScratchcardType type = rewardManager.type(args[2]).orElse(null);
        if (type == null) {
            languageManager.send(sender, "type_not_found", TextUtil.placeholders("%type%", args[2]));
            return;
        }
        int amount = parsePositiveAmount(args[3]);
        if (amount <= 0) {
            languageManager.send(sender, "invalid_amount");
            return;
        }
        if (itemFactory.countOwned(player, type.id()) < amount) {
            languageManager.send(player, "gift_not_enough", TextUtil.placeholders(
                    "%type%", type.displayName(),
                    "%amount%", String.valueOf(amount)
            ));
            return;
        }
        if (!itemFactory.removeOwned(player, type.id(), amount)) {
            languageManager.send(player, "gift_not_enough", TextUtil.placeholders("%type%", type.displayName(), "%amount%", String.valueOf(amount)));
            return;
        }
        PurchaseService.GiveResult result = purchaseService.give(target, type, amount);
        if (result.given() < amount) {
            purchaseService.give(player, type, amount - result.given());
        }
        Map<String, String> placeholders = TextUtil.placeholders(
                "%player%", target.getName(),
                "%sender%", player.getName(),
                "%type%", type.displayName(),
                "%amount%", String.valueOf(result.given())
        );
        languageManager.send(player, "gift_sent", placeholders);
        languageManager.send(target, "gift_received", placeholders);
    }

    private void simulate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("craftplay.scratchcards.admin")) {
            languageManager.send(sender, "no_permission");
            return;
        }
        if (args.length < 3) {
            languageManager.sendList(sender, "help", Map.of());
            return;
        }
        ScratchcardType type = rewardManager.type(args[1]).orElse(null);
        if (type == null) {
            languageManager.send(sender, "type_not_found", TextUtil.placeholders("%type%", args[1]));
            return;
        }
        int amount = parsePositiveAmount(args[2]);
        if (amount <= 0) {
            languageManager.send(sender, "invalid_amount");
            return;
        }
        amount = Math.min(amount, 100000);
        Map<String, Integer> counts = new LinkedHashMap<>();
        double paid = 0.0D;
        int wins = 0;
        for (int i = 0; i < amount; i++) {
            Reward reward = rewardManager.chooseReward(type, 1.0D);
            counts.merge(reward.id(), 1, Integer::sum);
            paid += reward.money();
            if (reward.isWin()) {
                wins++;
            }
        }
        languageManager.send(sender, "simulate_header", TextUtil.placeholders(
                "%type%", type.displayName(),
                "%amount%", String.valueOf(amount),
                "%wins%", String.valueOf(wins),
                "%paid%", TextUtil.money(paid),
                "%income%", TextUtil.money(type.price() * amount),
                "%profit%", TextUtil.money((type.price() * amount) - paid)
        ));
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            languageManager.send(sender, "simulate_line", TextUtil.placeholders(
                    "%reward%", entry.getKey(),
                    "%count%", String.valueOf(entry.getValue())
            ));
        }
    }

    private void resetPending(CommandSender sender, String[] args) {
        if (!sender.hasPermission("craftplay.scratchcards.admin")) {
            languageManager.send(sender, "no_permission");
            return;
        }
        if (args.length < 2) {
            languageManager.sendList(sender, "help", Map.of());
            return;
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(args[1]);
        if (!offlinePlayer.hasPlayedBefore() && offlinePlayer.getPlayer() == null) {
            languageManager.send(sender, "player_not_found", TextUtil.placeholders("%player%", args[1]));
            return;
        }
        boolean removed = sessionManager.clearPending(offlinePlayer.getUniqueId());
        languageManager.send(sender, removed ? "pending_reset_success" : "pending_reset_none", TextUtil.placeholders(
                "%player%", offlinePlayer.getName() == null ? args[1] : offlinePlayer.getName()
        ));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return tabComplete(sender, label, args);
    }

    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        try {
            if (args.length == 1) {
                return filter(List.of("shop", "claim", "daily", "history", "series", "pass", "quests", "board", "risk", "gift", "simulate",
                        "give", "reload", "stats", "info", "list", "debug", "jackpots", "resetpending", "help"), args[0]);
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("gift"))) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            }
            if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("gift"))) {
                return filter(rewardManager.types().stream().map(ScratchcardType::id).toList(), args[2]);
            }
            if (args.length == 4 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("gift"))) {
                return filter(List.of("1", "5", "10", "64"), args[3]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("simulate")) {
                return filter(rewardManager.types().stream().map(ScratchcardType::id).toList(), args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("simulate")) {
                return filter(List.of("100", "1000", "10000"), args[2]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("resetpending")) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            }
            return List.of();
        } catch (Throwable throwable) {
            diagnosticLogger.error("Fehler bei TabComplete fuer /" + label, throwable);
            return List.of();
        }
    }

    private List<String> filter(List<String> values, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }

    private int parsePositiveAmount(String value) {
        try {
            int amount = Integer.parseInt(value);
            return amount > 0 ? amount : -1;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }
}
