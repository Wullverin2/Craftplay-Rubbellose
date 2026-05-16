package de.craftplay.scratchcards.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TextUtil {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final DecimalFormat MONEY_FORMAT;

    static {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.GERMANY);
        MONEY_FORMAT = new DecimalFormat("#,##0.##", symbols);
    }

    private TextUtil() {
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    public static List<String> color(List<String> lines) {
        return lines.stream().map(TextUtil::color).toList();
    }

    public static Component component(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    public static String plain(String text) {
        return ChatColor.stripColor(color(text));
    }

    public static void send(CommandSender sender, String text) {
        sender.sendMessage(color(text));
    }

    public static String replace(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    public static List<String> replace(List<String> lines, Map<String, String> placeholders) {
        return lines.stream().map(line -> replace(line, placeholders)).toList();
    }

    public static Map<String, String> placeholders(String... values) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            placeholders.put(values[i], values[i + 1]);
        }
        return placeholders;
    }

    public static String money(double amount) {
        return MONEY_FORMAT.format(amount);
    }
}
