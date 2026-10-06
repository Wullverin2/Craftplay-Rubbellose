package de.craftplay.scratchcards.service;

import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.config.LanguageManager;
import de.craftplay.scratchcards.database.DatabaseManager;
import de.craftplay.scratchcards.economy.EconomyManager;
import de.craftplay.scratchcards.model.GroupGoalProgress;
import de.craftplay.scratchcards.model.PassProgress;
import de.craftplay.scratchcards.model.QuestProgress;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProgressionServiceTest {
    @Test
    void multiplePurchasesAwardXpAndQuestAndGroupProgressForEveryTicket() {
        ConfigManager configs = mock(ConfigManager.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("pass.season_id", "test");
        config.set("pass.xp.buy", 10);
        config.set("quests.daily.buy5.event", "buy");
        config.set("quests.daily.buy5.target", 20);
        config.set("group_goals.goals.buy100.event", "buy");
        config.set("group_goals.goals.buy100.target", 100);
        when(configs.config()).thenReturn(config);
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Player");
        DatabaseManager database = mock(DatabaseManager.class);
        when(database.addPassXp(uuid, "Player", "test", 50)).thenReturn(new PassProgress("test", 50, 0));
        when(database.getQuestProgress(eq(uuid), eq("Player"), eq("buy5"), eq("buy5"), anyLong(), eq(20)))
                .thenReturn(new QuestProgress("buy5", "buy5", 0, 20, false));
        when(database.addQuestProgress(eq(uuid), eq("Player"), eq("buy5"), eq("buy5"), anyLong(), eq(20), eq(5)))
                .thenReturn(new QuestProgress("buy5", "buy5", 5, 20, false));
        when(database.getGroupGoalProgress(eq("buy100"), eq("buy100"), anyLong(), eq(100L)))
                .thenReturn(new GroupGoalProgress("buy100", "buy100", 0, 100, false));
        when(database.addGroupGoalProgress(eq("buy100"), eq("buy100"), anyLong(), eq(100L), eq(5L)))
                .thenReturn(new GroupGoalProgress("buy100", "buy100", 5, 100, false));
        ProgressionService progression = new ProgressionService(configs,
                mock(LanguageManager.class), database, mock(EconomyManager.class));

        progression.onBuy(player, 5);

        verify(database).addPassXp(uuid, "Player", "test", 50);
        verify(database).addQuestProgress(eq(uuid), eq("Player"), eq("buy5"), eq("buy5"), anyLong(), eq(20), eq(5));
        verify(database).addGroupGoalProgress(eq("buy100"), eq("buy100"), anyLong(), eq(100L), eq(5L));
    }
}
