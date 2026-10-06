package de.craftplay.scratchcards.config;

import de.craftplay.scratchcards.CraftplayScratchcardsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public final class ConfigManager {
    private final CraftplayScratchcardsPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration gui;
    private FileConfiguration rewards;
    private FileConfiguration language;

    public ConfigManager(CraftplayScratchcardsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        saveDefault("config.yml");
        saveDefault("gui.yml");
        saveDefault("rewards.yml");
        saveDefault("language_de.yml");
        saveDefault("language_en.yml");
        syncExistingFiles();

        plugin.reloadConfig();
        config = plugin.getConfig();
        gui = loadFile("gui.yml");
        rewards = loadFile("rewards.yml");

        String languageId = config.getString("language.default", "de").toLowerCase();
        File languageFile = new File(plugin.getDataFolder(), "language_" + languageId + ".yml");
        if (!languageFile.exists()) {
            languageFile = new File(plugin.getDataFolder(), "language_de.yml");
        }
        language = YamlConfiguration.loadConfiguration(languageFile);
    }

    private void saveDefault(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
    }

    private void syncExistingFiles() {
        syncFile("config.yml", List.of("limits.max_purchases_per_hour", "limits.max_opens_per_day", "bedrock_support"));
        syncFile("gui.yml", List.of());
        syncFile("rewards.yml", List.of());
        syncFile("language_de.yml", List.of("bedrock_shop_opener_given", "open_limit_day"));
        syncFile("language_en.yml", List.of("bedrock_shop_opener_given", "open_limit_day"));
    }

    private void syncFile(String name, List<String> obsoletePaths) {
        File file = new File(plugin.getDataFolder(), name);
        try (InputStreamReader reader = new InputStreamReader(plugin.getResource(name), StandardCharsets.UTF_8)) {
            YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            boolean changed = addMissingValues(current, defaults);
            if (name.equals("config.yml")) {
                changed = migrateGermanDefaultTexts(current, defaults) || changed;
            }
            if (name.equals("gui.yml") || name.equals("language_de.yml")) {
                changed = replaceTextValue(current, "/rubellos", "/rubbellos") || changed;
            }
            if (name.equals("gui.yml")) {
                changed = migrateShopAmounts(current) || changed;
                changed = replaceTextValue(current, "%daily_opened%/%daily_open_limit%", "%daily_opened%") || changed;
                changed = replaceTextValue(current, "%daily_open_limit%", "-") || changed;
                changed = replaceTextValue(current, "%daily_open_remaining%", "-") || changed;
                changed = replaceTextValue(current, "&7Preis: &e%price%", "&7Preis pro Los: &e%price%") || changed;
                changed = replaceTextValue(current, "&aKlicke zum Kaufen", "&a%amount%x kaufen: &e%total_price%") || changed;
                changed = replaceExactValue(current, "shop.card_item.reward_chance_line",
                        "&8- &f%reward%&7: &e%chance%%",
                        "&8- &f%reward% &7[%rarity%&7]: &e%chance%%") || changed;
                changed = migrateGermanDefaultTexts(current, defaults) || changed;
                changed = mergeMissingListEntries(current, defaults, "shop.card_item.lore") || changed;
                changed = mergeMissingListEntries(current, defaults, "shop.info.lore") || changed;
            }
            if (name.equals("language_de.yml") || name.equals("language_en.yml")) {
                // Eigene Nachrichtentexte behalten; nur die bisherigen Standardtexte aktualisieren.
                boolean german = name.equals("language_de.yml");
                changed = replaceExactValue(current, "purchase_success", german
                                ? "%prefix%&aDu hast &e%type% &afür &e%price% &agekauft. &7Heute: &e%daily_bought%/%daily_limit% &7gekauft, &e%daily_remaining% &7übrig."
                                : "%prefix%&aYou bought &e%type% &afor &e%price%&a. &7Today: &e%daily_bought%/%daily_limit% &7bought, &e%daily_remaining% &7left.",
                        defaults.getString("purchase_success")) || changed;
                changed = replaceExactValue(current, "purchase_limit_day", german
                                ? "%prefix%&cDu hast dein Tageslimit erreicht. &7Heute: &e%daily_bought%/%daily_limit%&7. Es wird um Mitternacht zurückgesetzt."
                                : "%prefix%&cYou reached your daily purchase limit. &7Today: &e%daily_bought%/%daily_limit%&7. It resets at midnight.",
                        defaults.getString("purchase_limit_day")) || changed;
                if (german) {
                    changed = migrateGermanDefaultTexts(current, defaults) || changed;
                }
                changed = mergeMissingListEntries(current, defaults, "help") || changed;
            }
            for (String path : obsoletePaths) {
                if (current.contains(path)) {
                    current.set(path, null);
                    changed = true;
                }
            }
            if (changed) {
                current.save(file);
                plugin.getLogger().info(name + " wurde mit neuen Standard-Eintraegen synchronisiert.");
            }
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, name + " konnte nicht automatisch synchronisiert werden.", exception);
        }
    }

    private boolean addMissingValues(ConfigurationSection current, ConfigurationSection defaults) {
        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            Object defaultValue = defaults.get(key);
            ConfigurationSection defaultSection = defaults.getConfigurationSection(key);
            if (defaultSection != null) {
                ConfigurationSection currentSection = current.getConfigurationSection(key);
                if (currentSection == null) {
                    currentSection = current.createSection(key);
                    changed = true;
                }
                changed = addMissingValues(currentSection, defaultSection) || changed;
                continue;
            }
            if (!current.contains(key)) {
                current.set(key, defaultValue);
                changed = true;
            }
        }
        return changed;
    }

    private boolean replaceExactValue(YamlConfiguration current, String path, String oldValue, String newValue) {
        String value = current.getString(path);
        if (!oldValue.equals(value)) {
            return false;
        }
        current.set(path, newValue);
        return true;
    }

    private boolean migrateShopAmounts(YamlConfiguration current) {
        String path = "shop.quantity_selector";
        List<Integer> amounts = current.getIntegerList(path + ".amounts");
        if (!amounts.equals(List.of(1, 5, 10, 25, 64))) {
            return false;
        }
        List<Integer> slots = current.getIntegerList(path + ".slots");
        current.set(path + ".amounts", List.of(1, 5, 10));
        // Die ersten drei Mengen behalten ihre bisherigen, eventuell angepassten Slots.
        current.set(path + ".slots", new ArrayList<>(slots.subList(0, Math.min(3, slots.size()))));
        return true;
    }

    private boolean migrateGermanDefaultTexts(YamlConfiguration current, YamlConfiguration defaults) {
        boolean changed = false;
        // Nur bekannte Standardtexte korrigieren, keine eigenen Texte oder technischen Schluessel.
        for (String path : defaults.getKeys(true)) {
            Object defaultValue = defaults.get(path);
            if (defaultValue instanceof String text) {
                String legacyText = withoutUmlauts(text);
                if (!legacyText.equals(text)) {
                    changed = replaceExactValue(current, path, legacyText, text) || changed;
                }
            } else if (defaultValue instanceof List<?>) {
                List<String> texts = new ArrayList<>(current.getStringList(path));
                boolean listChanged = false;
                for (String text : defaults.getStringList(path)) {
                    String legacyText = withoutUmlauts(text);
                    if (legacyText.equals(text)) {
                        continue;
                    }
                    for (int index = 0; index < texts.size(); index++) {
                        if (texts.get(index).equals(legacyText)) {
                            texts.set(index, text);
                            listChanged = true;
                        }
                    }
                }
                if (listChanged) {
                    current.set(path, texts);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private String withoutUmlauts(String text) {
        return text.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
                .replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue").replace("ß", "ss");
    }

    private boolean mergeMissingListEntries(YamlConfiguration current, YamlConfiguration defaults, String path) {
        List<String> defaultList = defaults.getStringList(path);
        if (defaultList.isEmpty()) {
            return false;
        }
        List<String> currentList = new ArrayList<>(current.getStringList(path));
        boolean changed = false;
        for (String entry : defaultList) {
            if (!currentList.contains(entry)) {
                currentList.add(entry);
                changed = true;
            }
        }
        if (changed) {
            current.set(path, currentList);
        }
        return changed;
    }

    private boolean replaceTextValue(ConfigurationSection section, String oldText, String newText) {
        boolean changed = false;
        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child != null) {
                changed = replaceTextValue(child, oldText, newText) || changed;
                continue;
            }
            Object value = section.get(key);
            if (value instanceof String text && text.contains(oldText)) {
                section.set(key, text.replace(oldText, newText));
                changed = true;
                continue;
            }
            if (value instanceof List<?> list) {
                List<Object> migrated = new ArrayList<>(list.size());
                boolean listChanged = false;
                for (Object entry : list) {
                    if (entry instanceof String text && text.contains(oldText)) {
                        migrated.add(text.replace(oldText, newText));
                        listChanged = true;
                    } else {
                        migrated.add(entry);
                    }
                }
                if (listChanged) {
                    section.set(key, migrated);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private FileConfiguration loadFile(String name) {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), name));
    }

    public FileConfiguration config() {
        return config;
    }

    public FileConfiguration gui() {
        return gui;
    }

    public FileConfiguration rewards() {
        return rewards;
    }

    public FileConfiguration language() {
        return language;
    }
}
