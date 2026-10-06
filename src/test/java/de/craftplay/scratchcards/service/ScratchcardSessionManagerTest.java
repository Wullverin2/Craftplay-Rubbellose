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
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
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

    @ParameterizedTest
    @ValueSource(doubles = {0, 100})
    void finishingTicketOnlyPaysItsPrizeWithoutOnlineBonusOrServerGoalBroadcast(double prizeMoney) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("scratchcard.loading.enabled", false);
        config.set("scratchcard.gui.required_opened_fields", 1);
        config.set("scratchcard.gui.auto_close_after_payout_ticks", 0);
        // Auch eine noch nicht migrierte alte Konfiguration darf keinen Bonus mehr ausloesen.
        config.set("server_goal.enabled", true);
        config.set("server_goal.target_opens", 1);
        config.set("server_goal.reward_money_online", 2500);
        config.set("server_goal.commands", List.of("broadcast Old server goal"));
        ConfigManager configs = mock(ConfigManager.class);
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
        when(reward.id()).thenReturn("prize");
        when(reward.displayName()).thenReturn("Prize");
        when(reward.money()).thenReturn(prizeMoney);
        when(reward.isWin()).thenReturn(prizeMoney > 0);
        ScratchcardType type = new ScratchcardType("small", "Small", Material.PAPER, Material.PAPER,
                500, true, 0, 0, List.of(reward));
        RewardManager rewards = mock(RewardManager.class);
        when(rewards.type("small")).thenReturn(Optional.of(type));
        when(rewards.chooseReward(eq(type), anyDouble())).thenReturn(reward);
        when(rewards.createSymbols(type, reward, 1, 3)).thenReturn(List.of("prize"));
        FeatureService features = mock(FeatureService.class);
        when(features.isTypeAvailable(type)).thenReturn(true);
        when(features.rollMysteryMultiplier(player, reward)).thenReturn(1.0);
        when(features.luckyMoneyMultiplier()).thenReturn(1.0);
        ProgressionService progression = mock(ProgressionService.class);
        when(progression.pityThreshold()).thenReturn(10);
        GuiManager gui = mock(GuiManager.class);
        when(gui.scratchSlots()).thenReturn(List.of(10));
        when(gui.rewardAt(any(), eq(0))).thenReturn(reward);
        DatabaseManager database = mock(DatabaseManager.class);
        EconomyManager economy = mock(EconomyManager.class);
        when(economy.format(anyDouble())).thenAnswer(invocation -> invocation.getArgument(0).toString());
        LanguageManager language = mock(LanguageManager.class);
        when(language.message("field_revealed", Map.of())).thenReturn("%reward%");
        ScratchcardSessionManager sessions = new ScratchcardSessionManager(mock(CraftplayScratchcardsPlugin.class), configs,
                language, database, economy, rewards, items, gui, mock(DiagnosticLogger.class),
                mock(FeedbackService.class), features, progression);

        try (var bukkit = mockStatic(Bukkit.class)) {
            sessions.startFromHand(player, EquipmentSlot.HAND);
            sessions.reveal(player, 0);
            sessions.reveal(player, 0);
            bukkit.verifyNoInteractions();
        }

        verify(economy, times(prizeMoney > 0 ? 1 : 0)).deposit(player, prizeMoney);
        verify(economy, never()).deposit(any(), eq(2500.0));
        verify(database).recordReward(uuid, "Player", "small", reward, prizeMoney, "Prize");
        verify(database).deletePending(uuid);
        verify(database, never()).countTotalOpens();
        verify(language, never()).send(any(), eq("server_goal_completed"), anyMap());
        verify(features).isTypeAvailable(type);
        verify(features).luckyWinChanceMultiplier();
        verify(features).rollMysteryMultiplier(player, reward);
        verify(features).luckyMoneyMultiplier();
        verify(features).handleSeries(player, type, reward);
        verifyNoMoreInteractions(features);
        assertFalse(sessions.hasOpenScratchcard(uuid));
    }
}
