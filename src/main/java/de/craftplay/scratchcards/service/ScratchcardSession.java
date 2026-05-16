package de.craftplay.scratchcards.service;

import de.craftplay.scratchcards.model.PendingScratchcard;
import de.craftplay.scratchcards.model.Reward;
import de.craftplay.scratchcards.model.ScratchcardType;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ScratchcardSession {
    private final UUID playerId;
    private final String playerName;
    private final ScratchcardType type;
    private final Reward reward;
    private final List<String> symbolRewardIds;
    private final Set<Integer> openedIndices;
    private final long createdAt;
    private Inventory inventory;
    private BukkitTask loadingTask;
    private boolean paidOut;
    private boolean loading;

    public ScratchcardSession(UUID playerId, String playerName, ScratchcardType type, Reward reward,
                              List<String> symbolRewardIds, Set<Integer> openedIndices, long createdAt) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.type = type;
        this.reward = reward;
        this.symbolRewardIds = symbolRewardIds;
        this.openedIndices = new LinkedHashSet<>(openedIndices);
        this.createdAt = createdAt;
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public ScratchcardType type() {
        return type;
    }

    public Reward reward() {
        return reward;
    }

    public List<String> symbolRewardIds() {
        return symbolRewardIds;
    }

    public Set<Integer> openedIndices() {
        return openedIndices;
    }

    public long createdAt() {
        return createdAt;
    }

    public Inventory inventory() {
        return inventory;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public BukkitTask loadingTask() {
        return loadingTask;
    }

    public void loadingTask(BukkitTask loadingTask) {
        this.loadingTask = loadingTask;
    }

    public boolean paidOut() {
        return paidOut;
    }

    public void paidOut(boolean paidOut) {
        this.paidOut = paidOut;
    }

    public boolean loading() {
        return loading;
    }

    public void loading(boolean loading) {
        this.loading = loading;
    }

    public PendingScratchcard toPending() {
        return new PendingScratchcard(playerId, playerName, type.id(), reward.id(), symbolRewardIds, openedIndices, createdAt);
    }
}
