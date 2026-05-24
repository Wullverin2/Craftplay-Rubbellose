package de.craftplay.scratchcards.listener;

import de.craftplay.scratchcards.config.LanguageManager;
import de.craftplay.scratchcards.database.DatabaseManager;
import de.craftplay.scratchcards.diagnostic.DiagnosticLogger;
import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.gui.GuiManager;
import de.craftplay.scratchcards.service.BedrockSupportService;
import de.craftplay.scratchcards.service.PurchaseService;
import de.craftplay.scratchcards.service.ScratchcardItemFactory;
import de.craftplay.scratchcards.service.ScratchcardSessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class PlayerListener implements Listener {
    private final ScratchcardItemFactory itemFactory;
    private final ScratchcardSessionManager sessionManager;
    private final DatabaseManager databaseManager;
    private final LanguageManager languageManager;
    private final DiagnosticLogger diagnosticLogger;
    private final ConfigManager configManager;
    private final PurchaseService purchaseService;
    private final GuiManager guiManager;
    private final BedrockSupportService bedrockSupportService;

    public PlayerListener(ScratchcardItemFactory itemFactory, ScratchcardSessionManager sessionManager,
                          DatabaseManager databaseManager, LanguageManager languageManager, DiagnosticLogger diagnosticLogger,
                          ConfigManager configManager, PurchaseService purchaseService, GuiManager guiManager,
                          BedrockSupportService bedrockSupportService) {
        this.itemFactory = itemFactory;
        this.sessionManager = sessionManager;
        this.databaseManager = databaseManager;
        this.languageManager = languageManager;
        this.diagnosticLogger = diagnosticLogger;
        this.configManager = configManager;
        this.purchaseService = purchaseService;
        this.guiManager = guiManager;
        this.bedrockSupportService = bedrockSupportService;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        try {
            handleInteract(event);
        } catch (Throwable throwable) {
            diagnosticLogger.error("Fehler im PlayerInteractEvent.", throwable);
            languageManager.send(event.getPlayer(), "internal_error");
        }
    }

    private void handleInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }
        ItemStack item = hand == EquipmentSlot.OFF_HAND
                ? event.getPlayer().getInventory().getItemInOffHand()
                : event.getPlayer().getInventory().getItemInMainHand();
        if (bedrockSupportService.isShopOpener(item)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (!player.hasPermission("craftplay.scratchcards.shop")) {
                languageManager.send(player, "no_permission");
                return;
            }
            guiManager.openShop(player);
            languageManager.send(player, "shop_opened");
            return;
        }
        if (itemFactory.readType(item).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        sessionManager.startFromHand(event.getPlayer(), hand);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        try {
            sessionManager.handleQuit(event.getPlayer());
        } catch (Throwable throwable) {
            diagnosticLogger.error("Fehler im PlayerQuitEvent.", throwable);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        try {
            Player player = event.getPlayer();
            if (databaseManager.hasPending(player.getUniqueId())) {
                languageManager.send(player, "pending_on_join");
            }
            if (configManager.config().getBoolean("daily.notify_on_join", true) && purchaseService.canClaimDaily(player)) {
                languageManager.send(player, "daily_available");
            }
            bedrockSupportService.handleJoin(player);
        } catch (Throwable throwable) {
            diagnosticLogger.error("Fehler im PlayerJoinEvent.", throwable);
        }
    }
}
