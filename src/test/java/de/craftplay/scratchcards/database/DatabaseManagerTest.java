package de.craftplay.scratchcards.database;

import de.craftplay.scratchcards.CraftplayScratchcardsPlugin;
import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.diagnostic.DiagnosticLogger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DatabaseManagerTest {
    @TempDir
    Path directory;
    private DatabaseManager database;
    private final DiagnosticLogger diagnostics = mock(DiagnosticLogger.class);
    private final UUID uuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        CraftplayScratchcardsPlugin plugin = mock(CraftplayScratchcardsPlugin.class);
        ConfigManager configs = mock(ConfigManager.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(configs.config()).thenReturn(new YamlConfiguration());
        database = new DatabaseManager(plugin, configs, diagnostics);
        database.initialize();
    }

    @AfterEach
    void close() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void batchCountsEachTicketAndPreservesUnitPricesAndExistingStatistics() {
        assertTrue(database.recordPurchases(uuid, "Player", "small", 500, 5));
        assertTrue(database.recordPurchases(uuid, "Player", "premium", 2000, 2));
        assertEquals(7, database.countPurchasesSince(uuid, 0));
        assertEquals(7, database.getPlayerStats(uuid, "Player").bought());
        assertEquals(7, database.getServerStats(10).totalBought());
        assertEquals(6500, database.getServerStats(10).totalIncome());
        assertEquals(0, database.countPurchasesSince(uuid, Long.MAX_VALUE));
    }

    @Test
    void failingSecondInsertRollsBackAllTicketsAndStatistics() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("scratchcards.db"));
             var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_second_purchase BEFORE INSERT ON cpsc_purchases "
                    + "WHEN (SELECT COUNT(*) FROM cpsc_purchases) >= 1 "
                    + "BEGIN SELECT RAISE(ABORT, 'simulated storage error'); END");
        }
        assertFalse(database.recordPurchases(uuid, "Player", "small", 500, 5));
        assertEquals(0, database.countPurchasesSince(uuid, 0));
        assertEquals(0, database.getPlayerStats(uuid, "Player").bought());
        assertEquals(0, database.getServerStats(10).totalIncome());
        verify(diagnostics).error(contains("Kauf konnte nicht vollstaendig gespeichert werden"), any(Throwable.class));
    }

    @Test
    void statisticsFailureRollsBackPurchasedTicketsButPreservesEarlierPurchases() throws Exception {
        assertTrue(database.recordPurchases(uuid, "Player", "small", 500, 2));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("scratchcards.db"));
             var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_stats BEFORE UPDATE ON cpsc_player_stats "
                    + "BEGIN SELECT RAISE(ABORT, 'simulated stats error'); END");
        }
        assertFalse(database.recordPurchases(uuid, "Player", "small", 500, 5));
        assertEquals(2, database.countPurchasesSince(uuid, 0));
        assertEquals(2, database.getPlayerStats(uuid, "Player").bought());
        assertEquals(1000, database.getServerStats(10).totalIncome());
    }

    @Test
    void newDatabaseDoesNotCreateServerGoalsTableButKeepsStatisticsAndGroupGoals() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("scratchcards.db"));
             var statement = connection.createStatement();
             var tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
            java.util.Set<String> names = new java.util.HashSet<>();
            while (tables.next()) {
                names.add(tables.getString("name"));
            }
            assertFalse(names.contains("cpsc_server_goals"));
            assertTrue(names.contains("cpsc_group_goals"));
            assertTrue(names.contains("cpsc_player_stats"));
            assertTrue(names.contains("cpsc_opens"));
        }
    }

    @Test
    void existingServerGoalHistoryAndPlayerStatisticsAreNotDeletedOnStartup() throws Exception {
        assertTrue(database.recordPurchases(uuid, "Player", "small", 500, 2));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("scratchcards.db"));
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE cpsc_server_goals (goal_id VARCHAR(64) PRIMARY KEY, opened_count BIGINT, completed_at BIGINT)");
            statement.execute("INSERT INTO cpsc_server_goals VALUES ('old_goal', 500, 1)");
        }
        database.close();
        database.initialize();

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("scratchcards.db"));
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT opened_count FROM cpsc_server_goals WHERE goal_id = 'old_goal'")) {
            assertTrue(result.next());
            assertEquals(500, result.getLong(1));
        }
        assertEquals(2, database.getPlayerStats(uuid, "Player").bought());
        assertEquals(1000, database.getServerStats(10).totalIncome());
    }
}
