package de.craftplay.scratchcards.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class ShopHolder implements InventoryHolder {
    private final Map<Integer, String> slotTypes = new HashMap<>();
    private final Map<Integer, Integer> slotAmounts = new HashMap<>();
    private int amount = 1;
    private int dailySlot = -1;
    private long dailyDayStart = Long.MIN_VALUE;
    private boolean dailyClaimed;
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
        dailySlot = -1;
        dailyDayStart = Long.MIN_VALUE;
    }

    public void dailySlot(int slot) {
        dailySlot = slot;
    }

    public int dailySlot() {
        return dailySlot;
    }

    public boolean isDailySlot(int slot) {
        return dailySlot >= 0 && dailySlot == slot;
    }

    public void dailyState(long dayStart, boolean claimed) {
        dailyDayStart = dayStart;
        dailyClaimed = claimed;
    }

    public long dailyDayStart() {
        return dailyDayStart;
    }

    public boolean dailyClaimed() {
        return dailyClaimed;
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
