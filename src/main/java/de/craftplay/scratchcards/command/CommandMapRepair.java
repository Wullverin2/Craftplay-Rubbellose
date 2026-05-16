package de.craftplay.scratchcards.command;

import de.craftplay.scratchcards.diagnostic.DiagnosticLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CommandMapRepair {
    private final Plugin plugin;
    private final DiagnosticLogger diagnosticLogger;

    public CommandMapRepair(Plugin plugin, DiagnosticLogger diagnosticLogger) {
        this.plugin = plugin;
        this.diagnosticLogger = diagnosticLogger;
    }

    public void registerOrRepair(String name, CommandExecutor executor, TabCompleter tabCompleter) {
        try {
            CommandMap commandMap = commandMap();
            Map<String, Command> knownCommands = knownCommands(commandMap);
            String lowerName = name.toLowerCase(Locale.ROOT);

            removeConflictingMappings(commandMap, knownCommands, lowerName);

            PluginCommand pluginCommand = Bukkit.getPluginCommand(lowerName);
            if (pluginCommand != null && pluginCommand.getPlugin() == plugin) {
                pluginCommand.setExecutor(executor);
                pluginCommand.setTabCompleter(tabCompleter);
                diagnosticLogger.info("Command /" + lowerName + " ist regulär registriert.");
                return;
            }

            DynamicScratchcardCommand dynamicCommand = new DynamicScratchcardCommand(lowerName, plugin, executor, tabCompleter);
            commandMap.register(plugin.getName().toLowerCase(Locale.ROOT), dynamicCommand);
            syncCommands();
            diagnosticLogger.info("Command /" + lowerName + " wurde dynamisch registriert.");
        } catch (Throwable throwable) {
            diagnosticLogger.error("Command /" + name + " konnte nicht repariert/registriert werden.", throwable);
            plugin.getLogger().warning("Command /" + name + " konnte nicht repariert/registriert werden: " + throwable.getMessage());
        }
    }

    public void unregisterLegacy(String name) {
        try {
            CommandMap commandMap = commandMap();
            Map<String, Command> knownCommands = knownCommands(commandMap);
            String lowerName = name.toLowerCase(Locale.ROOT);
            Set<String> keysToRemove = new HashSet<>();
            for (Map.Entry<String, Command> entry : knownCommands.entrySet()) {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                if (!key.equals(lowerName) && !key.endsWith(":" + lowerName)) {
                    continue;
                }
                Command command = entry.getValue();
                Plugin owner = owner(command);
                if (owner != null && owner.isEnabled() && !owner.getName().equalsIgnoreCase(plugin.getName())) {
                    continue;
                }
                command.unregister(commandMap);
                keysToRemove.add(entry.getKey());
                diagnosticLogger.warning("Legacy-Command-Mapping entfernt: /" + entry.getKey() + " Besitzer=" + ownerName(owner), null);
            }
            for (String key : keysToRemove) {
                knownCommands.remove(key);
            }
            if (!keysToRemove.isEmpty()) {
                syncCommands();
            }
        } catch (Throwable throwable) {
            diagnosticLogger.warning("Legacy-Command /" + name + " konnte nicht entfernt werden.", throwable);
        }
    }

    private void removeConflictingMappings(CommandMap commandMap, Map<String, Command> knownCommands, String commandName) {
        Set<String> keysToRemove = new HashSet<>();
        for (Map.Entry<String, Command> entry : knownCommands.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (!key.equals(commandName) && !key.endsWith(":" + commandName)) {
                continue;
            }
            Command command = entry.getValue();
            Plugin owner = owner(command);
            if (owner == plugin) {
                continue;
            }
            if (owner == null || owner.getName().equalsIgnoreCase(plugin.getName()) || !owner.isEnabled() || key.equals(commandName)) {
                command.unregister(commandMap);
                keysToRemove.add(entry.getKey());
                diagnosticLogger.warning("Altes Command-Mapping entfernt: /" + entry.getKey() + " Besitzer=" + ownerName(owner), null);
            }
        }
        for (String key : keysToRemove) {
            knownCommands.remove(key);
        }
    }

    private Plugin owner(Command command) {
        if (command instanceof PluginIdentifiableCommand pluginCommand) {
            return pluginCommand.getPlugin();
        }
        return null;
    }

    private String ownerName(Plugin owner) {
        if (owner == null) {
            return "unbekannt";
        }
        return owner.getName() + " v" + owner.getDescription().getVersion() + " aktiv=" + owner.isEnabled();
    }

    private CommandMap commandMap() throws ReflectiveOperationException {
        try {
            Method method = Bukkit.getServer().getClass().getMethod("getCommandMap");
            return (CommandMap) method.invoke(Bukkit.getServer());
        } catch (NoSuchMethodException ignored) {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            return (CommandMap) field.get(Bukkit.getServer());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> knownCommands(CommandMap commandMap) throws ReflectiveOperationException {
        Field field = findField(commandMap.getClass(), "knownCommands");
        field.setAccessible(true);
        return (Map<String, Command>) field.get(commandMap);
    }

    private Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private void syncCommands() {
        try {
            Method method = Bukkit.getServer().getClass().getMethod("syncCommands");
            method.invoke(Bukkit.getServer());
            diagnosticLogger.info("Server-Commands wurden nach CommandMap-Reparatur synchronisiert.");
        } catch (Throwable throwable) {
            diagnosticLogger.warning("Server-Commands konnten nicht automatisch synchronisiert werden. Ein kompletter Neustart synchronisiert sie sicher.", throwable);
        }
    }
}
