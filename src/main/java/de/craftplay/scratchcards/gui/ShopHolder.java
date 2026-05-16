package de.craftplay.scratchcards.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class ShopHolder implements InventoryHolder {
    private final Map<Integer, String> slotTypes = new HashMap<>();
    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void setType(int slot, String typeId) {
        slotTypes.put(slot, typeId);
    }

    public String typeAt(int slot) {
        return slotTypes.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
