package de.craftplay.scratchcards.command;

import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.config.LanguageManager;
import de.craftplay.scratchcards.database.DatabaseManager;
import de.craftplay.scratchcards.diagnostic.DiagnosticLogger;
import de.craftplay.scratchcards.economy.EconomyManager;
import de.craftplay.scratchcards.gui.GuiManager;
import de.craftplay.scratchcards.service.FeatureService;
import de.craftplay.scratchcards.service.PurchaseService;
import de.craftplay.scratchcards.service.RewardManager;
import de.craftplay.scratchcards.service.ScratchcardItemFactory;
import de.craftplay.scratchcards.service.ScratchcardSessionManager;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScratchcardCommandTest {
    private final ConfigManager configs = mock(ConfigManager.class);
    private final LanguageManager language = mock(LanguageManager.class);
    private final DatabaseManager database = mock(DatabaseManager.class);
    private final EconomyManager economy = mock(EconomyManager.class);
    private final DiagnosticLogger diagnostics = mock(DiagnosticLogger.class);
    private final GuiManager gui = mock(GuiManager.class);
    private final FeatureService features = mock(FeatureService.class);
    private final PurchaseService purchases = mock(PurchaseService.class);
    private final ScratchcardSessionManager sessions = mock(ScratchcardSessionManager.class);
    private final RewardManager rewards = mock(RewardManager.class);
    private final ScratchcardItemFactory items = mock(ScratchcardItemFactory.class);
    private final Player player = mock(Player.class);
    private final ScratchcardCommand command = new ScratchcardCommand(() -> {}, "0.3.6", configs, language,
            diagnostics, economy, rewards, purchases, sessions, gui, database, features, items);

    @ParameterizedTest
    @ValueSource(strings = {"risk", "pass", "quests", "series"})
    void retiredCommandsAreRejectedWithoutSideEffects(String name) {
        assertTrue(command.execute(player, "rubbellos", "rubbellos", new String[]{name}));
        verify(language).send(player, "unknown_command");
        verifyNoMoreInteractions(language);
        verifyNoInteractions(configs, database, economy, diagnostics, gui, features, purchases, sessions, rewards, items);
    }

    @Test
    void tabCompletionOnlyOffersRemainingCommands() {
        List<String> options = command.tabComplete(player, "rubbellos", new String[]{""});
        assertTrue(options.containsAll(List.of("shop", "buy", "claim", "give", "board", "history", "stats")));
        assertTrue(options.stream().noneMatch(List.of("risk", "pass", "quests", "series")::contains));
    }

    @Test
    void shopStillOpensWithTheMainCommand() {
        when(player.hasPermission("craftplay.scratchcards.shop")).thenReturn(true);
        assertTrue(command.execute(player, "rubbellos", "rubbellos", new String[]{}));
        verify(gui).openShop(player);
        verify(language).send(player, "shop_opened");
    }
}
