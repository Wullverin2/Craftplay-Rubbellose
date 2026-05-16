package de.craftplay.scratchcards.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.craftplay.scratchcards.CraftplayScratchcardsPlugin;
import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.diagnostic.DiagnosticLogger;
import de.craftplay.scratchcards.model.DailyStreak;
import de.craftplay.scratchcards.model.JackpotEntry;
import de.craftplay.scratchcards.model.PendingScratchcard;
import de.craftplay.scratchcards.model.GroupGoalProgress;
import de.craftplay.scratchcards.model.PassProgress;
import de.craftplay.scratchcards.model.PlayerStats;
import de.craftplay.scratchcards.model.QuestProgress;
import de.craftplay.scratchcards.model.Reward;
import de.craftplay.scratchcards.model.RewardHistoryEntry;
import de.craftplay.scratchcards.model.RiskOffer;
import de.craftplay.scratchcards.model.ServerStats;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class DatabaseManager {
    private final CraftplayScratchcardsPlugin plugin;
    private final ConfigManager configManager;
    private final DiagnosticLogger diagnosticLogger;
    private HikariDataSource dataSource;
    private String prefix;
    private boolean mysql;

    public DatabaseManager(CraftplayScratchcardsPlugin plugin, ConfigManager configManager, DiagnosticLogger diagnosticLogger) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.diagnosticLogger = diagnosticLogger;
    }

    public void initialize() {
        mysql = configManager.config().getBoolean("database.use_mysql", false);
        prefix = configManager.config().getString("database.table_prefix", "cpsc_");

        HikariConfig hikari = new HikariConfig();
        if (mysql) {
            String host = configManager.config().getString("database.mysql.host", "localhost");
            int port = configManager.config().getInt("database.mysql.port", 3306);
            String database = configManager.config().getString("database.mysql.database", "minecraft");
            boolean ssl = configManager.config().getBoolean("database.mysql.use_ssl", false);
            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=" + ssl + "&allowPublicKeyRetrieval=true&characterEncoding=utf8");
            hikari.setUsername(configManager.config().getString("database.mysql.username", "root"));
            hikari.setPassword(configManager.config().getString("database.mysql.password", ""));
            hikari.setMaximumPoolSize(configManager.config().getInt("database.mysql.pool.max_pool_size", 10));
            hikari.setMinimumIdle(configManager.config().getInt("database.mysql.pool.min_idle", 2));
            hikari.setConnectionTimeout(configManager.config().getLong("database.mysql.pool.connection_timeout_ms", 30000L));
        } else {
            File file = new File(plugin.getDataFolder(), configManager.config().getString("database.sqlite.file", "scratchcards.db"));
            hikari.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setMaximumPoolSize(1);
        }
        hikari.setPoolName("CraftplayScratchcards");
        dataSource = new HikariDataSource(hikari);
        createTables();
    }

    private void createTables() {
        String id = mysql ? "BIGINT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";
        execute("CREATE TABLE IF NOT EXISTS " + table("purchases") + " ("
                + "id " + id + ","
                + "uuid VARCHAR(36) NOT NULL,"
                + "player_name VARCHAR(32) NOT NULL,"
                + "type_id VARCHAR(64) NOT NULL,"
                + "price DOUBLE NOT NULL,"
                + "purchased_at BIGINT NOT NULL"
                + ")");
        execute("CREATE TABLE IF NOT EXISTS " + table("rewards_log") + " ("
                + "id " + id + ","
                + "uuid VARCHAR(36) NOT NULL,"
                + "player_name VARCHAR(32) NOT NULL,"
                + "type_id VARCHAR(64) NOT NULL,"
                + "reward_id VARCHAR(64) NOT NULL,"
                + "reward_name VARCHAR(128) NOT NULL,"
                + "money DOUBLE NOT NULL,"
                + "jackpot TINYINT NOT NULL,"
                + "created_at BIGINT NOT NULL"
                + ")");
        execute("CREATE TABLE IF NOT EXISTS " + table("opens") + " ("
                + "id " + id + ","
                + "uuid VARCHAR(36) NOT NULL,"
                + "player_name VARCHAR(32) NOT NULL,"
                + "type_id VARCHAR(64) NOT NULL,"
                + "opened_at BIGINT NOT NULL"
                + ")");
        execute("CREATE TABLE IF NOT EXISTS " + table("daily_claims") + " ("
                + "id " + id + ","
                + "uuid VARCHAR(36) NOT NULL,"
                + "player_name VARCHAR(32) NOT NULL,"
                + "type_id VARCHAR(64) NOT NULL,"
                + "amount INT NOT NULL,"
                + "claimed_at BIGINT NOT NULL"
                + ")");
        execute("CREATE TABLE IF NOT EXISTS " + table("daily_streaks") + " ("
                + "uuid VARCHAR(36) PRIMARY KEY,"
                + "player_name VARCHAR(32) NOT NULL,"
                + "current_streak INT NOT NULL,"
                + "best_streak INT NOT NULL,"
                + "last_day_start BIGINT NOT NULL"
                + ")");
        execute("CREATE TABLE IF NOT EXISTS " + table("series_symbols") + " ("
                + "id " + id + ","
                + "uuid VARCHAR(36) NOT NULL,"
                + "player_name VARCHAR(32) NOT NULL,"
                + "series_id VARCHAR(64) NOT NULL,"
                + "symbol_id VARCHAR(128) NOT NULL,"
                + "collected_at BIGINT NOT NULL"
                + ")");
        execute("CREATE TABLE IF NOT EXISTS " + table("series_claims") + " ("
                + "id " + id + ","
                + "uuid VARCHAR(36) NOT NULL,"
                + "player_name VARCHAR(32) NOT NULL,"
                + "series_id VARCHAR(64) NOT NULL,"
                + "claimed_at BIGINT NOT NULL"
                + ")");
        execute("CREATE TABLE IF NOT EXISTS " + table("server_goals") + " ("
                + "goal_id VARCHAR(64) PRIMARY KEY,"
                + "opened_count BIGINT NOT NULL,"
                + "completed_at BIGINT NOT NULL"
                + ")");
        execute("CREATE TABLE IF NOT EXISTS " + table("pass_progress") + " ("
                + "uuid VARCHAR(36) NOT NULL,"
                + "player_name VARCHAR(32) NOT NULL,"
                + "season VARCHAR(64) NOT NULL,"
                + "xp INT NOT NULL,"
                + "claimed_levels INT NOT NULL"
                + ")");
        execute("CREATE TABLE IF NOT EXISTS " + table("quest_progress") + " ("
                + "uuid VARCHAR(36) NOT NULL,"
                + "player_name VARCHAR(32) NOT NULL,"
                + "quest_id VARCHAR(64) NOT NULL,"
                + "day_start BIGINT NOT NULL,"
                + "progress INT NOT NULL,"
                + "completed TINYINT NOT NULL"
                + ")");
        execute("CREATE TABLE IF NOT EXISTS " + table("pity_counters") + " ("
                + "uuid VARCHAR(36) NOT NULL,"
                + "type_id VARCHAR(64) NOT NULL,"
                + "losses INT NOT NULL"
                + ")");
        execute("CREATE TABLE IF NOT EXISTS " + table("group_goals") + " ("
                + "goal_id VARCHAR(64) NOT NULL,"
                + "day_start BIGINT NOT NULL,"
                + "progress BIGINT NOT NULL,"
                + "completed TINYINT NOT NULL"
                + ")");
        execute("CREATE TABLE IF NOT EXISTS " + table("risk_offers") + " ("
                + "uuid VARCHAR(36) PRIMARY KEY,"
                + "player_name VARCHAR(32) NOT NULL,"
                + "amount DOUBLE NOT NULL,"
                + "reward_name VARCHAR(128) NOT NULL,"
                + "expires_at BIGINT NOT NULL"
                + ")");
        execute("CREATE TABLE IF NOT EXISTS " + table("pending_cards") + " ("
                + "uuid VARCHAR(36) PRIMARY KEY,"
                + "player_name VARCHAR(32) NOT NULL,"
                + "type_id VARCHAR(64) NOT NULL,"
                + "reward_id VARCHAR(64) NOT NULL,"
                + "symbols TEXT NOT NULL,"
                + "opened_slots TEXT NOT NULL,"
                + "created_at BIGINT NOT NULL"
                + ")");
        execute("CREATE TABLE IF NOT EXISTS " + table("player_stats") + " ("
                + "uuid VARCHAR(36) PRIMARY KEY,"
                + "player_name VARCHAR(32) NOT NULL,"
                + "bought INT NOT NULL,"
                + "opened INT NOT NULL,"
                + "won_money DOUBLE NOT NULL,"
                + "best_win DOUBLE NOT NULL,"
                + "jackpots INT NOT NULL"
                + ")");
        createIndex("idx_purchases_uuid_time", "purchases", "uuid, purchased_at");
        createIndex("idx_opens_uuid_time", "opens", "uuid, opened_at");
        createIndex("idx_daily_claims_uuid_time", "daily_claims", "uuid, claimed_at");
        createIndex("idx_series_symbols_uuid_series", "series_symbols", "uuid, series_id");
        createIndex("idx_series_claims_uuid_series", "series_claims", "uuid, series_id");
        createIndex("uidx_series_symbols_once", "series_symbols", "uuid, series_id, symbol_id");
        createIndex("uidx_series_claims_once", "series_claims", "uuid, series_id");
        createIndex("idx_pass_progress_uuid_season", "pass_progress", "uuid, season");
        createIndex("idx_quest_progress_uuid_day", "quest_progress", "uuid, day_start");
        createIndex("idx_pity_counters_uuid_type", "pity_counters", "uuid, type_id");
        createIndex("idx_group_goals_goal_day", "group_goals", "goal_id, day_start");
        createIndex("idx_rewards_jackpot_time", "rewards_log", "jackpot, created_at");
    }

    private void execute(String sql) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            log(Level.SEVERE, "Datenbankfehler bei Schema-Erstellung", exception);
        }
    }

    private void createIndex(String indexName, String tableName, String columns) {
        String sql = mysql
                ? "CREATE INDEX " + table(indexName) + " ON " + table(tableName) + " (" + columns + ")"
                : "CREATE INDEX IF NOT EXISTS " + table(indexName) + " ON " + table(tableName) + " (" + columns + ")";
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            if (mysql && exception.getErrorCode() == 1061) {
                return;
            }
            log(Level.WARNING, "Datenbankindex konnte nicht angelegt werden: " + indexName, exception);
        }
    }

    public synchronized void recordPurchase(UUID uuid, String playerName, String typeId, double price) {
        ensureStats(uuid, playerName);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table("purchases")
                     + " (uuid, player_name, type_id, price, purchased_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, playerName);
            statement.setString(3, typeId);
            statement.setDouble(4, price);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Kauf konnte nicht gespeichert werden", exception);
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE " + table("player_stats")
                     + " SET player_name = ?, bought = bought + 1 WHERE uuid = ?")) {
            statement.setString(1, playerName);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Kaufstatistik konnte nicht aktualisiert werden", exception);
        }
    }

    public synchronized int countPurchasesSince(UUID uuid, long sinceMillis) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table("purchases")
                     + " WHERE uuid = ? AND purchased_at >= ?")) {
            statement.setString(1, uuid.toString());
            statement.setLong(2, sinceMillis);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Kauflimits konnten nicht geprüft werden", exception);
            return 0;
        }
    }

    public synchronized void recordOpen(UUID uuid, String playerName, String typeId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table("opens")
                     + " (uuid, player_name, type_id, opened_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, playerName);
            statement.setString(3, typeId);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Oeffnung konnte nicht gespeichert werden", exception);
        }
    }

    public synchronized int countOpensSince(UUID uuid, long sinceMillis) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table("opens")
                     + " WHERE uuid = ? AND opened_at >= ?")) {
            statement.setString(1, uuid.toString());
            statement.setLong(2, sinceMillis);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Oeffnungslimits konnten nicht geprueft werden", exception);
            return 0;
        }
    }

    public synchronized void recordDailyClaim(UUID uuid, String playerName, String typeId, int amount) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table("daily_claims")
                     + " (uuid, player_name, type_id, amount, claimed_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, playerName);
            statement.setString(3, typeId);
            statement.setInt(4, amount);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Taegliches Gratis-Los konnte nicht gespeichert werden", exception);
        }
    }

    public synchronized int countDailyClaimsSince(UUID uuid, long sinceMillis) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table("daily_claims")
                     + " WHERE uuid = ? AND claimed_at >= ?")) {
            statement.setString(1, uuid.toString());
            statement.setLong(2, sinceMillis);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Taegliches Gratis-Los konnte nicht geprueft werden", exception);
            return 0;
        }
    }

    public synchronized DailyStreak getDailyStreak(UUID uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT current_streak, best_streak, last_day_start FROM "
                     + table("daily_streaks") + " WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new DailyStreak(
                            resultSet.getInt("current_streak"),
                            resultSet.getInt("best_streak"),
                            resultSet.getLong("last_day_start")
                    );
                }
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Daily-Streak konnte nicht gelesen werden", exception);
        }
        return DailyStreak.empty();
    }

    public synchronized void saveDailyStreak(UUID uuid, String playerName, DailyStreak streak) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM " + table("daily_streaks") + " WHERE uuid = ?")) {
            delete.setString(1, uuid.toString());
            delete.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Daily-Streak konnte nicht aktualisiert werden", exception);
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement("INSERT INTO " + table("daily_streaks")
                     + " (uuid, player_name, current_streak, best_streak, last_day_start) VALUES (?, ?, ?, ?, ?)")) {
            insert.setString(1, uuid.toString());
            insert.setString(2, playerName);
            insert.setInt(3, streak.current());
            insert.setInt(4, streak.best());
            insert.setLong(5, streak.lastDayStart());
            insert.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Daily-Streak konnte nicht gespeichert werden", exception);
        }
    }

    public synchronized boolean hasSeriesSymbol(UUID uuid, String seriesId, String symbolId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id FROM " + table("series_symbols")
                     + " WHERE uuid = ? AND series_id = ? AND symbol_id = ?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, seriesId);
            statement.setString(3, symbolId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Serien-Symbol konnte nicht gelesen werden", exception);
            return false;
        }
    }

    public synchronized boolean collectSeriesSymbol(UUID uuid, String playerName, String seriesId, String symbolId) {
        if (hasSeriesSymbol(uuid, seriesId, symbolId)) {
            return false;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table("series_symbols")
                     + " (uuid, player_name, series_id, symbol_id, collected_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, playerName);
            statement.setString(3, seriesId);
            statement.setString(4, symbolId);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
            return true;
        } catch (SQLException exception) {
            log(Level.SEVERE, "Serien-Symbol konnte nicht gespeichert werden", exception);
            return false;
        }
    }

    public synchronized int countSeriesSymbols(UUID uuid, String seriesId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table("series_symbols")
                     + " WHERE uuid = ? AND series_id = ?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, seriesId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Serien-Fortschritt konnte nicht gelesen werden", exception);
            return 0;
        }
    }

    public synchronized boolean hasSeriesClaim(UUID uuid, String seriesId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id FROM " + table("series_claims")
                     + " WHERE uuid = ? AND series_id = ?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, seriesId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Serien-Abschluss konnte nicht gelesen werden", exception);
            return false;
        }
    }

    public synchronized void markSeriesClaim(UUID uuid, String playerName, String seriesId) {
        if (hasSeriesClaim(uuid, seriesId)) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table("series_claims")
                     + " (uuid, player_name, series_id, claimed_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, playerName);
            statement.setString(3, seriesId);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Serien-Abschluss konnte nicht gespeichert werden", exception);
        }
    }

    public synchronized void savePending(PendingScratchcard pending) {
        deletePending(pending.playerId());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table("pending_cards")
                     + " (uuid, player_name, type_id, reward_id, symbols, opened_slots, created_at)"
                     + " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, pending.playerId().toString());
            statement.setString(2, pending.playerName());
            statement.setString(3, pending.typeId());
            statement.setString(4, pending.rewardId());
            statement.setString(5, String.join("|", pending.symbolRewardIds()));
            statement.setString(6, joinInts(pending.openedIndices()));
            statement.setLong(7, pending.createdAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Offenes Rubellos konnte nicht gespeichert werden", exception);
        }
    }

    public synchronized PendingScratchcard loadPending(UUID uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table("pending_cards") + " WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                String symbols = resultSet.getString("symbols");
                return new PendingScratchcard(
                        uuid,
                        resultSet.getString("player_name"),
                        resultSet.getString("type_id"),
                        resultSet.getString("reward_id"),
                        symbols == null || symbols.isBlank() ? List.of() : List.of(symbols.split("\\|")),
                        parseInts(resultSet.getString("opened_slots")),
                        resultSet.getLong("created_at")
                );
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Offenes Rubellos konnte nicht geladen werden", exception);
            return null;
        }
    }

    public synchronized boolean hasPending(UUID uuid) {
        return loadPending(uuid) != null;
    }

    public synchronized void deletePending(UUID uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table("pending_cards") + " WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Offenes Rubellos konnte nicht entfernt werden", exception);
        }
    }

    public synchronized boolean deletePendingByUuid(UUID uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table("pending_cards") + " WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            log(Level.SEVERE, "Offenes Rubellos konnte nicht entfernt werden", exception);
            return false;
        }
    }

    public synchronized int countPendingScratchcards() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table("pending_cards"))) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        } catch (SQLException exception) {
            log(Level.SEVERE, "Offene Rubellose konnten nicht gezählt werden", exception);
            return 0;
        }
    }

    public synchronized void recordReward(UUID uuid, String playerName, String typeId, Reward reward) {
        recordReward(uuid, playerName, typeId, reward, reward.money(), reward.displayName());
    }

    public synchronized void recordReward(UUID uuid, String playerName, String typeId, Reward reward, double paidMoney, String rewardName) {
        ensureStats(uuid, playerName);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table("rewards_log")
                     + " (uuid, player_name, type_id, reward_id, reward_name, money, jackpot, created_at)"
                     + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, playerName);
            statement.setString(3, typeId);
            statement.setString(4, reward.id());
            statement.setString(5, rewardName);
            statement.setDouble(6, paidMoney);
            statement.setInt(7, reward.broadcast() ? 1 : 0);
            statement.setLong(8, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Gewinn konnte nicht gespeichert werden", exception);
        }

        PlayerStats current = getPlayerStats(uuid, playerName);
        double bestWin = Math.max(current.bestWin(), paidMoney);
        int jackpots = current.jackpots() + (reward.broadcast() ? 1 : 0);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE " + table("player_stats")
                     + " SET player_name = ?, opened = opened + 1, won_money = won_money + ?, best_win = ?, jackpots = ? WHERE uuid = ?")) {
            statement.setString(1, playerName);
            statement.setDouble(2, paidMoney);
            statement.setDouble(3, bestWin);
            statement.setInt(4, jackpots);
            statement.setString(5, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Gewinnstatistik konnte nicht aktualisiert werden", exception);
        }
    }

    public synchronized PlayerStats getPlayerStats(UUID uuid, String fallbackName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table("player_stats") + " WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return readPlayerStats(resultSet);
                }
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Spielerstatistik konnte nicht gelesen werden", exception);
        }
        return PlayerStats.empty(uuid, fallbackName);
    }

    public synchronized ServerStats getServerStats(int topLimit) {
        long totalBought = 0L;
        double totalIncome = 0.0D;
        double totalPaid = 0.0D;

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*), COALESCE(SUM(price), 0) FROM " + table("purchases"))) {
            if (resultSet.next()) {
                totalBought = resultSet.getLong(1);
                totalIncome = resultSet.getDouble(2);
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Serverstatistik konnte nicht gelesen werden", exception);
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COALESCE(SUM(money), 0) FROM " + table("rewards_log"))) {
            if (resultSet.next()) {
                totalPaid = resultSet.getDouble(1);
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Auszahlungsstatistik konnte nicht gelesen werden", exception);
        }

        List<PlayerStats> top = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table("player_stats")
                     + " ORDER BY won_money DESC LIMIT ?")) {
            statement.setInt(1, Math.max(1, topLimit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    top.add(readPlayerStats(resultSet));
                }
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Top-Gewinner konnten nicht gelesen werden", exception);
        }

        return new ServerStats(totalBought, totalIncome, totalPaid, top);
    }

    public synchronized long countTotalOpens() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table("opens"))) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        } catch (SQLException exception) {
            log(Level.SEVERE, "Gesamtzahl geoeffneter Lose konnte nicht gelesen werden", exception);
            return 0L;
        }
    }

    public synchronized boolean isServerGoalCompleted(String goalId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT goal_id FROM " + table("server_goals") + " WHERE goal_id = ?")) {
            statement.setString(1, goalId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Serverziel konnte nicht gelesen werden", exception);
            return false;
        }
    }

    public synchronized void markServerGoalCompleted(String goalId, long openedCount) {
        if (isServerGoalCompleted(goalId)) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table("server_goals")
                     + " (goal_id, opened_count, completed_at) VALUES (?, ?, ?)")) {
            statement.setString(1, goalId);
            statement.setLong(2, openedCount);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Serverziel konnte nicht gespeichert werden", exception);
        }
    }

    public synchronized PassProgress getPassProgress(UUID uuid, String season) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT xp, claimed_levels FROM "
                     + table("pass_progress") + " WHERE uuid = ? AND season = ? ORDER BY xp DESC LIMIT 1")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, season);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new PassProgress(season, resultSet.getInt("xp"), resultSet.getInt("claimed_levels"));
                }
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Pass-Fortschritt konnte nicht gelesen werden", exception);
        }
        return new PassProgress(season, 0, 0);
    }

    public synchronized PassProgress addPassXp(UUID uuid, String playerName, String season, int xpToAdd) {
        PassProgress current = getPassProgress(uuid, season);
        PassProgress updated = new PassProgress(season, Math.max(0, current.xp() + Math.max(0, xpToAdd)), current.claimedLevels());
        savePassProgress(uuid, playerName, updated);
        return updated;
    }

    public synchronized void savePassProgress(UUID uuid, String playerName, PassProgress progress) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM " + table("pass_progress") + " WHERE uuid = ? AND season = ?")) {
            delete.setString(1, uuid.toString());
            delete.setString(2, progress.season());
            delete.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Pass-Fortschritt konnte nicht aktualisiert werden", exception);
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement("INSERT INTO " + table("pass_progress")
                     + " (uuid, player_name, season, xp, claimed_levels) VALUES (?, ?, ?, ?, ?)")) {
            insert.setString(1, uuid.toString());
            insert.setString(2, playerName);
            insert.setString(3, progress.season());
            insert.setInt(4, progress.xp());
            insert.setInt(5, progress.claimedLevels());
            insert.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Pass-Fortschritt konnte nicht gespeichert werden", exception);
        }
    }

    public synchronized QuestProgress getQuestProgress(UUID uuid, String playerName, String questId, String displayName, long dayStart, int target) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT progress, completed FROM " + table("quest_progress")
                     + " WHERE uuid = ? AND quest_id = ? AND day_start = ? ORDER BY progress DESC LIMIT 1")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, questId);
            statement.setLong(3, dayStart);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new QuestProgress(questId, displayName, resultSet.getInt("progress"), target, resultSet.getInt("completed") == 1);
                }
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Quest-Fortschritt konnte nicht gelesen werden", exception);
        }
        return new QuestProgress(questId, displayName, 0, target, false);
    }

    public synchronized QuestProgress addQuestProgress(UUID uuid, String playerName, String questId, String displayName, long dayStart, int target, int amount) {
        QuestProgress current = getQuestProgress(uuid, playerName, questId, displayName, dayStart, target);
        if (current.completed()) {
            return current;
        }
        QuestProgress updated = new QuestProgress(questId, displayName,
                Math.min(Math.max(1, target), current.progress() + Math.max(0, amount)),
                target,
                current.progress() + Math.max(0, amount) >= target);
        saveQuestProgress(uuid, playerName, dayStart, updated);
        return updated;
    }

    public synchronized void saveQuestProgress(UUID uuid, String playerName, long dayStart, QuestProgress progress) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM " + table("quest_progress")
                     + " WHERE uuid = ? AND quest_id = ? AND day_start = ?")) {
            delete.setString(1, uuid.toString());
            delete.setString(2, progress.questId());
            delete.setLong(3, dayStart);
            delete.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Quest-Fortschritt konnte nicht aktualisiert werden", exception);
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement("INSERT INTO " + table("quest_progress")
                     + " (uuid, player_name, quest_id, day_start, progress, completed) VALUES (?, ?, ?, ?, ?, ?)")) {
            insert.setString(1, uuid.toString());
            insert.setString(2, playerName);
            insert.setString(3, progress.questId());
            insert.setLong(4, dayStart);
            insert.setInt(5, progress.progress());
            insert.setInt(6, progress.completed() ? 1 : 0);
            insert.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Quest-Fortschritt konnte nicht gespeichert werden", exception);
        }
    }

    public synchronized int getPityLosses(UUID uuid, String typeId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT losses FROM " + table("pity_counters")
                     + " WHERE uuid = ? AND type_id = ? ORDER BY losses DESC LIMIT 1")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, typeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("losses") : 0;
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Pity-Zaehler konnte nicht gelesen werden", exception);
            return 0;
        }
    }

    public synchronized void setPityLosses(UUID uuid, String typeId, int losses) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM " + table("pity_counters") + " WHERE uuid = ? AND type_id = ?")) {
            delete.setString(1, uuid.toString());
            delete.setString(2, typeId);
            delete.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Pity-Zaehler konnte nicht aktualisiert werden", exception);
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement("INSERT INTO " + table("pity_counters")
                     + " (uuid, type_id, losses) VALUES (?, ?, ?)")) {
            insert.setString(1, uuid.toString());
            insert.setString(2, typeId);
            insert.setInt(3, Math.max(0, losses));
            insert.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Pity-Zaehler konnte nicht gespeichert werden", exception);
        }
    }

    public synchronized GroupGoalProgress getGroupGoalProgress(String goalId, String displayName, long dayStart, long target) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT progress, completed FROM " + table("group_goals")
                     + " WHERE goal_id = ? AND day_start = ? ORDER BY progress DESC LIMIT 1")) {
            statement.setString(1, goalId);
            statement.setLong(2, dayStart);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new GroupGoalProgress(goalId, displayName, resultSet.getLong("progress"), target, resultSet.getInt("completed") == 1);
                }
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Gruppenziel konnte nicht gelesen werden", exception);
        }
        return new GroupGoalProgress(goalId, displayName, 0L, target, false);
    }

    public synchronized GroupGoalProgress addGroupGoalProgress(String goalId, String displayName, long dayStart, long target, long amount) {
        GroupGoalProgress current = getGroupGoalProgress(goalId, displayName, dayStart, target);
        if (current.completed()) {
            return current;
        }
        long progress = Math.min(Math.max(1L, target), current.progress() + Math.max(0L, amount));
        GroupGoalProgress updated = new GroupGoalProgress(goalId, displayName, progress, target, progress >= target);
        saveGroupGoalProgress(dayStart, updated);
        return updated;
    }

    private void saveGroupGoalProgress(long dayStart, GroupGoalProgress progress) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM " + table("group_goals")
                     + " WHERE goal_id = ? AND day_start = ?")) {
            delete.setString(1, progress.goalId());
            delete.setLong(2, dayStart);
            delete.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Gruppenziel konnte nicht aktualisiert werden", exception);
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement("INSERT INTO " + table("group_goals")
                     + " (goal_id, day_start, progress, completed) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, progress.goalId());
            insert.setLong(2, dayStart);
            insert.setLong(3, progress.progress());
            insert.setInt(4, progress.completed() ? 1 : 0);
            insert.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Gruppenziel konnte nicht gespeichert werden", exception);
        }
    }

    public synchronized void saveRiskOffer(RiskOffer offer) {
        deleteRiskOffer(offer.playerId());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement("INSERT INTO " + table("risk_offers")
                     + " (uuid, player_name, amount, reward_name, expires_at) VALUES (?, ?, ?, ?, ?)")) {
            insert.setString(1, offer.playerId().toString());
            insert.setString(2, offer.playerName());
            insert.setDouble(3, offer.amount());
            insert.setString(4, offer.rewardName());
            insert.setLong(5, offer.expiresAt());
            insert.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Risk-Angebot konnte nicht gespeichert werden", exception);
        }
    }

    public synchronized RiskOffer loadRiskOffer(UUID uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table("risk_offers") + " WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new RiskOffer(uuid, resultSet.getString("player_name"), resultSet.getDouble("amount"),
                            resultSet.getString("reward_name"), resultSet.getLong("expires_at"));
                }
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Risk-Angebot konnte nicht gelesen werden", exception);
        }
        return null;
    }

    public synchronized void deleteRiskOffer(UUID uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM " + table("risk_offers") + " WHERE uuid = ?")) {
            delete.setString(1, uuid.toString());
            delete.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Risk-Angebot konnte nicht entfernt werden", exception);
        }
    }

    public synchronized List<String> getLatestJackpots(int limit) {
        List<String> jackpots = new ArrayList<>();
        for (JackpotEntry entry : getLatestJackpotEntries(limit)) {
            jackpots.add(entry.playerName() + " - " + entry.rewardName() + " (" + entry.money() + ")");
        }
        return jackpots;
    }

    public synchronized List<JackpotEntry> getLatestJackpotEntries(int limit) {
        List<JackpotEntry> jackpots = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT player_name, type_id, reward_id, reward_name, money, created_at FROM "
                     + table("rewards_log") + " WHERE jackpot = 1 ORDER BY created_at DESC LIMIT ?")) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    jackpots.add(new JackpotEntry(
                            resultSet.getString("player_name"),
                            resultSet.getString("type_id"),
                            resultSet.getString("reward_id"),
                            resultSet.getString("reward_name"),
                            resultSet.getDouble("money"),
                            resultSet.getLong("created_at")
                    ));
                }
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Jackpot-Statistik konnte nicht gelesen werden", exception);
        }
        return jackpots;
    }

    public synchronized List<RewardHistoryEntry> getPlayerRewardHistory(UUID uuid, int limit) {
        List<RewardHistoryEntry> entries = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT type_id, reward_id, reward_name, money, jackpot, created_at FROM "
                     + table("rewards_log") + " WHERE uuid = ? ORDER BY created_at DESC LIMIT ?")) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(new RewardHistoryEntry(
                            resultSet.getString("type_id"),
                            resultSet.getString("reward_id"),
                            resultSet.getString("reward_name"),
                            resultSet.getDouble("money"),
                            resultSet.getInt("jackpot") == 1,
                            resultSet.getLong("created_at")
                    ));
                }
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Spieler-Historie konnte nicht gelesen werden", exception);
        }
        return entries;
    }

    public boolean isMysql() {
        return mysql;
    }

    public String tablePrefix() {
        return prefix;
    }

    private void ensureStats(UUID uuid, String playerName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement check = connection.prepareStatement("SELECT uuid FROM " + table("player_stats") + " WHERE uuid = ?")) {
            check.setString(1, uuid.toString());
            try (ResultSet resultSet = check.executeQuery()) {
                if (resultSet.next()) {
                    return;
                }
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Spielerstatistik konnte nicht geprüft werden", exception);
            return;
        }

        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement("INSERT INTO " + table("player_stats")
                     + " (uuid, player_name, bought, opened, won_money, best_win, jackpots) VALUES (?, ?, 0, 0, 0, 0, 0)")) {
            insert.setString(1, uuid.toString());
            insert.setString(2, playerName == null ? "Unbekannt" : playerName);
            insert.executeUpdate();
        } catch (SQLException exception) {
            log(Level.SEVERE, "Spielerstatistik konnte nicht angelegt werden", exception);
        }
    }

    private PlayerStats readPlayerStats(ResultSet resultSet) throws SQLException {
        return new PlayerStats(
                UUID.fromString(resultSet.getString("uuid")),
                resultSet.getString("player_name"),
                resultSet.getInt("bought"),
                resultSet.getInt("opened"),
                resultSet.getDouble("won_money"),
                resultSet.getDouble("best_win"),
                resultSet.getInt("jackpots")
        );
    }

    private String joinInts(Set<Integer> values) {
        List<String> strings = new ArrayList<>();
        for (Integer value : values) {
            strings.add(String.valueOf(value));
        }
        return String.join(",", strings);
    }

    private Set<Integer> parseInts(String text) {
        Set<Integer> values = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return values;
        }
        for (String part : text.split(",")) {
            try {
                values.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return values;
    }

    private String table(String name) {
        return prefix + name;
    }

    private void log(Level level, String message, Throwable throwable) {
        plugin.getLogger().log(level, message, throwable);
        if (diagnosticLogger == null) {
            return;
        }
        if (level.intValue() >= Level.SEVERE.intValue()) {
            diagnosticLogger.error(message, throwable);
        } else {
            diagnosticLogger.warning(message, throwable);
        }
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
