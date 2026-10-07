package de.craftplay.scratchcards.gui;

import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.database.DatabaseManager;
import de.craftplay.scratchcards.model.JackpotEntry;
import de.craftplay.scratchcards.model.PlayerStats;
import de.craftplay.scratchcards.model.Reward;
import de.craftplay.scratchcards.model.RewardHistoryEntry;
import de.craftplay.scratchcards.model.ScratchcardType;
import de.craftplay.scratchcards.service.FeatureService;
import de.craftplay.scratchcards.service.RewardManager;
import de.craftplay.scratchcards.service.ScratchcardItemFactory;
import de.craftplay.scratchcards.service.ScratchcardSession;
import de.craftplay.scratchcards.util.ItemBuilder;
import de.craftplay.scratchcards.util.MaterialUtil;
import de.craftplay.scratchcards.util.ServerDayUtil;
import de.craftplay.scratchcards.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class GuiManager {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("#0.##");
    private final ConfigManager configManager;
    private final RewardManager rewardManager;
    private final DatabaseManager databaseManager;
    private final ScratchcardItemFactory itemFactory;
    private final FeatureService featureService;

    public GuiManager(ConfigManager configManager, RewardManager rewardManager, DatabaseManager databaseManager,
                      ScratchcardItemFactory itemFactory, FeatureService featureService) {
        this.configManager = configManager;
        this.rewardManager = rewardManager;
        this.databaseManager = databaseManager;
        this.itemFactory = itemFactory;
        this.featureService = featureService;
    }

    public void openShop(Player player) {
        int size = normalizeSize(configManager.gui().getInt("shop.size", 27));
        ShopHolder holder = new ShopHolder();
        Inventory inventory = Bukkit.createInventory(holder, size, TextUtil.color(configManager.gui().getString("shop.title", "&8Rubellos-Shop")));
        holder.setInventory(inventory);
        refreshShop(player, holder);
        player.openInventory(inventory);
    }

    public void refreshShop(Player player, ShopHolder holder) {
        long now = System.currentTimeMillis();
        Inventory inventory = holder.getInventory();
        inventory.clear();
        holder.clearMappings();
        long dayStart = ServerDayUtil.serverDayStartMillis(now);
        holder.dailyState(dayStart, databaseManager.countDailyClaimsSince(player.getUniqueId(), dayStart) > 0);
        int maxAmount = Math.max(1, configManager.config().getInt("purchases.max_amount_per_purchase", 64));
        if (holder.amount() > maxAmount || !configManager.gui().getBoolean("shop.quantity_selector.enabled", true)) {
            holder.amount(1);
        }

        if (configManager.gui().getBoolean("shop.filler.enabled", true)) {
            ItemStack filler = namedItem("shop.filler", Map.of());
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }

        for (ScratchcardType type : rewardManager.types()) {
            int slot = configManager.gui().getInt("shop.card_slots." + type.id(), -1);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            holder.setType(slot, type.id());
            inventory.setItem(slot, shopItem(type, holder.amount()));
        }

        int infoSlot = configManager.gui().getInt("shop.info.slot", -1);
        if (infoSlot >= 0 && infoSlot < inventory.getSize()) {
            inventory.setItem(infoSlot, shopInfoItem(player, holder, dayStart));
        }

        if (configManager.gui().getBoolean("shop.quantity_selector.enabled", true)) {
            List<Integer> amounts = configManager.gui().getIntegerList("shop.quantity_selector.amounts");
            List<Integer> slots = configManager.gui().getIntegerList("shop.quantity_selector.slots");
            for (int index = 0; index < amounts.size() && index < slots.size(); index++) {
                int amount = amounts.get(index);
                int slot = slots.get(index);
                if (amount <= 0 || amount > maxAmount || slot < 0 || slot >= inventory.getSize()
                        || slot == infoSlot || holder.typeAt(slot) != null || holder.amountAt(slot) != null) {
                    continue;
                }
                String path = amount == holder.amount() ? "shop.quantity_selector.selected_item" : "shop.quantity_selector.item";
                holder.setAmountOption(slot, amount);
                inventory.setItem(slot, namedItem(path, TextUtil.placeholders("%amount%", String.valueOf(amount))));
            }
        }
        int dailySlot = configManager.gui().getInt("shop.daily.slot", 24);
        if (configManager.gui().getBoolean("shop.daily.enabled", true)
                && dailySlot >= 0 && dailySlot < inventory.getSize() && dailySlot != infoSlot
                && holder.typeAt(dailySlot) == null && holder.amountAt(dailySlot) == null) {
            holder.dailySlot(dailySlot);
            refreshDailyButton(player, holder, now);
        }
    }

    public String dailyTypeId() {
        return configManager.config().getString("daily.type", "small");
    }

    public void refreshShopIfOpen(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof ShopHolder holder) {
            refreshShop(player, holder);
        }
    }

    public void updateOpenShops() {
        long now = System.currentTimeMillis();
        long dayStart = ServerDayUtil.serverDayStartMillis(now);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof ShopHolder holder) {
                if (holder.dailyDayStart() != dayStart) {
                    refreshShop(player, holder);
                } else {
                    refreshDailyButton(player, holder, now);
                }
            }
        }
    }

    private void refreshDailyButton(Player player, ShopHolder holder, long now) {
        if (holder.dailySlot() < 0) {
            return;
        }
        long dayStart = ServerDayUtil.serverDayStartMillis(now);
        // Pro offenem Shop nur beim Oeffnen, Abholen oder Tageswechsel aus der Datenbank lesen.
        if (holder.dailyDayStart() != dayStart) {
            holder.dailyState(dayStart, databaseManager.countDailyClaimsSince(player.getUniqueId(), dayStart) > 0);
        }
        ScratchcardType type = rewardManager.type(dailyTypeId()).orElse(null);
        String reasonKey = null;
        if (!configManager.config().getBoolean("daily.enabled", true)) {
            reasonKey = "daily_button_disabled";
        } else if (!player.hasPermission("craftplay.scratchcards.daily")
                && !player.hasPermission("craftplay.scratchcards.admin")) {
            reasonKey = "daily_button_no_permission";
        } else if (type == null) {
            reasonKey = "daily_button_invalid_type";
        } else if (!featureService.isTypeAvailable(type)) {
            reasonKey = "daily_button_inactive_type";
        }
        String state = reasonKey != null ? "disabled_item" : holder.dailyClaimed() ? "claimed_item" : "available_item";
        Map<String, String> placeholders = TextUtil.placeholders(
                "%player%", player.getName(),
                "%type%", type == null ? dailyTypeId() : type.displayName(),
                "%type_id%", dailyTypeId(),
                "%amount%", String.valueOf(Math.max(1, configManager.config().getInt("daily.amount", 1))),
                "%daily_countdown%", ServerDayUtil.countdownUntilNextServerDay(now),
                "%reason%", reasonKey == null ? "" : configManager.language().getString(reasonKey, "")
        );
        holder.getInventory().setItem(holder.dailySlot(), namedItem("shop.daily." + state, placeholders));
    }

    public void openBoard(Player player) {
        int size = normalizeSize(configManager.gui().getInt("board.size", 45));
        BoardHolder holder = new BoardHolder();
        Inventory inventory = Bukkit.createInventory(holder, size, TextUtil.color(configManager.gui().getString("board.title", "&8Rubellos-Board")));
        holder.setInventory(inventory);
        if (configManager.gui().getBoolean("board.filler.enabled", true)) {
            ItemStack filler = namedItem("board.filler", Map.of());
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }
        setBoardItem(inventory, "board.items.jackpots", boardJackpotPlaceholders());
        setBoardItem(inventory, "board.items.lucky_hour", TextUtil.placeholders("%lucky_hour%", featureService.luckyHourName()));
        player.openInventory(inventory);
    }

    public void openJackpotHistory(Player player) {
        int size = normalizeSize(configManager.gui().getInt("jackpots.size", 27));
        JackpotHistoryHolder holder = new JackpotHistoryHolder();
        Inventory inventory = Bukkit.createInventory(holder, size, TextUtil.color(configManager.gui().getString("jackpots.title", "&8Jackpot-Historie")));
        holder.setInventory(inventory);

        if (configManager.gui().getBoolean("jackpots.filler.enabled", true)) {
            ItemStack filler = namedItem("jackpots.filler", Map.of());
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }

        List<Integer> slots = configManager.gui().getIntegerList("jackpots.entry_slots");
        int limit = configManager.gui().getInt("jackpots.limit", slots.isEmpty() ? 10 : slots.size());
        List<JackpotEntry> entries = databaseManager.getLatestJackpotEntries(limit);
        for (int index = 0; index < entries.size() && index < slots.size(); index++) {
            int slot = slots.get(index);
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, jackpotItem(entries.get(index), index + 1));
            }
        }

        if (entries.isEmpty()) {
            int emptySlot = configManager.gui().getInt("jackpots.empty.slot", size / 2);
            if (emptySlot >= 0 && emptySlot < inventory.getSize()) {
                inventory.setItem(emptySlot, namedItem("jackpots.empty", Map.of()));
            }
        }

        player.openInventory(inventory);
    }

    public void openPlayerHistory(Player player) {
        int size = normalizeSize(configManager.gui().getInt("history.size", 45));
        HistoryHolder holder = new HistoryHolder();
        Inventory inventory = Bukkit.createInventory(holder, size, TextUtil.color(configManager.gui().getString("history.title", "&8Deine Rubellos-Historie")));
        holder.setInventory(inventory);

        if (configManager.gui().getBoolean("history.filler.enabled", true)) {
            ItemStack filler = namedItem("history.filler", Map.of());
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }

        List<Integer> slots = configManager.gui().getIntegerList("history.entry_slots");
        int limit = configManager.gui().getInt("history.limit", slots.isEmpty() ? 21 : slots.size());
        List<RewardHistoryEntry> entries = databaseManager.getPlayerRewardHistory(player.getUniqueId(), limit);
        for (int index = 0; index < entries.size() && index < slots.size(); index++) {
            int slot = slots.get(index);
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, historyItem(entries.get(index), index + 1));
            }
        }

        if (entries.isEmpty()) {
            int emptySlot = configManager.gui().getInt("history.empty.slot", size / 2);
            if (emptySlot >= 0 && emptySlot < inventory.getSize()) {
                inventory.setItem(emptySlot, namedItem("history.empty", Map.of()));
            }
        }

        player.openInventory(inventory);
    }

    private ItemStack shopItem(ScratchcardType type, int amount) {
        Map<String, String> placeholders = TextUtil.placeholders(
                "%type%", type.displayName(),
                "%type_id%", type.id(),
                "%price%", TextUtil.money(type.price()),
                "%unit_price%", TextUtil.money(type.price()),
                "%amount%", String.valueOf(amount),
                "%total_price%", TextUtil.money(type.price() * amount),
                "%chance%", PERCENT_FORMAT.format(rewardManager.winChancePercent(type)),
                "%win_chance%", PERCENT_FORMAT.format(rewardManager.winChancePercent(type)),
                "%active%", featureService.isTypeAvailable(type) ? "Ja" : "Nein",
                "%lucky_hour%", featureService.luckyHourName()
        );
        String name = TextUtil.replace(configManager.gui().getString("shop.card_item.name", "%type%"), placeholders);
        List<String> lore = shopItemLore(type, placeholders);
        if (!type.buyable() || !featureService.isTypeAvailable(type)) {
            lore.addAll(configManager.gui().getStringList("shop.unavailable_lore"));
        }
        return new ItemBuilder(type.shopMaterial())
                .name(name)
                .lore(lore)
                .hideAttributes()
                .build();
    }

    private List<String> shopItemLore(ScratchcardType type, Map<String, String> placeholders) {
        List<String> lore = new ArrayList<>();
        for (String line : configManager.gui().getStringList("shop.card_item.lore")) {
            if (line.contains("%reward_chances%")) {
                lore.addAll(rewardChanceLore(type));
                continue;
            }
            lore.add(TextUtil.replace(line, placeholders));
        }
        return lore;
    }

    private List<String> rewardChanceLore(ScratchcardType type) {
        String format = configManager.gui().getString("shop.card_item.reward_chance_line", "&8- &f%reward%&7: &e%chance%%");
        List<String> lines = new ArrayList<>();
        for (Reward reward : type.rewards()) {
            lines.add(TextUtil.replace(format, TextUtil.placeholders(
                    "%reward%", reward.displayName(),
                    "%reward_id%", reward.id(),
                    "%rarity%", featureService.rarityDisplay(reward),
                    "%chance%", PERCENT_FORMAT.format(rewardManager.effectiveChancePercent(type, reward)),
                    "%weight%", PERCENT_FORMAT.format(reward.chance())
            )));
        }
        return lines;
    }

    private ItemStack shopInfoItem(Player player, ShopHolder holder, long dayStart) {
        PlayerStats stats = databaseManager.getPlayerStats(player.getUniqueId(), player.getName());
        int boughtToday = databaseManager.countPurchasesSince(player.getUniqueId(), dayStart);
        int openedToday = databaseManager.countOpensSince(player.getUniqueId(), dayStart);
        boolean dailyAvailable = configManager.config().getBoolean("daily.enabled", true) && !holder.dailyClaimed();
        boolean limitsEnabled = configManager.config().getBoolean("limits.enabled", true);
        int purchaseLimit = limitsEnabled ? configManager.config().getInt("limits.max_purchases_per_day", 25) : 0;
        int owned = itemFactory.countOwned(player);
        Map<String, String> placeholders = TextUtil.placeholders(
                "%player%", player.getName(),
                "%cpsc_bought%", String.valueOf(stats.bought()),
                "%cpsc_opened%", String.valueOf(stats.opened()),
                "%cpsc_won_money%", TextUtil.money(stats.wonMoney()),
                "%cpsc_jackpots%", String.valueOf(stats.jackpots()),
                "%cpsc_best_win%", TextUtil.money(stats.bestWin()),
                "%daily_bought%", String.valueOf(boughtToday),
                "%daily_limit%", purchaseLimit > 0 ? String.valueOf(purchaseLimit) : "-",
                "%daily_remaining%", purchaseLimit > 0 ? String.valueOf(Math.max(0, purchaseLimit - boughtToday)) : "-",
                "%daily_opened%", String.valueOf(openedToday),
                "%daily_available%", dailyAvailable ? "Ja" : "Nein",
                "%daily_status%", dailyAvailable ? "Verfügbar" : "Abgeholt",
                "%owned%", String.valueOf(owned)
        );
        return namedItem("shop.info", placeholders);
    }

    public void openScratchcard(Player player, ScratchcardSession session) {
        Inventory inventory = createScratchcardInventory(session);
        session.inventory(inventory);
        refreshScratchcard(session);
        player.openInventory(inventory);
    }

    public void refreshScratchcard(ScratchcardSession session) {
        Inventory inventory = session.inventory();
        if (inventory == null) {
            return;
        }
        inventory.clear();

        if (configManager.gui().getBoolean("scratchcard.border.enabled", true)) {
            ItemStack border = namedItem("scratchcard.border", Map.of());
            for (int slot : configManager.gui().getIntegerList("scratchcard.border.slots")) {
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, border);
                }
            }
        }

        List<Integer> scratchSlots = scratchSlots();
        for (int index = 0; index < scratchSlots.size(); index++) {
            int slot = scratchSlots.get(index);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            if (session.openedIndices().contains(index)) {
                inventory.setItem(slot, revealedItem(session, index));
            } else {
                inventory.setItem(slot, namedItem("scratchcard.hidden_item", Map.of()));
            }
        }

        int infoSlot = configManager.gui().getInt("scratchcard.info.slot", -1);
        if (infoSlot >= 0 && infoSlot < inventory.getSize()) {
            inventory.setItem(infoSlot, scratchcardInfoItem(session));
        }

        if (session.paidOut()) {
            int resultSlot = configManager.gui().getInt("scratchcard.result.slot", configManager.gui().getInt("scratchcard.close.slot", -1));
            if (resultSlot >= 0 && resultSlot < inventory.getSize()) {
                inventory.setItem(resultSlot, resultItem(session));
            }
        } else {
            int closeSlot = configManager.gui().getInt("scratchcard.close.slot", -1);
            if (closeSlot >= 0 && closeSlot < inventory.getSize()) {
                inventory.setItem(closeSlot, namedItem("scratchcard.close", Map.of()));
            }
        }
    }

    private Inventory createScratchcardInventory(ScratchcardSession session) {
        int size = normalizeSize(configManager.gui().getInt("scratchcard.size", 45));
        ScratchcardHolder holder = new ScratchcardHolder(session.playerId());
        Map<String, String> placeholders = TextUtil.placeholders(
                "%type%", session.type().displayName(),
                "%type_id%", session.type().id(),
                "%reward%", session.reward().displayName()
        );
        String title = TextUtil.replace(configManager.gui().getString("scratchcard.title", "&8Rubellos"), placeholders);
        Inventory inventory = Bukkit.createInventory(holder, size, TextUtil.color(title));
        holder.setInventory(inventory);
        return inventory;
    }

    private ItemStack scratchcardInfoItem(ScratchcardSession session) {
        int opened = session.openedIndices().size();
        int total = scratchSlots().size();
        Map<String, String> placeholders = TextUtil.placeholders(
                "%type%", session.type().displayName(),
                "%type_id%", session.type().id(),
                "%reward%", session.reward().displayName(),
                "%opened%", String.valueOf(opened),
                "%remaining%", String.valueOf(Math.max(0, total - opened))
        );
        return namedItem("scratchcard.info", placeholders);
    }

    private ItemStack revealedItem(ScratchcardSession session, int index) {
        Reward reward = rewardAt(session, index);
        Map<String, String> placeholders = TextUtil.placeholders(
                "%reward%", reward.displayName(),
                "%opened%", String.valueOf(index + 1)
        );
        String name = TextUtil.replace(configManager.gui().getString("scratchcard.revealed_item.name", "%reward%"), placeholders);
        List<String> lore = TextUtil.replace(configManager.gui().getStringList("scratchcard.revealed_item.lore"), placeholders);
        return new ItemBuilder(reward.symbolMaterial()).name(name).lore(lore).hideAttributes().build();
    }

    private ItemStack jackpotItem(JackpotEntry entry, int position) {
        Map<String, String> placeholders = TextUtil.placeholders(
                "%position%", String.valueOf(position),
                "%player%", entry.playerName(),
                "%type%", entry.typeId(),
                "%reward%", entry.rewardName(),
                "%money%", TextUtil.money(entry.money()),
                "%date%", DATE_FORMATTER.format(Instant.ofEpochMilli(entry.createdAt()))
        );
        return namedItem("jackpots.entry_item", placeholders);
    }

    private ItemStack historyItem(RewardHistoryEntry entry, int position) {
        Map<String, String> placeholders = TextUtil.placeholders(
                "%position%", String.valueOf(position),
                "%type%", entry.typeId(),
                "%reward%", entry.rewardName(),
                "%reward_id%", entry.rewardId(),
                "%money%", TextUtil.money(entry.money()),
                "%jackpot%", entry.jackpot() ? "Ja" : "Nein",
                "%date%", DATE_FORMATTER.format(Instant.ofEpochMilli(entry.createdAt()))
        );
        return namedItem("history.entry_item", placeholders);
    }

    private void setBoardItem(Inventory inventory, String path, Map<String, String> placeholders) {
        int slot = configManager.gui().getInt(path + ".slot", -1);
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, namedItem(path, placeholders));
        }
    }

    private Map<String, String> boardJackpotPlaceholders() {
        List<String> jackpots = databaseManager.getLatestJackpots(configManager.config().getInt("stats.latest_jackpots_limit", 5));
        return TextUtil.placeholders(
                "%latest_jackpots%", jackpots.isEmpty() ? "-" : String.join(", ", jackpots),
                "%total_opens%", String.valueOf(databaseManager.countTotalOpens())
        );
    }

    private ItemStack resultItem(ScratchcardSession session) {
        Reward reward = session.reward();
        Map<String, String> placeholders = TextUtil.placeholders(
                "%type%", session.type().displayName(),
                "%type_id%", session.type().id(),
                "%reward%", reward.displayName(),
                "%money%", TextUtil.money(reward.money())
        );
        Material material = MaterialUtil.parse(configManager.gui().getString("scratchcard.result.material"), Material.CHEST);
        String name = TextUtil.replace(configManager.gui().getString("scratchcard.result.name", "&aErgebnis"), placeholders);
        String lorePath = reward.isWin() ? "scratchcard.result.lore_win" : "scratchcard.result.lore_lose";
        List<String> lore = TextUtil.replace(configManager.gui().getStringList(lorePath), placeholders);
        return new ItemBuilder(material).name(name).lore(lore).hideAttributes().build();
    }

    public Reward rewardAt(ScratchcardSession session, int index) {
        if (index < 0 || index >= session.symbolRewardIds().size()) {
            return session.reward();
        }
        Reward reward = session.type().rewardById(session.symbolRewardIds().get(index));
        return reward == null ? session.reward() : reward;
    }

    public int scratchIndexBySlot(int slot) {
        List<Integer> slots = scratchSlots();
        for (int index = 0; index < slots.size(); index++) {
            if (slots.get(index) == slot) {
                return index;
            }
        }
        return -1;
    }

    public boolean isCloseSlot(int slot) {
        return configManager.gui().getInt("scratchcard.close.slot", -1) == slot;
    }

    public List<Integer> scratchSlots() {
        return configManager.gui().getIntegerList("scratchcard.scratch_slots");
    }

    private ItemStack namedItem(String path, Map<String, String> placeholders) {
        Material material = MaterialUtil.parse(configManager.gui().getString(path + ".material"), Material.STONE);
        String name = TextUtil.replace(configManager.gui().getString(path + ".name", " "), placeholders);
        List<String> lore = TextUtil.replace(configManager.gui().getStringList(path + ".lore"), placeholders);
        return new ItemBuilder(material).name(name).lore(lore).hideAttributes().build();
    }

    private int normalizeSize(int size) {
        int normalized = Math.max(9, Math.min(54, size));
        return (normalized / 9) * 9;
    }
}
