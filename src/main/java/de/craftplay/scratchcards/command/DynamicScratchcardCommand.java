package de.craftplay.scratchcards.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class DynamicScratchcardCommand extends Command implements PluginIdentifiableCommand {
    private final Plugin plugin;
    private final CommandExecutor executor;
    private final TabCompleter tabCompleter;

    public DynamicScratchcardCommand(String name, Plugin plugin, CommandExecutor executor, TabCompleter tabCompleter) {
        super(name);
        this.plugin = plugin;
        this.executor = executor;
        this.tabCompleter = tabCompleter;
        setDescription("Craftplay-Rubbellose command");
        setUsage("/" + name);
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!plugin.isEnabled()) {
            sender.sendMessage("Craftplay-Rubbellose ist aktuell nicht aktiviert.");
            return true;
        }
        return executor.onCommand(sender, this, commandLabel, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return tabCompleter.onTabComplete(sender, this, alias, args);
    }

    @Override
    public Plugin getPlugin() {
        return plugin;
    }
}
