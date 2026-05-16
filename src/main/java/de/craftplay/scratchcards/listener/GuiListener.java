package de.craftplay.scratchcards.listener;

import de.craftplay.scratchcards.config.LanguageManager;
import de.craftplay.scratchcards.diagnostic.DiagnosticLogger;
import de.craftplay.scratchcards.gui.BoardHolder;
import de.craftplay.scratchcards.gui.GuiManager;
import de.craftplay.scratchcards.gui.HistoryHolder;
import de.craftplay.scratchcards.gui.JackpotHistoryHolder;
import de.craftplay.scratchcards.gui.ScratchcardHolder;
import de.craftplay.scratchcards.gui.ShopHolder;
import de.craftplay.scratchcards.service.PurchaseService;
import de.craftplay.scratchcards.service.RewardManager;
import de.craftplay.scratchcards.service.ScratchcardSessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class GuiListener implements Listener {
    private final RewardManager rewardManager;
    private final PurchaseService purchaseService;
    private final ScratchcardSessionManager sessionManager;
    private final GuiManager guiManager;
    private final LanguageManager languageManager;
    private final DiagnosticLogger diagnosticLogger;

    public GuiListener(RewardManager rewardManager, PurchaseService purchaseService,
                       ScratchcardSessionManager sessionManager, GuiManager guiManager,
                       LanguageManager languageManager, DiagnosticLogger diagnosticLogger) {
        this.rewardManager = rewardManager;
        this.purchaseService = purchaseService;
        this.sessionManager = sessionManager;
        this.guiManager = guiManager;
        this.languageManager = languageManager;
        this.diagnosticLogger = diagnosticLogger;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        try {
            handleClick(event);
        } catch (Throwable throwable) {
            diagnosticLogger.error("Fehler im InventoryClickEvent.", throwable);
            if (event.getWhoClicked() instanceof Player player) {
                languageManager.send(player, "internal_error");
            }
        }
    }

    private void handleClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof ShopHolder shopHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() >= top.getSize()) {
                return;
            }
            String typeId = shopHolder.typeAt(event.getRawSlot());
            if (typeId == null) {
                return;
            }
            rewardManager.type(typeId).ifPresentOrElse(
                    type -> purchaseService.buy(player, type),
                    () -> languageManager.send(player, "type_not_found")
            );
            return;
        }

        if (top.getHolder() instanceof ScratchcardHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() >= top.getSize()) {
                return;
            }
            int rawSlot = event.getRawSlot();
            if (guiManager.isCloseSlot(rawSlot)) {
                player.closeInventory();
                return;
            }
            int index = guiManager.scratchIndexBySlot(rawSlot);
            if (index >= 0) {
                sessionManager.reveal(player, index);
            }
        }

        if (top.getHolder() instanceof JackpotHistoryHolder) {
            event.setCancelled(true);
        }

        if (top.getHolder() instanceof HistoryHolder) {
            event.setCancelled(true);
        }

        if (top.getHolder() instanceof BoardHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        try {
            Inventory top = event.getView().getTopInventory();
            if (top.getHolder() instanceof ShopHolder || top.getHolder() instanceof ScratchcardHolder
                    || top.getHolder() instanceof JackpotHistoryHolder || top.getHolder() instanceof HistoryHolder
                    || top.getHolder() instanceof BoardHolder) {
                event.setCancelled(true);
            }
        } catch (Throwable throwable) {
            diagnosticLogger.error("Fehler im InventoryDragEvent.", throwable);
        }
    }
}
