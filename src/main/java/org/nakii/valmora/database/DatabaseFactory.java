package org.nakii.valmora.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.nakii.valmora.Valmora;

import java.io.File;

public class DatabaseFactory {

    public static DataStore createDataStore(Valmora plugin) {
        FileConfiguration config = plugin.getConfig();
        String type = config.getString("database.type", "sqlite").toLowerCase();
        // HC-003 / HC-005
        int workerThreads = config.getInt("database.worker-threads", 4);
        int ledgerRetentionPerPlayer = config.getInt("economy.ledger-retention-per-player", 10);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("Valmora-Pool");
        // HC-001: pool size — 10 starves large networks, wastes RAM on tiny ones.
        hikariConfig.setMaximumPoolSize(config.getInt("database.pool.maximum-pool-size", 10));

        if (type.equals("mysql")) {
            String host = config.getString("database.mysql.host", "localhost");
            int port = config.getInt("database.mysql.port", 3306);
            String db = config.getString("database.mysql.database", "valmora");
            boolean useSsl = config.getBoolean("database.mysql.use-ssl", false);

            hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=" + useSsl);
            hikariConfig.setUsername(config.getString("database.mysql.username", "root"));
            hikariConfig.setPassword(config.getString("database.mysql.password", ""));
            // HC-002: VPS vs. dedicated-host MySQL tuning.
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", String.valueOf(config.getInt("database.mysql.prep-cache-size", 250)));
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", String.valueOf(config.getInt("database.mysql.prep-cache-sql-limit", 2048)));

        return new SQLDataStore(new HikariDataSource(hikariConfig), true, plugin.getLogger(),
            plugin.getDataFolder(), workerThreads, ledgerRetentionPerPlayer);
        } else {
            // Default to SQLite
            File dbFile = new File(plugin.getDataFolder(), "database.db");
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            // WAL lets readers and the (now infrequent, batched) writer proceed concurrently
            // instead of blocking each other under SQLite's default rollback-journal mode.
            hikariConfig.setConnectionInitSql("PRAGMA journal_mode=WAL");

        return new SQLDataStore(new HikariDataSource(hikariConfig), false, plugin.getLogger(),
            plugin.getDataFolder(), workerThreads, ledgerRetentionPerPlayer);
        }
    }
}
