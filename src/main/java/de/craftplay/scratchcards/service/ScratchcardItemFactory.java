package de.craftplay.scratchcards.service;

import de.craftplay.scratchcards.CraftplayScratchcardsPlugin;
import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.model.ScratchcardType;
import de.craftplay.scratchcards.util.ItemBuilder;
import de.craftplay.scratchcards.util.TextUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ScratchcardItemFactory {
    private final ConfigManager configManager;
    private final NamespacedKey typeKey;

    public ScratchcardItemFactory(CraftplayScratchcardsPlugin plugin, ConfigManager configManager) {
        this.configManager = configManager;
        this.typeKey = new NamespacedKey(plugin, "scratchcard_type");
    }

    public ItemStack create(ScratchcardType type, int amount) {
        Map<String, String> placeholders = TextUtil.placeholders(
                "%type%", type.displayName(),
                "%type_id%", type.id(),
                "%price%", TextUtil.money(type.price())
        );
        String name = TextUtil.replace(configManager.gui().getString("items.scratchcard.name", "%type%"), placeholders);
        List<String> lore = TextUtil.replace(configManager.gui().getStringList("items.scratchcard.lore"), placeholders);
        ItemStack item = new ItemBuilder(type.material())
                .amount(amount)
                .name(name)
                .lore(lore)
                .hideAttributes()
                .build();

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    public Optional<String> readType(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String value = container.get(typeKey, PersistentDataType.STRING);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    public int countOwned(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (readType(item).isPresent()) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public int countOwned(Player player, String typeId) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            Optional<String> itemType = readType(item);
            if (itemType.isPresent() && itemType.get().equalsIgnoreCase(typeId)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public boolean canFit(Player player, ScratchcardType type, int amount) {
        return freeCapacity(player, type) >= Math.max(1, amount);
    }

    public boolean removeOwned(Player player, String typeId, int amount) {
        int remaining = Math.max(1, amount);
        if (countOwned(player, typeId) < remaining) {
            return false;
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            Optional<String> itemType = readType(item);
            if (item == null || itemType.isEmpty() || !itemType.get().equalsIgnoreCase(typeId)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) {
                contents[slot] = null;
            }
            remaining -= take;
        }
        player.getInventory().setStorageContents(contents);
        return remaining <= 0;
    }

    public int freeCapacity(Player player, ScratchcardType type) {
        ItemStack probe = create(type, 1);
        int capacity = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                capacity += probe.getMaxStackSize();
            } else if (item.isSimilar(probe) && item.getAmount() < item.getMaxStackSize()) {
                capacity += item.getMaxStackSize() - item.getAmount();
            }
        }
        return capacity;
    }
}
