package de.craftplay.scratchcards.diagnostic;

import de.craftplay.scratchcards.config.ConfigManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DiagnosticLogger {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    public DiagnosticLogger(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void info(String message) {
        if (!isEnabled() || !configManager.config().getBoolean("debug.write_info_messages", true)) {
            return;
        }
        write("INFO", message, null);
    }

    public void warning(String message, Throwable throwable) {
        if (!isEnabled()) {
            return;
        }
        write("WARN", message, throwable);
    }

    public void error(String message, Throwable throwable) {
        if (!isEnabled()) {
            return;
        }
        write("ERROR", message, throwable);
    }

    public boolean isEnabled() {
        return configManager != null
                && configManager.config() != null
                && configManager.config().getBoolean("debug.enabled", false);
    }

    public Path debugFile() {
        String fileName = configManager.config().getString("debug.file", "debug-errors.txt");
        return plugin.getDataFolder().toPath().resolve(fileName).normalize();
    }

    private synchronized void write(String level, String message, Throwable throwable) {
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            StringBuilder builder = new StringBuilder();
            builder.append("[").append(LocalDateTime.now().format(FORMATTER)).append("] ");
            builder.append("[").append(level).append("] ");
            builder.append("[").append(Thread.currentThread().getName()).append("] ");
            builder.append(message).append(System.lineSeparator());
            if (throwable != null) {
                builder.append(stackTrace(throwable)).append(System.lineSeparator());
            }
            builder.append(System.lineSeparator());
            Files.writeString(debugFile(), builder.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            plugin.getLogger().warning("Debug-Datei konnte nicht geschrieben werden: " + exception.getMessage());
        }
    }

    private String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
