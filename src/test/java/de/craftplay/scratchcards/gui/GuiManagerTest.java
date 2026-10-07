package de.craftplay.scratchcards.gui;

import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.database.DatabaseManager;
import de.craftplay.scratchcards.model.ScratchcardType;
import de.craftplay.scratchcards.service.FeatureService;
import de.craftplay.scratchcards.service.RewardManager;
import de.craftplay.scratchcards.service.ScratchcardItemFactory;
import de.craftplay.scratchcards.util.ItemBuilder;
import de.craftplay.scratchcards.util.MaterialUtil;
import de.craftplay.scratchcards.util.ServerDayUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GuiManagerTest {
    private MockedStatic<MaterialUtil> materialParser;
    private final ConfigManager configs = mock(ConfigManager.class);
    private final YamlConfiguration config = new YamlConfiguration();
    private final YamlConfiguration guiConfig = new YamlConfiguration();
    private final YamlConfiguration language = new YamlConfiguration();
    private final DatabaseManager database = mock(DatabaseManager.class);
    private final RewardManager rewards = mock(RewardManager.class);
    private final FeatureService features = mock(FeatureService.class);
    private final Player player = mock(Player.class);
    private final Inventory inventory = mock(Inventory.class);
    private final ShopHolder holder = new ShopHolder();
    private final UUID uuid = UUID.randomUUID();
    private final List<Material> materials = new ArrayList<>();
    private final ScratchcardType type = new ScratchcardType("small", "Small", Material.PAPER, Material.PAPER,
            500, true, 0, 0, List.of());
    private final GuiManager gui = new GuiManager(configs, rewards, database, mock(ScratchcardItemFactory.class), features);

    @BeforeEach
    void setUp() {
        materialParser = mockStatic(MaterialUtil.class);
        // Die Paper-Materialregistrierung existiert nur auf einem laufenden Server.
        materialParser.when(() -> MaterialUtil.parse(any(), any())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            return name == null ? invocation.getArgument(1) : Material.valueOf(name);
        });
        when(configs.config()).thenReturn(config);
        when(configs.gui()).thenReturn(guiConfig);
        when(configs.language()).thenReturn(language);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Player");
        when(player.hasPermission("craftplay.scratchcards.daily")).thenReturn(true);
        when(rewards.type("small")).thenReturn(Optional.of(type));
        when(features.isTypeAvailable(type)).thenReturn(true);
        when(inventory.getSize()).thenReturn(27);
        when(inventory.getHolder()).thenReturn(holder);
        InventoryView view = mock(InventoryView.class);
        when(player.getOpenInventory()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        holder.setInventory(inventory);
        guiConfig.set("shop.filler.enabled", false);
        guiConfig.set("shop.quantity_selector.enabled", false);
        guiConfig.set("shop.info.slot", -1);
        guiConfig.set("shop.daily.slot", 24);
        guiConfig.set("shop.daily.available_item.material", "CHEST");
        guiConfig.set("shop.daily.available_item.name", "Available");
        guiConfig.set("shop.daily.available_item.lore", List.of("%type%", "%amount%"));
        guiConfig.set("shop.daily.claimed_item.material", "CLOCK");
        guiConfig.set("shop.daily.claimed_item.name", "Claimed");
        guiConfig.set("shop.daily.claimed_item.lore", List.of("%daily_countdown%"));
        guiConfig.set("shop.daily.disabled_item.material", "GRAY_DYE");
        guiConfig.set("shop.daily.disabled_item.name", "Blocked");
        guiConfig.set("shop.daily.disabled_item.lore", List.of("%reason%"));
        for (String key : List.of("disabled", "no_permission", "invalid_type", "inactive_type")) {
            language.set("daily_button_" + key, key);
        }
    }

    @AfterEach
    void tearDown() {
        materialParser.close();
    }

    private MockedConstruction<ItemBuilder> builders() {
        return mockConstruction(ItemBuilder.class, withSettings().defaultAnswer(RETURNS_SELF), (builder, context) -> {
            materials.add((Material) context.arguments().getFirst());
            when(builder.build()).thenReturn(mock(ItemStack.class));
        });
    }

    @Test
    void rendersAvailableButtonWithConfiguredDailyAmountAndItsOwnClickMapping() {
        config.set("daily.amount", 3);
        try (var builders = builders()) {
            gui.refreshShop(player, holder);
            assertTrue(holder.isDailySlot(24));
            assertFalse(holder.dailyClaimed());
            verify(builders.constructed().getLast()).name("Available");
            verify(builders.constructed().getLast()).lore(List.of("Small", "3"));
            assertEquals(Material.CHEST, materials.getLast());
            verify(inventory).setItem(eq(24), any(ItemStack.class));
        }
    }

    @Test
    void updatesClaimedCountdownWithoutRepeatedDatabaseQueries() {
        try (var clock = mockStatic(ServerDayUtil.class);
             var bukkit = mockStatic(Bukkit.class);
             var builders = builders()) {
            clock.when(() -> ServerDayUtil.serverDayStartMillis(anyLong())).thenReturn(1000L);
            clock.when(() -> ServerDayUtil.countdownUntilNextServerDay(anyLong())).thenReturn("01:00:00", "00:59:59");
            when(database.countDailyClaimsSince(uuid, 1000L)).thenReturn(1);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            gui.refreshShop(player, holder);
            verify(builders.constructed().getLast()).name("Claimed");
            verify(builders.constructed().getLast()).lore(List.of("01:00:00"));
            gui.updateOpenShops();
            verify(builders.constructed().getLast()).lore(List.of("00:59:59"));
            assertEquals(Material.CLOCK, materials.getLast());
            verify(database).countDailyClaimsSince(uuid, 1000L);
            verify(inventory).clear();
        }
    }

    @Test
    void switchesAnAlreadyOpenShopToAvailableWhenTheServerDayChanges() {
        try (var clock = mockStatic(ServerDayUtil.class);
             var bukkit = mockStatic(Bukkit.class);
             var builders = builders()) {
            clock.when(() -> ServerDayUtil.serverDayStartMillis(anyLong())).thenReturn(1000L);
            clock.when(() -> ServerDayUtil.countdownUntilNextServerDay(anyLong())).thenReturn("00:00:01");
            when(database.countDailyClaimsSince(uuid, 1000L)).thenReturn(1);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            gui.refreshShop(player, holder);
            assertTrue(holder.dailyClaimed());
            clock.when(() -> ServerDayUtil.serverDayStartMillis(anyLong())).thenReturn(2000L);
            gui.updateOpenShops();
            assertFalse(holder.dailyClaimed());
            assertEquals(2000, holder.dailyDayStart());
            verify(builders.constructed().getLast()).name("Available");
            verify(database).countDailyClaimsSince(uuid, 2000L);
            verify(inventory, times(2)).clear();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "no_permission", "invalid_type", "inactive_type"})
    void unavailableDailyTicketsShowTheReason(String reason) {
        switch (reason) {
            case "disabled" -> config.set("daily.enabled", false);
            case "no_permission" -> when(player.hasPermission("craftplay.scratchcards.daily")).thenReturn(false);
            case "invalid_type" -> when(rewards.type("small")).thenReturn(Optional.empty());
            case "inactive_type" -> when(features.isTypeAvailable(type)).thenReturn(false);
            default -> fail("Unexpected reason");
        }
        try (var builders = builders()) {
            gui.refreshShop(player, holder);
            verify(builders.constructed().getLast()).name("Blocked");
            verify(builders.constructed().getLast()).lore(List.of(reason));
            assertEquals(Material.GRAY_DYE, materials.getLast());
        }
    }

    @Test
    void hidingTheButtonRemovesItsClickMapping() {
        try (var builders = builders()) {
            gui.refreshShop(player, holder);
            assertTrue(holder.isDailySlot(24));
            guiConfig.set("shop.daily.enabled", false);
            gui.refreshShop(player, holder);
            assertFalse(holder.isDailySlot(24));
        }
    }

    @Test
    void dailyButtonDoesNotOverwriteAnExistingQuantitySelector() {
        guiConfig.set("shop.quantity_selector.enabled", true);
        guiConfig.set("shop.quantity_selector.amounts", List.of(5));
        guiConfig.set("shop.quantity_selector.slots", List.of(24));
        try (var builders = builders()) {
            gui.refreshShop(player, holder);
            assertFalse(holder.isDailySlot(24));
            assertEquals(5, holder.amountAt(24));
            assertEquals(1, builders.constructed().size());
        }
    }
}
