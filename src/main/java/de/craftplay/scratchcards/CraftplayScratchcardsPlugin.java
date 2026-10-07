package de.craftplay.scratchcards;

import de.craftplay.scratchcards.command.CommandMapRepair;
import de.craftplay.scratchcards.command.ScratchcardCommand;
import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.config.LanguageManager;
import de.craftplay.scratchcards.database.DatabaseManager;
import de.craftplay.scratchcards.diagnostic.DiagnosticLogger;
import de.craftplay.scratchcards.economy.EconomyManager;
import de.craftplay.scratchcards.gui.GuiManager;
import de.craftplay.scratchcards.listener.GuiListener;
import de.craftplay.scratchcards.listener.PlayerListener;
import de.craftplay.scratchcards.papi.CpscPlaceholderExpansion;
import de.craftplay.scratchcards.service.PurchaseService;
import de.craftplay.scratchcards.service.FeedbackService;
import de.craftplay.scratchcards.service.FeatureService;
import de.craftplay.scratchcards.service.RewardManager;
import de.craftplay.scratchcards.service.ScratchcardItemFactory;
import de.craftplay.scratchcards.service.ScratchcardSessionManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.logging.Level;

public final class CraftplayScratchcardsPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private DiagnosticLogger diagnosticLogger;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private RewardManager rewardManager;
    private ScratchcardItemFactory itemFactory;
    private GuiManager guiManager;
    private PurchaseService purchaseService;
    private FeedbackService feedbackService;
    private FeatureService featureService;
    private ScratchcardSessionManager sessionManager;
    private CpscPlaceholderExpansion placeholderExpansion;
    private BukkitTask shopRefreshTask;

    @Override
    public void onEnable() {
        try {
            configManager = new ConfigManager(this);
            configManager.load();
            languageManager = new LanguageManager(configManager);
            diagnosticLogger = new DiagnosticLogger(this, configManager);
            diagnosticLogger.info("Plugin-Start beginnt. Version: " + getDescription().getVersion());

            economyManager = new EconomyManager(this, diagnosticLogger);
            if (!economyManager.setup()) {
                getLogger().warning("Vault ist geladen, aber es wurde noch kein Economy-Provider gefunden. Das Plugin bleibt aktiv und prueft spaeter erneut.");
                diagnosticLogger.warning("Vault-Economy wurde beim Start nicht gefunden.", null);
            }

            databaseManager = new DatabaseManager(this, configManager, diagnosticLogger);
            databaseManager.initialize();

            rewardManager = new RewardManager(configManager);
            rewardManager.reload();

            itemFactory = new ScratchcardItemFactory(this, configManager);
            feedbackService = new FeedbackService(configManager);
            featureService = new FeatureService(configManager, languageManager);
            guiManager = new GuiManager(configManager, rewardManager, databaseManager, itemFactory, featureService);
            purchaseService = new PurchaseService(configManager, languageManager, databaseManager, economyManager, itemFactory, feedbackService, featureService, diagnosticLogger);
            sessionManager = new ScratchcardSessionManager(this, configManager, languageManager, databaseManager,
                    economyManager, rewardManager, itemFactory, guiManager, diagnosticLogger, feedbackService, featureService);

            registerCommands();
            registerListeners();
            registerPlaceholderApi();
            shopRefreshTask = getServer().getScheduler().runTaskTimer(this, () -> {
                try {
                    guiManager.updateOpenShops();
                } catch (Throwable throwable) {
                    diagnosticLogger.error("Fehler beim Aktualisieren des Gratislos-Countdowns.", throwable);
                }
            }, 20L, 20L);

            diagnosticLogger.info("Plugin-Start erfolgreich abgeschlossen.");
            getLogger().info("Craftplay-Rubbellose wurde aktiviert.");
        } catch (Throwable throwable) {
            if (diagnosticLogger != null) {
                diagnosticLogger.error("Plugin konnte nicht aktiviert werden.", throwable);
            }
            getLogger().log(Level.SEVERE, "Craftplay-Rubbellose konnte nicht aktiviert werden.", throwable);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            if (shopRefreshTask != null) {
                shopRefreshTask.cancel();
            }
            if (placeholderExpansion != null) {
                placeholderExpansion.unregister();
            }
            if (sessionManager != null) {
                sessionManager.shutdown();
            }
            if (databaseManager != null) {
                databaseManager.close();
            }
            if (diagnosticLogger != null) {
                diagnosticLogger.info("Plugin wurde deaktiviert.");
            }
        } catch (Throwable throwable) {
            if (diagnosticLogger != null) {
                diagnosticLogger.error("Fehler beim Deaktivieren des Plugins.", throwable);
            }
            getLogger().log(Level.SEVERE, "Fehler beim Deaktivieren des Plugins.", throwable);
        }
        getLogger().info("Craftplay-Rubbellose wurde deaktiviert.");
    }

    private void registerCommands() {
        ScratchcardCommand command = new ScratchcardCommand(this::reloadRuntimeConfiguration, getDescription().getVersion(), configManager,
                languageManager, diagnosticLogger, economyManager, rewardManager, purchaseService, sessionManager, guiManager, databaseManager, featureService, itemFactory);
        CommandMapRepair commandMapRepair = new CommandMapRepair(this, diagnosticLogger);
        commandMapRepair.unregisterLegacy("rubellos");
        registerPluginCommand("rubbellos", command);
        registerPluginCommand("scratchcard", command);
        registerPluginCommand("cpscratchdiag", command);
        registerPaperCommand("rubbellos", "Öffnet den Rubellos-Shop oder verwaltet Rubellose.", command);
        registerPaperCommand("scratchcard", "Opens the scratchcard shop or manages scratchcards.", command);
        registerPaperCommand("cpscratchdiag", "Zeigt Diagnoseinformationen zur geladenen Craftplay-Rubbellose-Version.", command);
        commandMapRepair.syncCommands();
    }

    private void registerPluginCommand(String name, ScratchcardCommand command) {
        PluginCommand pluginCommand = getCommand(name);
        if (pluginCommand == null) {
            diagnosticLogger.warning("Command /" + name + " wurde nicht in plugin.yml gefunden.", null);
            return;
        }
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
        diagnosticLogger.info("Command /" + name + " wurde ueber plugin.yml registriert.");
    }

    private void registerPaperCommand(String name, String description, ScratchcardCommand command) {
        registerCommand(name, description, List.of(), new PaperScratchcardCommand(name, command, diagnosticLogger));
        diagnosticLogger.info("Command /" + name + " wurde fuer Paper/Brigadier registriert.");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(economyManager, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(itemFactory, sessionManager, databaseManager,
                languageManager, diagnosticLogger, configManager, purchaseService), this);
        getServer().getPluginManager().registerEvents(new GuiListener(rewardManager, purchaseService, sessionManager, guiManager, languageManager, diagnosticLogger), this);
    }

    private void registerPlaceholderApi() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        placeholderExpansion = new CpscPlaceholderExpansion(this, databaseManager);
        placeholderExpansion.register();
        getLogger().info("PlaceholderAPI-Platzhalter wurden registriert.");
        diagnosticLogger.info("PlaceholderAPI-Platzhalter wurden registriert.");
    }

    public void reloadRuntimeConfiguration() {
        try {
            configManager.load();
            rewardManager.reload();
            getServer().getOnlinePlayers().forEach(guiManager::refreshShopIfOpen);
            if (diagnosticLogger != null) {
                diagnosticLogger.info("Konfiguration wurde neu geladen.");
            }
        } catch (Throwable throwable) {
            if (diagnosticLogger != null) {
                diagnosticLogger.error("Fehler beim Neuladen der Konfiguration.", throwable);
            }
            throw throwable;
        }
    }
}
