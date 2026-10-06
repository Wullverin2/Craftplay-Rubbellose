package de.craftplay.scratchcards.service;

import de.craftplay.scratchcards.CraftplayScratchcardsPlugin;
import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.config.LanguageManager;
import de.craftplay.scratchcards.database.DatabaseManager;
import de.craftplay.scratchcards.diagnostic.DiagnosticLogger;
import de.craftplay.scratchcards.economy.EconomyManager;
import de.craftplay.scratchcards.gui.GuiManager;
import de.craftplay.scratchcards.model.Reward;
import de.craftplay.scratchcards.model.ScratchcardType;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScratchcardSessionManagerTest {
    @Test
    void legacyDailyOpeningLimitIsIgnoredButSecondRunningTicketIsBlocked() {
        ConfigManager configs = mock(ConfigManager.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("limits.enabled", true);
        config.set("limits.max_opens_per_day", 1);
        config.set("cooldown.open_seconds", 0);
        config.set("scratchcard.loading.enabled", false);
        when(configs.config()).thenReturn(config);
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Player");
        when(player.hasPermission("craftplay.scratchcards.use")).thenReturn(true);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        ItemStack item = mock(ItemStack.class);
        when(item.getAmount()).thenReturn(2);
        when(inventory.getItemInMainHand()).thenReturn(item);
        ScratchcardItemFactory items = mock(ScratchcardItemFactory.class);
        when(items.readType(item)).thenReturn(Optional.of("small"));
        Reward reward = mock(Reward.class);
        when(reward.id()).thenReturn("nothing");
        ScratchcardType type = new ScratchcardType("small", "Small", Material.PAPER, Material.PAPER,
                500, true, 0, 0, List.of(reward));
        RewardManager rewards = mock(RewardManager.class);
        when(rewards.type("small")).thenReturn(Optional.of(type));
        when(rewards.chooseReward(eq(type), anyDouble())).thenReturn(reward);
        when(rewards.createSymbols(type, reward, 9, 3)).thenReturn(Collections.nCopies(9, "nothing"));
        FeatureService features = mock(FeatureService.class);
        when(features.isTypeAvailable(type)).thenReturn(true);
        ProgressionService progression = mock(ProgressionService.class);
        when(progression.pityThreshold()).thenReturn(10);
        GuiManager gui = mock(GuiManager.class);
        when(gui.scratchSlots()).thenReturn(List.of(10, 11, 12, 19, 20, 21, 28, 29, 30));
        DatabaseManager database = mock(DatabaseManager.class);
        LanguageManager language = mock(LanguageManager.class);
        ScratchcardSessionManager sessions = new ScratchcardSessionManager(mock(CraftplayScratchcardsPlugin.class), configs,
                language, database, mock(EconomyManager.class), rewards, items, gui, mock(DiagnosticLogger.class),
                mock(FeedbackService.class), features, progression);

        sessions.startFromHand(player, EquipmentSlot.HAND);
        sessions.startFromHand(player, EquipmentSlot.HAND);

        verify(item).setAmount(1);
        verify(database).savePending(any());
        verify(database).recordOpen(uuid, "Player", "small");
        verify(database, never()).countOpensSince(any(), anyLong());
        verify(gui).openScratchcard(eq(player), any(ScratchcardSession.class));
        verify(language).send(player, "already_running");
    }
}
