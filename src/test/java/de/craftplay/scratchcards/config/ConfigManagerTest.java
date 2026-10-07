package de.craftplay.scratchcards.config;

import de.craftplay.scratchcards.CraftplayScratchcardsPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
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
    private ConfigManager configs;

    @BeforeEach
    void setUp() {
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
        configs = new ConfigManager(plugin);
    }

    @Test
    void migratesLegacyOpeningLimitAndAddsBatchPurchaseOptionsWithoutOverwritingCustomValues() throws Exception {
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

        configs.load();

        assertFalse(configs.config().contains("limits.max_opens_per_day"));
        assertEquals(37, configs.config().getInt("limits.max_purchases_per_day"));
        assertFalse(configs.config().contains("limits.max_owned_scratchcards"));
        assertEquals(32, configs.config().getInt("purchases.max_amount_per_purchase"));
        assertEquals(7, configs.config().getInt("cooldown.open_seconds"));
        assertEquals("My custom shop", configs.gui().getString("shop.title"));
        assertEquals(3, configs.gui().getInt("shop.card_slots.small"));
        assertEquals(List.of(1, 5, 10), configs.gui().getIntegerList("shop.quantity_selector.amounts"));
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

    @Test
    void removesOldLargeAmountButtonsAndKeepsCustomSlotsAndTenTicketLimit() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("limits.max_purchases_per_day", 10);
        config.save(directory.resolve("config.yml").toFile());
        YamlConfiguration gui = new YamlConfiguration();
        gui.set("shop.quantity_selector.amounts", List.of(1, 5, 10, 25, 64));
        gui.set("shop.quantity_selector.slots", List.of(0, 1, 2, 3, 4));
        gui.save(directory.resolve("gui.yml").toFile());

        configs.load();

        assertEquals(List.of(1, 5, 10), configs.gui().getIntegerList("shop.quantity_selector.amounts"));
        assertEquals(List.of(0, 1, 2), configs.gui().getIntegerList("shop.quantity_selector.slots"));
        assertEquals(10, configs.config().getInt("limits.max_purchases_per_day"));
        String afterFirstLoad = Files.readString(directory.resolve("gui.yml"));
        configs.load();
        assertEquals(afterFirstLoad, Files.readString(directory.resolve("gui.yml")));
    }

    @Test
    void retainsIndividuallyConfiguredAmountButtons() throws Exception {
        YamlConfiguration gui = new YamlConfiguration();
        gui.set("shop.quantity_selector.amounts", List.of(2, 7, 10));
        gui.set("shop.quantity_selector.slots", List.of(3, 4, 5));
        gui.save(directory.resolve("gui.yml").toFile());

        configs.load();

        assertEquals(List.of(2, 7, 10), configs.gui().getIntegerList("shop.quantity_selector.amounts"));
        assertEquals(List.of(3, 4, 5), configs.gui().getIntegerList("shop.quantity_selector.slots"));
    }

    @Test
    void correctsGermanStandardTextsWithoutChangingCustomTextOrDuplicatingLoreAndHelp() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("rarities.common.display_name", "&7Gewoehnlich");
        config.set("rarities.uncommon.display_name", "&aMeine eigenen seltenen Preise");
        config.save(directory.resolve("config.yml").toFile());
        YamlConfiguration gui = new YamlConfiguration();
        gui.set("shop.quantity_selector.selected_item.lore", List.of("&aAusgewaehlt"));
        gui.set("shop.info.lore", List.of("&7Kaeufe heute: &e%daily_bought%/%daily_limit%",
                "&7Geoeffnet heute: &e%daily_opened%/%daily_open_limit%", "Meine eigene Anzeige"));
        gui.save(directory.resolve("gui.yml").toFile());
        YamlConfiguration german = new YamlConfiguration();
        german.set("purchase_inventory_full", "%prefix%&cDein Inventar hat nicht genug Platz fuer &e%amount% &cLose.");
        german.set("daily_disabled", "Mein eigener Text fuer Spieler");
        german.set("help", List.of("&6/rubbellos daily &7- Taegliches Gratis-Los abholen"));
        german.save(directory.resolve("language_de.yml").toFile());

        configs.load();

        assertEquals("&7Gewöhnlich", configs.config().getString("rarities.common.display_name"));
        assertEquals("&aMeine eigenen seltenen Preise", configs.config().getString("rarities.uncommon.display_name"));
        assertEquals(List.of("&aAusgewählt"), configs.gui().getStringList("shop.quantity_selector.selected_item.lore"));
        List<String> lore = configs.gui().getStringList("shop.info.lore");
        assertTrue(lore.contains("Meine eigene Anzeige"));
        assertEquals(1, lore.stream().filter(line -> line.contains("Käufe heute:")).count());
        assertEquals(1, lore.stream().filter(line -> line.contains("Geöffnet heute:")).count());
        assertTrue(lore.stream().noneMatch(line -> line.contains("Kaeufe") || line.contains("Geoeffnet")));
        assertEquals("%prefix%&cDein Inventar hat nicht genug Platz für &e%amount% &cLose.",
                configs.language().getString("purchase_inventory_full"));
        assertEquals("Mein eigener Text fuer Spieler", configs.language().getString("daily_disabled"));
        assertEquals(1, configs.language().getStringList("help").stream().filter(line -> line.contains("/rubbellos daily")).count());
    }

    @Test
    void removesRiskPassAndAllCollectionFeaturesWhilePreservingPurchaseSettingsAndRewards() throws Exception {
        List<String> retired = List.of("risk", "pass", "quests", "series", "streak", "pity", "group_goals");
        YamlConfiguration config = new YamlConfiguration();
        for (String root : retired) {
            config.set(root + ".enabled", true);
            config.set(root + ".custom_value", 987);
        }
        config.set("limits.max_owned_scratchcards", 1);
        config.set("daily.respect_owned_limit", true);
        config.set("limits.max_purchases_per_day", 10);
        config.set("database.sqlite.file", "custom.db");
        config.save(directory.resolve("config.yml").toFile());
        YamlConfiguration gui = new YamlConfiguration();
        for (String item : List.of("pass", "quests", "group_goals")) {
            gui.set("board.items." + item + ".name", "My legacy widget");
        }
        gui.set("board.items.jackpots.slot", 7);
        gui.set("shop.info.lore", List.of("Meine Anzeige", "Im Besitz: %owned%/%owned_limit%"));
        gui.save(directory.resolve("gui.yml").toFile());
        YamlConfiguration rewards = new YamlConfiguration();
        rewards.set("scratchcards.small.price", 123);
        rewards.set("scratchcards.small.rewards.nothing.chance", 81.5);
        rewards.save(directory.resolve("rewards.yml").toFile());
        for (String name : List.of("language_de.yml", "language_en.yml")) {
            YamlConfiguration language = new YamlConfiguration();
            for (String key : List.of("risk_available", "pass_xp", "pass_info", "quest_completed", "streak_reward", "series_completed")) {
                language.set(key, "Obsolete custom message");
            }
            language.set("owned_limit", "Obsolete quota");
            language.set("reward_win", "Mein eigener Gewinntext");
            language.set("help", List.of("Custom help", "&6/rubbellos pass &7- Pass",
                    "&6/rubellos risk &7- Risiko", "&6/scratchcard quests &7- Quests",
                    "&6/rubbellos series &7- Serie", "&6/rubbellos board &7- Jackpot-/Pass-/Ziel-Board öffnen"));
            language.save(directory.resolve(name).toFile());
        }

        configs.load();

        for (String root : retired) {
            assertFalse(configs.config().contains(root), root);
        }
        assertFalse(configs.config().contains("limits.max_owned_scratchcards"));
        assertFalse(configs.config().contains("daily.respect_owned_limit"));
        assertEquals(10, configs.config().getInt("limits.max_purchases_per_day"));
        assertEquals("custom.db", configs.config().getString("database.sqlite.file"));
        assertEquals(123, configs.rewards().getInt("scratchcards.small.price"));
        assertEquals(81.5, configs.rewards().getDouble("scratchcards.small.rewards.nothing.chance"));
        for (String item : List.of("pass", "quests", "group_goals")) {
            assertFalse(configs.gui().contains("board.items." + item));
        }
        assertEquals(7, configs.gui().getInt("board.items.jackpots.slot"));
        assertTrue(configs.gui().getStringList("shop.info.lore").contains("Im Besitz: %owned%"));
        assertFalse(configs.gui().saveToString().contains("%owned_limit%"));
        for (String name : List.of("language_de.yml", "language_en.yml")) {
            YamlConfiguration language = YamlConfiguration.loadConfiguration(directory.resolve(name).toFile());
            assertEquals("Mein eigener Gewinntext", language.getString("reward_win"));
            assertFalse(language.getKeys(false).stream().anyMatch(key ->
                    key.matches("(?:risk|pass|quests?|series|streak|pity|group_goal)_.*") || key.equals("owned_limit")));
            List<String> help = language.getStringList("help");
            assertTrue(help.contains("Custom help"));
            assertFalse(help.stream().anyMatch(line -> line.matches(".*?/(?:rubbellos|rubellos|scratchcard) (?:risk|pass|quests|series) .*")));
            assertFalse(help.stream().anyMatch(line -> line.contains("Pass-/Ziel")));
        }
        for (String name : List.of("config.yml", "gui.yml", "language_de.yml", "language_en.yml")) {
            String first = Files.readString(directory.resolve(name));
            configs.load();
            assertEquals(first, Files.readString(directory.resolve(name)), name);
        }
    }

    @Test
    void removesServerGoalAndItsMessagesFromExistingFilesWithoutChangingOtherSettings() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("server_goal.enabled", true);
        config.set("server_goal.id", "custom_goal");
        config.set("server_goal.target_opens", 1);
        config.set("server_goal.reward_money_online", 2500);
        config.set("server_goal.commands", List.of("broadcast Old server goal"));
        config.set("limits.max_purchases_per_day", 10);
        config.set("group_goals.goals.open_100.target", 750);
        config.save(directory.resolve("config.yml").toFile());
        for (String name : List.of("language_de.yml", "language_en.yml")) {
            YamlConfiguration language = new YamlConfiguration();
            language.set("server_goal_completed", "Old online bonus message");
            language.set("reward_win", "My custom prize message");
            language.save(directory.resolve(name).toFile());
        }

        configs.load();

        assertFalse(configs.config().contains("server_goal"));
        assertEquals(10, configs.config().getInt("limits.max_purchases_per_day"));
        assertFalse(configs.config().contains("group_goals"));
        for (String name : List.of("language_de.yml", "language_en.yml")) {
            YamlConfiguration language = YamlConfiguration.loadConfiguration(directory.resolve(name).toFile());
            assertFalse(language.contains("server_goal_completed"));
            assertEquals("My custom prize message", language.getString("reward_win"));
        }
        String afterFirstLoad = Files.readString(directory.resolve("config.yml"));
        configs.load();
        assertEquals(afterFirstLoad, Files.readString(directory.resolve("config.yml")));
        assertFalse(configs.config().contains("server_goal"));
    }
}
