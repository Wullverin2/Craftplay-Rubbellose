package de.craftplay.scratchcards.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PendingScratchcard {
    private final UUID playerId;
    private final String playerName;
    private final String typeId;
    private final String rewardId;
    private final List<String> symbolRewardIds;
    private final Set<Integer> openedIndices;
    private final long createdAt;

    public PendingScratchcard(UUID playerId, String playerName, String typeId, String rewardId,
                              List<String> symbolRewardIds, Set<Integer> openedIndices, long createdAt) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.typeId = typeId;
        this.rewardId = rewardId;
        this.symbolRewardIds = new ArrayList<>(symbolRewardIds);
        this.openedIndices = new LinkedHashSet<>(openedIndices);
        this.createdAt = createdAt;
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public String typeId() {
        return typeId;
    }

    public String rewardId() {
        return rewardId;
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
}
