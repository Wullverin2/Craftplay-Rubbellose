package de.craftplay.scratchcards.service;

import de.craftplay.scratchcards.CraftplayScratchcardsPlugin;
import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.config.LanguageManager;
import de.craftplay.scratchcards.database.DatabaseManager;
import de.craftplay.scratchcards.diagnostic.DiagnosticLogger;
import de.craftplay.scratchcards.economy.EconomyManager;
import de.craftplay.scratchcards.gui.GuiManager;
import de.craftplay.scratchcards.model.PendingScratchcard;
import de.craftplay.scratchcards.model.Reward;
import de.craftplay.scratchcards.model.RewardItem;
import de.craftplay.scratchcards.model.ScratchcardType;
import de.craftplay.scratchcards.util.ItemBuilder;
import de.craftplay.scratchcards.util.ServerDayUtil;
import de.craftplay.scratchcards.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ScratchcardSessionManager {
    private final CraftplayScratchcardsPlugin plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final DatabaseManager databaseManager;
    private final EconomyManager economyManager;
    private final RewardManager rewardManager;
    private final ScratchcardItemFactory itemFactory;
    private final GuiManager guiManager;
    private final DiagnosticLogger diagnosticLogger;
    private final FeedbackService feedbackService;
    private final FeatureService featureService;
    private final ProgressionService progressionService;
    private final Map<UUID, ScratchcardSession> activeSessions = new HashMap<>();
    private final Map<UUID, Long> openCooldowns = new HashMap<>();

    public ScratchcardSessionManager(CraftplayScratchcardsPlugin plugin, ConfigManager configManager,
                                     LanguageManager languageManager, DatabaseManager databaseManager,
                                     EconomyManager economyManager, RewardManager rewardManager,
                                     ScratchcardItemFactory itemFactory, GuiManager guiManager,
                                     DiagnosticLogger diagnosticLogger, FeedbackService feedbackService,
                                     FeatureService featureService, ProgressionService progressionService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.languageManager = languageManager;
        this.databaseManager = databaseManager;
        this.economyManager = economyManager;
        this.rewardManager = rewardManager;
        this.itemFactory = itemFactory;
        this.guiManager = guiManager;
        this.diagnosticLogger = diagnosticLogger;
        this.feedbackService = feedbackService;
        this.featureService = featureService;
        this.progressionService = progressionService;
    }

    public void startFromHand(Player player, EquipmentSlot hand) {
        ItemStack item = hand == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        Optional<String> typeId = itemFactory.readType(item);
        if (typeId.isEmpty()) {
            return;
        }
        if (!player.hasPermission("craftplay.scratchcards.use")) {
            languageManager.send(player, "no_permission");
            return;
        }
        if (isOpenCooldown(player)) {
            languageManager.send(player, "open_cooldown");
            return;
        }
        if (hasOpenScratchcard(player.getUniqueId())) {
            languageManager.send(player, "already_running");
            return;
        }
        if (configManager.config().getBoolean("limits.enabled", true)) {
            int perDay = configManager.config().getInt("limits.max_opens_per_day", 25);
            int openedToday = dailyOpenCount(player);
            if (perDay > 0 && openedToday >= perDay) {
                languageManager.send(player, "open_limit_day", TextUtil.placeholders(
                        "%daily_opened%", String.valueOf(openedToday),
                        "%daily_open_limit%", String.valueOf(perDay),
                        "%daily_open_remaining%", String.valueOf(Math.max(0, perDay - openedToday))
                ));
                return;
            }
        }

        Optional<ScratchcardType> optionalType = rewardManager.type(typeId.get());
        if (optionalType.isEmpty()) {
            languageManager.send(player, "type_not_found", TextUtil.placeholders("%type%", typeId.get()));
            return;
        }
        if (!featureService.isTypeAvailable(optionalType.get())) {
            featureService.sendTypeUnavailable(player, optionalType.get());
            return;
        }

        removeOne(player, hand);
        ScratchcardType type = optionalType.get();
        Reward reward = rewardManager.chooseReward(type, featureService.luckyWinChanceMultiplier());
        int pityLosses = progressionService.pityLosses(player.getUniqueId(), type.id());
        if (pityLosses + 1 >= progressionService.pityThreshold()) {
            Reward guaranteed = type.rewardById(progressionService.guaranteedRewardId());
            if (guaranteed == null || !guaranteed.isWin()) {
                guaranteed = type.rewards().stream().filter(Reward::isWin).findFirst().orElse(guaranteed);
            }
            if (guaranteed != null && guaranteed.isWin()) {
                reward = guaranteed;
                languageManager.send(player, "pity_guaranteed", TextUtil.placeholders(
                        "%reward%", guaranteed.displayName(),
                        "%losses%", String.valueOf(pityLosses)
                ));
            }
        }
        int fieldCount = Math.max(1, guiManager.scratchSlots().size());
        int winningMatches = Math.max(2, configManager.config().getInt("scratchcard.result.winning_matches", 3));
        List<String> symbols = rewardManager.createSymbols(type, reward, fieldCount, winningMatches);

        ScratchcardSession session = new ScratchcardSession(player.getUniqueId(), player.getName(), type, reward,
                symbols, new LinkedHashSet<>(), System.currentTimeMillis());
        activeSessions.put(player.getUniqueId(), session);
        databaseManager.savePending(session.toPending());
        databaseManager.recordOpen(player.getUniqueId(), player.getName(), type.id());
        progressionService.onOpen(player);
        openCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        languageManager.send(player, "started");
        feedbackService.play(player, "start");
        runLoading(player, session);
    }

    private void removeOne(Player player, EquipmentSlot hand) {
        ItemStack item = hand == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        if (item.getAmount() <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
            return;
        }
        item.setAmount(item.getAmount() - 1);
    }

    private void runLoading(Player player, ScratchcardSession session) {
        if (!configManager.config().getBoolean("scratchcard.loading.enabled", true)) {
            guiManager.openScratchcard(player, session);
            return;
        }

        int durationTicks = Math.max(1, configManager.config().getInt("scratchcard.loading.duration_ticks", 60));
        int steps = Math.max(1, configManager.config().getInt("scratchcard.loading.steps", 20));
        int period = Math.max(1, durationTicks / steps);
        session.loading(true);
        BukkitRunnable runnable = new BukkitRunnable() {
            private int currentStep = 0;

            @Override
            public void run() {
                try {
                    Player online = Bukkit.getPlayer(session.playerId());
                    if (online == null || !online.isOnline()) {
                        cancel();
                        session.loading(false);
                        return;
                    }
                    if (currentStep > steps) {
                        online.sendActionBar(TextUtil.component(configManager.config().getString("scratchcard.loading.actionbar.finished", "&aRubellos bereit!")));
                        session.loading(false);
                        guiManager.openScratchcard(online, session);
                        cancel();
                        return;
                    }
                    int percent = (int) Math.round((currentStep * 100.0D) / steps);
                    String bar = progressBar(percent);
                    String text = configManager.config().getString("scratchcard.loading.actionbar.text",
                            "&6Rubellos wird generiert... %bar% &e%percent%%");
                    online.sendActionBar(TextUtil.component(TextUtil.replace(text, TextUtil.placeholders(
                            "%bar%", bar,
                            "%percent%", String.valueOf(percent),
                            "%progress%", String.valueOf(currentStep)
                    ))));
                    currentStep++;
                } catch (Throwable throwable) {
                    diagnosticLogger.error("Fehler in der Rubellos-Ladeanimation.", throwable);
                    cancel();
                    session.loading(false);
                }
            }
        };
        session.loadingTask(runnable.runTaskTimer(plugin, 0L, period));
    }

    private String progressBar(int percent) {
        int length = Math.max(1, configManager.config().getInt("scratchcard.loading.actionbar.bar.length", 10));
        String filled = configManager.config().getString("scratchcard.loading.actionbar.bar.filled", "&a█");
        String empty = configManager.config().getString("scratchcard.loading.actionbar.bar.empty", "&7░");
        int filledCount = (int) Math.round(length * (percent / 100.0D));
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append(i < filledCount ? filled : empty);
        }
        return builder.toString();
    }

    public void claim(Player player) {
        ScratchcardSession session = activeSessions.get(player.getUniqueId());
        if (session != null) {
            if (session.loading()) {
                languageManager.send(player, "started");
                return;
            }
            guiManager.openScratchcard(player, session);
            languageManager.send(player, "claim_opened");
            return;
        }

        PendingScratchcard pending = databaseManager.loadPending(player.getUniqueId());
        if (pending == null) {
            languageManager.send(player, "no_pending");
            return;
        }
        Optional<ScratchcardType> optionalType = rewardManager.type(pending.typeId());
        if (optionalType.isEmpty()) {
            languageManager.send(player, "type_not_found", TextUtil.placeholders("%type%", pending.typeId()));
            return;
        }
        Reward reward = optionalType.get().rewardById(pending.rewardId());
        if (reward == null) {
            languageManager.send(player, "type_not_found", TextUtil.placeholders("%type%", pending.rewardId()));
            return;
        }
        session = new ScratchcardSession(player.getUniqueId(), player.getName(), optionalType.get(), reward,
                pending.symbolRewardIds(), pending.openedIndices(), pending.createdAt());
        activeSessions.put(player.getUniqueId(), session);
        guiManager.openScratchcard(player, session);
        languageManager.send(player, "claim_opened");
    }

    public void reveal(Player player, int index) {
        ScratchcardSession session = activeSessions.get(player.getUniqueId());
        if (session == null || session.paidOut() || index < 0 || index >= session.symbolRewardIds().size()) {
            return;
        }
        if (!session.openedIndices().add(index)) {
            return;
        }
        databaseManager.savePending(session.toPending());
        guiManager.refreshScratchcard(session);
        Reward visibleReward = guiManager.rewardAt(session, index);
        feedbackService.play(player, "reveal");
        player.sendActionBar(TextUtil.component(TextUtil.replace(languageManager.message("field_revealed", Map.of()), TextUtil.placeholders(
                "%reward%", visibleReward.displayName()
        ))));

        int required = Math.max(1, configManager.config().getInt("scratchcard.gui.required_opened_fields", guiManager.scratchSlots().size()));
        if (session.openedIndices().size() >= required) {
            payout(player, session);
        }
    }

    private void payout(Player player, ScratchcardSession session) {
        if (session.paidOut()) {
            return;
        }
        session.paidOut(true);
        Reward reward = session.reward();

        double mysteryMultiplier = featureService.rollMysteryMultiplier(player, reward);
        double luckyMoneyMultiplier = featureService.luckyMoneyMultiplier();
        double finalMoney = reward.money() * mysteryMultiplier * luckyMoneyMultiplier;
        String rewardName = reward.displayName();
        if (mysteryMultiplier > 1.0D || luckyMoneyMultiplier > 1.0D) {
            rewardName = reward.displayName() + " x" + formatMultiplier(mysteryMultiplier * luckyMoneyMultiplier);
        }

        if (finalMoney > 0.0D) {
            economyManager.deposit(player, finalMoney);
        }
        for (RewardItem item : reward.items()) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemBuilder(item.material())
                    .amount(item.amount())
                    .name(item.name())
                    .lore(item.lore())
                    .hideAttributes()
                    .build());
            for (ItemStack stack : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
        }
        for (String command : reward.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
        }

        databaseManager.recordReward(player.getUniqueId(), player.getName(), session.type().id(), reward, finalMoney, rewardName);
        databaseManager.deletePending(player.getUniqueId());
        activeSessions.remove(player.getUniqueId());
        guiManager.refreshScratchcard(session);
        featureService.handleSeries(player, session.type(), reward);
        featureService.handleServerGoal();
        progressionService.updatePity(player, session.type().id(), reward.isWin());
        if (reward.isWin()) {
            progressionService.onWin(player, reward.broadcast());
        }
        progressionService.addRiskOffer(player, finalMoney, rewardName);

        if (reward.isWin()) {
            languageManager.send(player, "reward_win", TextUtil.placeholders(
                    "%reward%", rewardName,
                    "%money%", economyManager.format(finalMoney)
            ));
        } else {
            languageManager.send(player, "reward_lose");
        }

        Map<String, String> feedbackPlaceholders = TextUtil.placeholders(
                "%reward%", rewardName,
                "%money%", economyManager.format(finalMoney),
                "%type%", session.type().displayName()
        );
        if (reward.broadcast()) {
            feedbackService.play(player, "jackpot");
            feedbackService.title(player, "jackpot", feedbackPlaceholders);
        } else if (reward.isWin()) {
            feedbackService.play(player, "win");
            feedbackService.title(player, "win", feedbackPlaceholders);
        } else {
            feedbackService.play(player, "lose");
            feedbackService.title(player, "lose", feedbackPlaceholders);
        }

        if (reward.broadcast()) {
            String message = languageManager.message("reward_broadcast", TextUtil.placeholders(
                    "%player%", player.getName(),
                    "%reward%", rewardName,
                    "%money%", economyManager.format(finalMoney)
            ));
            Bukkit.broadcastMessage(TextUtil.color(message));
        }

        int closeTicks = configManager.config().getInt("scratchcard.gui.auto_close_after_payout_ticks", 60);
        if (closeTicks > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    if (player.isOnline() && player.getOpenInventory().getTopInventory().equals(session.inventory())) {
                        player.closeInventory();
                    }
                } catch (Throwable throwable) {
                    diagnosticLogger.error("Fehler beim automatischen Schliessen des Rubellos-GUIs.", throwable);
                }
            }, closeTicks);
        }
    }

    public void handleQuit(Player player) {
        ScratchcardSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.loadingTask() != null) {
            session.loadingTask().cancel();
        }
        if (!session.paidOut()) {
            databaseManager.savePending(session.toPending());
        }
    }

    public boolean clearPending(UUID uuid) {
        ScratchcardSession session = activeSessions.remove(uuid);
        boolean removed = false;
        if (session != null) {
            if (session.loadingTask() != null) {
                session.loadingTask().cancel();
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && session.inventory() != null
                    && player.getOpenInventory().getTopInventory().equals(session.inventory())) {
                player.closeInventory();
            }
            removed = true;
        }
        return databaseManager.deletePendingByUuid(uuid) || removed;
    }

    public int activeSessionCount() {
        return activeSessions.size();
    }

    public void shutdown() {
        for (ScratchcardSession session : activeSessions.values()) {
            if (session.loadingTask() != null) {
                session.loadingTask().cancel();
            }
            if (!session.paidOut()) {
                databaseManager.savePending(session.toPending());
            }
        }
        activeSessions.clear();
    }

    public boolean hasOpenScratchcard(UUID uuid) {
        return activeSessions.containsKey(uuid) || databaseManager.hasPending(uuid);
    }

    private boolean isOpenCooldown(Player player) {
        int seconds = configManager.config().getInt("cooldown.open_seconds", 3);
        if (seconds <= 0) {
            return false;
        }
        long last = openCooldowns.getOrDefault(player.getUniqueId(), 0L);
        return System.currentTimeMillis() - last < seconds * 1000L;
    }

    private int dailyOpenCount(Player player) {
        return databaseManager.countOpensSince(player.getUniqueId(), ServerDayUtil.currentServerDayStartMillis());
    }

    private String formatMultiplier(double multiplier) {
        if (Math.rint(multiplier) == multiplier) {
            return String.valueOf((int) multiplier);
        }
        return String.format(java.util.Locale.GERMANY, "%.2f", multiplier);
    }
}
