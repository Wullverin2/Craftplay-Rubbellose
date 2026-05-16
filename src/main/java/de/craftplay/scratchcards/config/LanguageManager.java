package de.craftplay.scratchcards.config;

import de.craftplay.scratchcards.util.TextUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LanguageManager {
    private final ConfigManager configManager;

    public LanguageManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public String message(String key, Map<String, String> placeholders) {
        FileConfiguration language = configManager.language();
        String prefix = language.getString("prefix", "");
        String message = language.getString(key, key);
        Map<String, String> merged = new java.util.LinkedHashMap<>(placeholders);
        merged.put("%prefix%", prefix);
        return TextUtil.replace(message, merged);
    }

    public List<String> list(String key, Map<String, String> placeholders) {
        FileConfiguration language = configManager.language();
        List<String> lines = new ArrayList<>(language.getStringList(key));
        String prefix = language.getString("prefix", "");
        Map<String, String> merged = new java.util.LinkedHashMap<>(placeholders);
        merged.put("%prefix%", prefix);
        return TextUtil.replace(lines, merged);
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        TextUtil.send(sender, message(key, placeholders));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void sendList(CommandSender sender, String key, Map<String, String> placeholders) {
        for (String line : list(key, placeholders)) {
            TextUtil.send(sender, line);
        }
    }
}
