package de.craftplay.scratchcards.economy;

import de.craftplay.scratchcards.CraftplayScratchcardsPlugin;
import de.craftplay.scratchcards.diagnostic.DiagnosticLogger;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;

public final class EconomyManager implements Listener {
    private static final String VAULT_ECONOMY_CLASS = "net.milkbowl.vault.economy.Economy";
    private final CraftplayScratchcardsPlugin plugin;
    private final DiagnosticLogger diagnosticLogger;
    private Object economy;
    private Class<?> economyClass;

    public EconomyManager(CraftplayScratchcardsPlugin plugin, DiagnosticLogger diagnosticLogger) {
        this.plugin = plugin;
        this.diagnosticLogger = diagnosticLogger;
    }

    public boolean setup() {
        Class<?> loadedEconomyClass = loadEconomyClass();
        if (loadedEconomyClass == null) {
            economy = null;
            economyClass = null;
            return false;
        }

        RegisteredServiceProvider<?> provider = plugin.getServer().getServicesManager().getRegistration(loadedEconomyClass);
        if (provider == null || provider.getProvider() == null) {
            economy = null;
            economyClass = loadedEconomyClass;
            return false;
        }

        economyClass = loadedEconomyClass;
        economy = provider.getProvider();
        return true;
    }

    public boolean ensureSetup() {
        return economy != null || setup();
    }

    public boolean isAvailable() {
        return ensureSetup();
    }

    public String providerName() {
        if (!ensureSetup()) {
            return "-";
        }
        Object value = invoke("getName", new Class<?>[0]);
        return value == null ? economy.getClass().getSimpleName() : String.valueOf(value);
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (!ensureSetup()) {
            return false;
        }
        Object value = invoke("has", new Class<?>[]{OfflinePlayer.class, double.class}, player, amount);
        return value instanceof Boolean result && result;
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (amount <= 0.0D) {
            return true;
        }
        if (!ensureSetup()) {
            return false;
        }
        Object response = invoke("withdrawPlayer", new Class<?>[]{OfflinePlayer.class, double.class}, player, amount);
        return transactionSuccess(response);
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (amount <= 0.0D) {
            return true;
        }
        if (!ensureSetup()) {
            return false;
        }
        Object response = invoke("depositPlayer", new Class<?>[]{OfflinePlayer.class, double.class}, player, amount);
        return transactionSuccess(response);
    }

    public String format(double amount) {
        if (!ensureSetup()) {
            return String.valueOf(amount);
        }
        Object value = invoke("format", new Class<?>[]{double.class}, amount);
        return value == null ? String.valueOf(amount) : String.valueOf(value);
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (!VAULT_ECONOMY_CLASS.equals(event.getProvider().getService().getName())) {
            return;
        }
        if (setup()) {
            plugin.getLogger().info("Vault-Economy gefunden: " + providerName());
            diagnosticLogger.info("Vault-Economy gefunden: " + providerName());
        }
    }

    @EventHandler
    public void onServiceUnregister(ServiceUnregisterEvent event) {
        if (!VAULT_ECONOMY_CLASS.equals(event.getProvider().getService().getName())) {
            return;
        }
        if (event.getProvider().getProvider() == economy) {
            economy = null;
            plugin.getLogger().warning("Vault-Economy wurde deregistriert. Kaufen und Geld-Auszahlungen sind bis zur erneuten Registrierung nicht verfügbar.");
            diagnosticLogger.warning("Vault-Economy wurde deregistriert.", null);
        }
    }

    private Class<?> loadEconomyClass() {
        Plugin vault = plugin.getServer().getPluginManager().getPlugin("Vault");
        if (vault != null) {
            try {
                return Class.forName(VAULT_ECONOMY_CLASS, false, vault.getClass().getClassLoader());
            } catch (ClassNotFoundException ignored) {
            }
        }
        try {
            return Class.forName(VAULT_ECONOMY_CLASS);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private Object invoke(String methodName, Class<?>[] parameterTypes, Object... args) {
        if (economy == null || economyClass == null) {
            return null;
        }
        try {
            Method method = economyClass.getMethod(methodName, parameterTypes);
            return method.invoke(economy, args);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            plugin.getLogger().log(Level.WARNING, "Vault-Economy Methode konnte nicht ausgeführt werden: " + methodName, exception);
            diagnosticLogger.warning("Vault-Economy Methode konnte nicht ausgeführt werden: " + methodName, exception);
            return null;
        }
    }

    private boolean transactionSuccess(Object response) {
        if (response == null) {
            return false;
        }
        try {
            Method method = response.getClass().getMethod("transactionSuccess");
            Object value = method.invoke(response);
            return value instanceof Boolean result && result;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            plugin.getLogger().log(Level.WARNING, "Vault-Economy Antwort konnte nicht gelesen werden.", exception);
            diagnosticLogger.warning("Vault-Economy Antwort konnte nicht gelesen werden.", exception);
            return false;
        }
    }
}
