package de.craftplay.scratchcards.config;

import de.craftplay.scratchcards.CraftplayScratchcardsPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConfigManagerTest {
    @TempDir
    Path directory;

    @Test
    void migratesLegacyOpeningLimitAndAddsBatchPurchaseOptionsWithoutOverwritingCustomValues() throws Exception {
        CraftplayScratchcardsPlugin plugin = mock(CraftplayScratchcardsPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getResource(anyString())).thenAnswer(invocation ->
                getClass().getResourceAsStream("/" + invocation.getArgument(0, String.class)));
        when(plugin.getConfig()).thenAnswer(invocation -> YamlConfiguration.loadConfiguration(directory.resolve("config.yml").toFile()));
        doAnswer(invocation -> {
            String name = invocation.getArgument(0);
            try (var resource = getClass().getResourceAsStream("/" + name)) {
                Files.copy(resource, directory.resolve(name));
            }
            return null;
        }).when(plugin).saveResource(anyString(), eq(false));
        YamlConfiguration config = new YamlConfiguration();
        config.set("limits.max_opens_per_day", 1);
        config.set("limits.max_purchases_per_day", 37);
        config.set("limits.max_owned_scratchcards", 128);
        config.set("purchases.max_amount_per_purchase", 32);
        config.set("cooldown.open_seconds", 7);
        config.save(directory.resolve("config.yml").toFile());
        YamlConfiguration gui = new YamlConfiguration();
        gui.set("shop.title", "My custom shop");
        gui.set("shop.card_slots.small", 3);
        gui.set("shop.info.lore", List.of("Custom text", "Opened: %daily_opened%/%daily_open_limit%"));
        gui.save(directory.resolve("gui.yml").toFile());
        YamlConfiguration german = new YamlConfiguration();
        german.set("purchase_success", "Custom purchase text %amount%");
        german.set("open_limit_day", "Obsolete message");
        german.save(directory.resolve("language_de.yml").toFile());
        ConfigManager configs = new ConfigManager(plugin);

        configs.load();

        assertFalse(configs.config().contains("limits.max_opens_per_day"));
        assertEquals(37, configs.config().getInt("limits.max_purchases_per_day"));
        assertEquals(128, configs.config().getInt("limits.max_owned_scratchcards"));
        assertEquals(32, configs.config().getInt("purchases.max_amount_per_purchase"));
        assertEquals(7, configs.config().getInt("cooldown.open_seconds"));
        assertEquals("My custom shop", configs.gui().getString("shop.title"));
        assertEquals(3, configs.gui().getInt("shop.card_slots.small"));
        assertEquals(List.of(1, 5, 10, 25, 64), configs.gui().getIntegerList("shop.quantity_selector.amounts"));
        assertTrue(configs.gui().getStringList("shop.info.lore").contains("Custom text"));
        assertTrue(configs.gui().getStringList("shop.info.lore").contains("Opened: %daily_opened%"));
        assertEquals("Custom purchase text %amount%", configs.language().getString("purchase_success"));
        assertFalse(configs.language().contains("open_limit_day"));
        assertTrue(configs.language().getStringList("help").stream().anyMatch(line -> line.contains("/rubbellos buy")));

        // Ein zweites Laden darf weder Texte erneut anhaengen noch Dateien wieder veraendern.
        String afterFirstLoad = Files.readString(directory.resolve("gui.yml"));
        configs.load();
        assertEquals(afterFirstLoad, Files.readString(directory.resolve("gui.yml")));
    }
}
