package de.craftplay.scratchcards.listener;

import de.craftplay.scratchcards.config.LanguageManager;
import de.craftplay.scratchcards.diagnostic.DiagnosticLogger;
import de.craftplay.scratchcards.gui.GuiManager;
import de.craftplay.scratchcards.gui.ShopHolder;
import de.craftplay.scratchcards.model.ScratchcardType;
import de.craftplay.scratchcards.service.PurchaseService;
import de.craftplay.scratchcards.service.RewardManager;
import de.craftplay.scratchcards.service.ScratchcardSessionManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GuiListenerTest {
    private final ShopHolder holder = new ShopHolder();
    private final RewardManager rewards = mock(RewardManager.class);
    private final PurchaseService purchases = mock(PurchaseService.class);
    private final GuiManager gui = mock(GuiManager.class);
    private final Player player = mock(Player.class);
    private final InventoryClickEvent event = mock(InventoryClickEvent.class);
    private final Inventory inventory = mock(Inventory.class);
    private final ScratchcardType type = new ScratchcardType("small", "Small", Material.PAPER, Material.PAPER,
            500, true, 0, 0, List.of());
    private final GuiListener listener = new GuiListener(rewards, purchases, mock(ScratchcardSessionManager.class), gui,
            mock(LanguageManager.class), mock(DiagnosticLogger.class));

    @BeforeEach
    void setUp() {
        InventoryView view = mock(InventoryView.class);
        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(inventory.getHolder()).thenReturn(holder);
        when(inventory.getSize()).thenReturn(27);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        holder.setType(10, "small");
        holder.setAmountOption(19, 5);
        when(rewards.type("small")).thenReturn(Optional.of(type));
    }

    @Test
    void selectingAmountDoesNotBuyOrCharge() {
        when(event.getRawSlot()).thenReturn(19);
        listener.onClick(event);
        assertEquals(5, holder.amount());
        verify(gui).refreshShop(player, holder);
        verify(event).setCancelled(true);
        verifyNoInteractions(purchases);
    }

    @Test
    void clickingTypeBuysSelectedAmountAndRefreshesExistingInventory() {
        holder.amount(10);
        when(event.getRawSlot()).thenReturn(10);
        listener.onClick(event);
        verify(purchases).buy(player, type, 10);
        verify(gui).refreshShop(player, holder);
        verify(player, never()).openInventory(inventory);
    }

    @ParameterizedTest
    @EnumSource(value = ClickType.class, names = {"SHIFT_LEFT", "SHIFT_RIGHT", "NUMBER_KEY", "DOUBLE_CLICK", "DROP", "SWAP_OFFHAND"})
    void specialClicksCannotTriggerPurchase(ClickType click) {
        when(event.getClick()).thenReturn(click);
        when(event.getRawSlot()).thenReturn(10);
        listener.onClick(event);
        verify(event).setCancelled(true);
        verifyNoInteractions(purchases);
    }

    @Test
    void dailyButtonClaimsTheDailyTypeWithoutBuyingTheSelectedQuantity() {
        holder.dailySlot(24);
        holder.amount(10);
        when(gui.dailyTypeId()).thenReturn("small");
        when(event.getRawSlot()).thenReturn(24);
        listener.onClick(event);
        verify(event).setCancelled(true);
        verify(purchases).claimDaily(player, type);
        verify(purchases, never()).buy(any(), any(), anyInt());
        verify(gui).refreshShop(player, holder);
        verify(player, never()).openInventory(any(Inventory.class));
    }

    @ParameterizedTest
    @EnumSource(value = ClickType.class, names = {"SHIFT_LEFT", "SHIFT_RIGHT", "NUMBER_KEY", "DOUBLE_CLICK", "DROP", "SWAP_OFFHAND"})
    void specialClicksCannotClaimDailyTickets(ClickType click) {
        holder.dailySlot(24);
        when(event.getClick()).thenReturn(click);
        when(event.getRawSlot()).thenReturn(24);
        listener.onClick(event);
        verify(event).setCancelled(true);
        verifyNoInteractions(purchases);
    }

    @Test
    void invalidDailyTypeDoesNotGiveOrBuyAnything() {
        holder.dailySlot(24);
        when(gui.dailyTypeId()).thenReturn("missing");
        when(rewards.type("missing")).thenReturn(Optional.empty());
        when(event.getRawSlot()).thenReturn(24);
        listener.onClick(event);
        verifyNoInteractions(purchases);
        verify(gui).refreshShop(player, holder);
    }

    @Test
    void clickingOutsideOrPlayerInventoryDoesNotBuy() {
        when(event.getRawSlot()).thenReturn(-999, 37);
        listener.onClick(event);
        listener.onClick(event);
        verifyNoInteractions(purchases);
    }
}
