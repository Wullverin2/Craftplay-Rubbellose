package de.craftplay.scratchcards.service;

import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.config.LanguageManager;
import de.craftplay.scratchcards.database.DatabaseManager;
import de.craftplay.scratchcards.diagnostic.DiagnosticLogger;
import de.craftplay.scratchcards.economy.EconomyManager;
import de.craftplay.scratchcards.model.ScratchcardType;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PurchaseServiceTest {
    private final YamlConfiguration config = new YamlConfiguration();
    private final ConfigManager configs = mock(ConfigManager.class);
    private final LanguageManager language = mock(LanguageManager.class);
    private final DatabaseManager database = mock(DatabaseManager.class);
    private final EconomyManager economy = mock(EconomyManager.class);
    private final ScratchcardItemFactory items = mock(ScratchcardItemFactory.class);
    private final FeedbackService feedback = mock(FeedbackService.class);
    private final FeatureService features = mock(FeatureService.class);
    private final ProgressionService progression = mock(ProgressionService.class);
    private final DiagnosticLogger diagnostics = mock(DiagnosticLogger.class);
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = mock(PlayerInventory.class);
    private final UUID uuid = UUID.randomUUID();
    private final ScratchcardType type = new ScratchcardType("small", "Small", Material.PAPER, Material.PAPER,
            500, true, 0, 0, List.of());
    private PurchaseService service;

    @BeforeEach
    void setUp() {
        config.set("cooldown.buy_seconds", 0);
        when(configs.config()).thenReturn(config);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Wullverin");
        when(player.getInventory()).thenReturn(inventory);
        when(player.hasPermission("craftplay.scratchcards.buy")).thenReturn(true);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);
        when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());
        when(features.isTypeAvailable(type)).thenReturn(true);
        when(items.canFit(eq(player), eq(type), anyInt())).thenReturn(true);
        when(items.create(eq(type), anyInt())).thenAnswer(invocation -> {
            ItemStack stack = mock(ItemStack.class);
            when(stack.getMaxStackSize()).thenReturn(64);
            when(stack.getAmount()).thenReturn(invocation.getArgument(1, Integer.class));
            return stack;
        });
        when(economy.ensureSetup()).thenReturn(true);
        when(economy.has(eq(player), anyDouble())).thenReturn(true);
        when(economy.withdraw(eq(player), anyDouble())).thenReturn(true);
        when(economy.deposit(eq(player), anyDouble())).thenReturn(true);
        when(economy.format(anyDouble())).thenAnswer(invocation -> invocation.getArgument(0).toString());
        when(database.recordPurchases(eq(uuid), eq("Wullverin"), eq("small"), eq(500.0), anyInt())).thenReturn(true);
        service = new PurchaseService(configs, language, database, economy, items, feedback, features, progression, diagnostics);
    }

    @Test
    void buysFiveTicketsWithOneWithdrawalAndFiveProgressionPoints() {
        when(database.countPurchasesSince(eq(uuid), anyLong())).thenReturn(3, 8);
        assertTrue(service.buy(player, type, 5));
        verify(economy).withdraw(player, 2500);
        verify(database).recordPurchases(uuid, "Wullverin", "small", 500, 5);
        verify(progression).onBuy(player, 5);
        ArgumentCaptor<ItemStack[]> stacks = ArgumentCaptor.forClass(ItemStack[].class);
        verify(inventory).addItem(stacks.capture());
        assertEquals(5, stacks.getValue()[0].getAmount());
        verify(language).send(eq(player), eq("purchase_success"), argThat(values ->
                values.get("%amount%").equals("5") && values.get("%daily_bought%").equals("8")
                        && values.get("%daily_remaining%").equals("17")));
    }

    @Test
    void rejectsWholeBatchWhenOnlyFourDailyPurchasesRemain() {
        when(database.countPurchasesSince(eq(uuid), anyLong())).thenReturn(21);
        assertFalse(service.buy(player, type, 5));
        verify(language).send(eq(player), eq("purchase_limit_day"), anyMap());
        verifyNoInteractions(economy, progression);
        verify(inventory, never()).addItem(any(ItemStack[].class));
    }

    @Test
    void acceptsExactRemainingDailyAllowance() {
        when(database.countPurchasesSince(eq(uuid), anyLong())).thenReturn(20, 25);
        assertTrue(service.buy(player, type, 5));
    }

    @Test
    void respectsOwnedLimitForEntireBatch() {
        when(items.countOwned(player)).thenReturn(60);
        assertFalse(service.buy(player, type, 5));
        verify(language).send(eq(player), eq("owned_limit"), anyMap());
        verifyNoInteractions(economy, progression);
    }

    @Test
    void rejectsFullInventoryBeforeCharging() {
        when(items.canFit(player, type, 5)).thenReturn(false);
        assertFalse(service.buy(player, type, 5));
        verifyNoInteractions(economy, progression);
        verify(database, never()).recordPurchases(any(), anyString(), anyString(), anyDouble(), anyInt());
    }

    @Test
    void checksTotalPriceNotSingleTicketPrice() {
        when(economy.has(player, 2500)).thenReturn(false);
        assertFalse(service.buy(player, type, 5));
        verify(economy, never()).withdraw(any(), anyDouble());
        verify(inventory, never()).addItem(any(ItemStack[].class));
    }

    @Test
    void failedWithdrawalDoesNotDeliverOrRecordTickets() {
        when(economy.withdraw(player, 2500)).thenReturn(false);
        assertFalse(service.buy(player, type, 5));
        verify(inventory, never()).addItem(any(ItemStack[].class));
        verify(database, never()).recordPurchases(any(), anyString(), anyString(), anyDouble(), anyInt());
        verifyNoInteractions(progression);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 65, Integer.MAX_VALUE})
    void rejectsInvalidAmounts(int amount) {
        assertFalse(service.buy(player, type, amount));
        verifyNoInteractions(economy, items, database, progression);
    }

    @Test
    void configurableBatchSizeSplitsIntoValidStacks() {
        config.set("limits.enabled", false);
        config.set("purchases.max_amount_per_purchase", 128);
        assertTrue(service.buy(player, type, 70));
        ArgumentCaptor<ItemStack[]> stacks = ArgumentCaptor.forClass(ItemStack[].class);
        verify(inventory).addItem(stacks.capture());
        assertArrayEquals(new int[]{64, 6}, java.util.Arrays.stream(stacks.getValue()).mapToInt(ItemStack::getAmount).toArray());
        verify(economy).withdraw(player, 35000);
        verify(database).recordPurchases(uuid, "Wullverin", "small", 500, 70);
    }

    @Test
    void databaseFailureRestoresClonedInventoryAndRefundsWholeAmount() {
        ItemStack original = mock(ItemStack.class);
        ItemStack snapshot = mock(ItemStack.class);
        when(original.clone()).thenReturn(snapshot);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{original, null});
        when(database.recordPurchases(uuid, "Wullverin", "small", 500, 5)).thenReturn(false);
        assertFalse(service.buy(player, type, 5));
        ArgumentCaptor<ItemStack[]> contents = ArgumentCaptor.forClass(ItemStack[].class);
        verify(inventory).setStorageContents(contents.capture());
        assertSame(snapshot, contents.getValue()[0]);
        assertNull(contents.getValue()[1]);
        verify(economy).deposit(player, 2500);
        verifyNoInteractions(progression, feedback);
    }

    @Test
    void partialInventoryInsertionRestoresInventoryAndRefundsWithoutDatabaseWrite() {
        HashMap<Integer, ItemStack> overflow = new HashMap<>();
        overflow.put(0, mock(ItemStack.class));
        when(inventory.addItem(any(ItemStack[].class))).thenReturn(overflow);
        assertFalse(service.buy(player, type, 5));
        verify(inventory).setStorageContents(any(ItemStack[].class));
        verify(economy).deposit(player, 2500);
        verify(database, never()).recordPurchases(any(), anyString(), anyString(), anyDouble(), anyInt());
        verifyNoInteractions(progression);
    }

    @Test
    void unexpectedDeliveryFailureAlsoRefundsAndPropagatesForDiagnostics() {
        when(inventory.addItem(any(ItemStack[].class))).thenThrow(new IllegalStateException("test"));
        assertThrows(IllegalStateException.class, () -> service.buy(player, type, 5));
        verify(economy).deposit(player, 2500);
        verify(inventory).setStorageContents(any(ItemStack[].class));
    }

    @Test
    void logsFailedRefundAndDoesNotCountPurchaseProgress() {
        when(database.recordPurchases(uuid, "Wullverin", "small", 500, 5)).thenReturn(false);
        when(economy.deposit(player, 2500)).thenReturn(false);
        assertFalse(service.buy(player, type, 5));
        verify(diagnostics).error(contains("Kauf-Erstattung fehlgeschlagen"), isNull());
        verify(language).send(eq(player), eq("purchase_refund_failed"), anyMap());
        verifyNoInteractions(progression);
    }
}
