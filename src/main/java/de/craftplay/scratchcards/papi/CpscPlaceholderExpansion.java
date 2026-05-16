package de.craftplay.scratchcards.papi;

import de.craftplay.scratchcards.CraftplayScratchcardsPlugin;
import de.craftplay.scratchcards.database.DatabaseManager;
import de.craftplay.scratchcards.model.PlayerStats;
import de.craftplay.scratchcards.util.TextUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CpscPlaceholderExpansion extends PlaceholderExpansion {
    private final CraftplayScratchcardsPlugin plugin;
    private final DatabaseManager databaseManager;

    public CpscPlaceholderExpansion(CraftplayScratchcardsPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "cpsc";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Wullverin";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        PlayerStats stats = databaseManager.getPlayerStats(player.getUniqueId(), player.getName());
        return switch (params.toLowerCase()) {
            case "opened" -> String.valueOf(stats.opened());
            case "bought" -> String.valueOf(stats.bought());
            case "won_money" -> TextUtil.money(stats.wonMoney());
            case "jackpots" -> String.valueOf(stats.jackpots());
            case "best_win" -> TextUtil.money(stats.bestWin());
            default -> null;
        };
    }
}
