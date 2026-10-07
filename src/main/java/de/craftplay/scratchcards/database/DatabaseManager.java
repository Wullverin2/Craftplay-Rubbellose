package de.craftplay.scratchcards.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.craftplay.scratchcards.CraftplayScratchcardsPlugin;
import de.craftplay.scratchcards.config.ConfigManager;
import de.craftplay.scratchcards.diagnostic.DiagnosticLogger;
import de.craftplay.scratchcards.model.JackpotEntry;
import de.craftplay.scratchcards.model.PendingScratchcard;
import de.craftplay.scratchcards.model.PlayerStats;
import de.craftplay.scratchcards.model.Reward;
import de.craftplay.scratchcards.model.RewardHistoryEntry;
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
        recordPurchases(uuid, playerName, typeId, price, 1);
    }

    public synchronized boolean recordPurchases(UUID uuid, String playerName, String typeId, double unitPrice, int amount) {
        if (amount <= 0 || !Double.isFinite(unitPrice) || unitPrice < 0.0D) {
            throw new IllegalArgumentException("Kaufmenge und Preis muessen gueltig sein.");
        }
        // Eine Zeile je Los erhaelt die bisherigen Tageszaehler und Statistiken ohne Schema-Aenderung.
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ensureStats(connection, uuid, playerName);
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table("purchases")
                        + " (uuid, player_name, type_id, price, purchased_at) VALUES (?, ?, ?, ?, ?)")) {
                    long purchasedAt = System.currentTimeMillis();
                    for (int index = 0; index < amount; index++) {
                        statement.setString(1, uuid.toString());
                        statement.setString(2, playerName);
                        statement.setString(3, typeId);
                        statement.setDouble(4, unitPrice);
                        statement.setLong(5, purchasedAt);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                try (PreparedStatement statement = connection.prepareStatement("UPDATE " + table("player_stats")
                        + " SET player_name = ?, bought = bought + ? WHERE uuid = ?")) {
                    statement.setString(1, playerName);
                    statement.setInt(2, amount);
                    statement.setString(3, uuid.toString());
                    statement.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            log(Level.SEVERE, "Kauf konnte nicht vollstaendig gespeichert werden", exception);
            return false;
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
        try (Connection connection = dataSource.getConnection()) {
            ensureStats(connection, uuid, playerName);
        } catch (SQLException exception) {
            log(Level.SEVERE, "Spielerstatistik konnte nicht angelegt oder geprueft werden", exception);
        }
    }

    private void ensureStats(Connection connection, UUID uuid, String playerName) throws SQLException {
        try (PreparedStatement check = connection.prepareStatement("SELECT uuid FROM " + table("player_stats") + " WHERE uuid = ?")) {
            check.setString(1, uuid.toString());
            try (ResultSet resultSet = check.executeQuery()) {
                if (resultSet.next()) {
                    return;
                }
            }
        }

        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + table("player_stats")
                     + " (uuid, player_name, bought, opened, won_money, best_win, jackpots) VALUES (?, ?, 0, 0, 0, 0, 0)")) {
            insert.setString(1, uuid.toString());
            insert.setString(2, playerName == null ? "Unbekannt" : playerName);
            insert.executeUpdate();
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
