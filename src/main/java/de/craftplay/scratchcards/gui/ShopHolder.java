package de.craftplay.scratchcards.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class ShopHolder implements InventoryHolder {
    private final Map<Integer, String> slotTypes = new HashMap<>();
    private final Map<Integer, Integer> slotAmounts = new HashMap<>();
    private int amount = 1;
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

    public void clearMappings() {
        slotTypes.clear();
        slotAmounts.clear();
    }

    public void setAmountOption(int slot, int amount) {
        slotAmounts.put(slot, amount);
    }

    public Integer amountAt(int slot) {
        return slotAmounts.get(slot);
    }

    public int amount() {
        return amount;
    }

    public void amount(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Die Kaufmenge muss positiv sein.");
        }
        this.amount = amount;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
