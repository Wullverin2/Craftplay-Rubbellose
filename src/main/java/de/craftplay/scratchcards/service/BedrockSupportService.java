package de.craftplay.scratchcards.service;

import de.craftplay.scratchcards.CraftplayScratchcardsPlugin;
import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.config.LanguageManager;
import de.craftplay.scratchcards.diagnostic.DiagnosticLogger;
import de.craftplay.scratchcards.util.ItemBuilder;
import de.craftplay.scratchcards.util.MaterialUtil;
import de.craftplay.scratchcards.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BedrockSupportService {
    private final CraftplayScratchcardsPlugin plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final DiagnosticLogger diagnosticLogger;
    private final NamespacedKey shopOpenerKey;
    private boolean geyserWarningLogged;
    private boolean floodgateWarningLogged;

    public BedrockSupportService(CraftplayScratchcardsPlugin plugin, ConfigManager configManager,
                                 LanguageManager languageManager, DiagnosticLogger diagnosticLogger) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.languageManager = languageManager;
        this.diagnosticLogger = diagnosticLogger;
        this.shopOpenerKey = new NamespacedKey(plugin, "shop_opener");
    }

    public void handleJoin(Player player) {
        if (!shopItemEnabled() || !configManager.config().getBoolean("bedrock_support.shop_item.give_on_join", true)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !shouldReceiveShopItem(player)) {
                return;
            }
            boolean added = ensureShopOpener(player);
            if (added && configManager.config().getBoolean("bedrock_support.notify_on_join", true)) {
                languageManager.send(player, "bedrock_shop_opener_given");
            }
        }, 20L);
    }

    public boolean isShopOpener(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(shopOpenerKey, PersistentDataType.STRING);
    }

    public boolean isBedrockPlayer(Player player) {
        if (!configManager.config().getBoolean("bedrock_support.enabled", true)) {
            return false;
        }
        if (configManager.config().getBoolean("bedrock_support.detect_geyser", true) && isGeyserPlayer(player.getUniqueId())) {
            return true;
        }
        if (configManager.config().getBoolean("bedrock_support.detect_floodgate", true) && isFloodgatePlayer(player.getUniqueId())) {
            return true;
        }
        String playerName = player.getName();
        for (String prefix : configManager.config().getStringList("bedrock_support.name_prefixes")) {
            if (!prefix.isBlank() && playerName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean ensureShopOpener(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        boolean found = false;
        boolean changed = false;
        boolean keepSingle = configManager.config().getBoolean("bedrock_support.shop_item.keep_single", true);

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!isShopOpener(item)) {
                continue;
            }
            if (!found) {
                found = true;
                continue;
            }
            if (keepSingle) {
                contents[slot] = null;
                changed = true;
            }
        }
        if (changed) {
            inventory.setStorageContents(contents);
        }
        if (found) {
            return false;
        }

        ItemStack opener = createShopOpener();
        int slot = configManager.config().getInt("bedrock_support.shop_item.slot", 8);
        if (slot >= 0 && slot < contents.length && (contents[slot] == null || contents[slot].getType().isAir())) {
            contents[slot] = opener;
            inventory.setStorageContents(contents);
            return true;
        }
        return inventory.addItem(opener).isEmpty();
    }

    private ItemStack createShopOpener() {
        Map<String, String> placeholders = TextUtil.placeholders("%command%", "/rubbellos");
        Material material = MaterialUtil.parse(configManager.config().getString("bedrock_support.shop_item.material"), Material.SUNFLOWER);
        String name = TextUtil.replace(configManager.config().getString("bedrock_support.shop_item.name", "&6Rubellos-Menue"), placeholders);
        List<String> lore = TextUtil.replace(configManager.config().getStringList("bedrock_support.shop_item.lore"), placeholders);
        ItemStack item = new ItemBuilder(material)
                .name(name)
                .lore(lore)
                .hideAttributes()
                .build();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(shopOpenerKey, PersistentDataType.STRING, "shop");
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean shouldReceiveShopItem(Player player) {
        if (!shopItemEnabled()) {
            return false;
        }
        if (!configManager.config().getBoolean("bedrock_support.shop_item.only_bedrock", true)) {
            return true;
        }
        return isBedrockPlayer(player);
    }

    private boolean shopItemEnabled() {
        return configManager.config().getBoolean("bedrock_support.enabled", true)
                && configManager.config().getBoolean("bedrock_support.shop_item.enabled", true);
    }

    private boolean isGeyserPlayer(UUID playerId) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Method apiMethod = apiClass.getMethod("api");
            Object api = apiMethod.invoke(null);
            Method isBedrockPlayer = apiClass.getMethod("isBedrockPlayer", UUID.class);
            Object result = isBedrockPlayer.invoke(api, playerId);
            return result instanceof Boolean value && value;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (Throwable throwable) {
            if (!geyserWarningLogged) {
                geyserWarningLogged = true;
                diagnosticLogger.warning("Geyser-Bedrock-Erkennung konnte nicht genutzt werden.", throwable);
            }
            return false;
        }
    }

    private boolean isFloodgatePlayer(UUID playerId) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method instanceMethod = apiClass.getMethod("getInstance");
            Object api = instanceMethod.invoke(null);
            Method isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            Object result = isFloodgatePlayer.invoke(api, playerId);
            return result instanceof Boolean value && value;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (Throwable throwable) {
            if (!floodgateWarningLogged) {
                floodgateWarningLogged = true;
                diagnosticLogger.warning("Floodgate-Bedrock-Erkennung konnte nicht genutzt werden.", throwable);
            }
            return false;
        }
    }
}
