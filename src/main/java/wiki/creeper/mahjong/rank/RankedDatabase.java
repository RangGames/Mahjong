package wiki.creeper.mahjong.rank;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;

final class RankedDatabase {
    private final JavaPlugin plugin;
    private final boolean enabled;
    private final String jdbcUrl;
    private final String user;
    private final String password;
    private final String tableName;
    private boolean available;

    RankedDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("database.enabled", true);
        this.jdbcUrl = resolveJdbcUrl(plugin);
        this.user = plugin.getConfig().getString("database.user", "root");
        this.password = plugin.getConfig().getString("database.password", "");
        this.tableName = plugin.getConfig().getString("database.table", "mahjong_ranked_profiles");
        init();
    }

    boolean isAvailable() {
        return available;
    }

    List<RankedProfile> loadAllProfiles() {
        if (!available) {
            return List.of();
        }
        List<RankedProfile> loaded = new ArrayList<>();
        String sql = "SELECT player_uuid, player_name, rating, games, firsts, seconds, thirds, fourths FROM " + tableName;
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                UUID playerId;
                try {
                    playerId = UUID.fromString(rs.getString("player_uuid"));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                String name = rs.getString("player_name");
                double rating = rs.getDouble("rating");
                int games = rs.getInt("games");
                int firsts = rs.getInt("firsts");
                int seconds = rs.getInt("seconds");
                int thirds = rs.getInt("thirds");
                int fourths = rs.getInt("fourths");
                loaded.add(new RankedProfile(playerId, name, rating, games, firsts, seconds, thirds, fourths));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load ranked profiles: " + e.getMessage());
        }
        return loaded;
    }

    RankedProfile loadProfile(UUID playerId) {
        if (!available || playerId == null) {
            return null;
        }
        String sql = "SELECT player_uuid, player_name, rating, games, firsts, seconds, thirds, fourths"
                + " FROM " + tableName + " WHERE player_uuid = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String name = rs.getString("player_name");
                double rating = rs.getDouble("rating");
                int games = rs.getInt("games");
                int firsts = rs.getInt("firsts");
                int seconds = rs.getInt("seconds");
                int thirds = rs.getInt("thirds");
                int fourths = rs.getInt("fourths");
                return new RankedProfile(playerId, name, rating, games, firsts, seconds, thirds, fourths);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load ranked profile: " + e.getMessage());
            return null;
        }
    }

    void saveProfiles(Collection<RankedProfile> profiles) {
        if (!available || profiles == null || profiles.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO " + tableName
                + " (player_uuid, player_name, rating, games, firsts, seconds, thirds, fourths)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), rating = VALUES(rating),"
                + " games = VALUES(games), firsts = VALUES(firsts), seconds = VALUES(seconds),"
                + " thirds = VALUES(thirds), fourths = VALUES(fourths)";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (RankedProfile profile : profiles) {
                statement.setString(1, profile.getPlayerId().toString());
                statement.setString(2, profile.getLastKnownName());
                statement.setDouble(3, profile.getRating());
                statement.setInt(4, profile.getGames());
                statement.setInt(5, profile.getFirsts());
                statement.setInt(6, profile.getSeconds());
                statement.setInt(7, profile.getThirds());
                statement.setInt(8, profile.getFourths());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save ranked profiles: " + e.getMessage());
        }
    }

    private void init() {
        if (!enabled) {
            available = false;
            return;
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("MySQL driver not found: " + e.getMessage());
            available = false;
            return;
        }
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + tableName + " ("
                    + "player_uuid VARCHAR(36) PRIMARY KEY,"
                    + "player_name VARCHAR(16),"
                    + "rating DOUBLE NOT NULL DEFAULT 0,"
                    + "games INT NOT NULL DEFAULT 0,"
                    + "firsts INT NOT NULL DEFAULT 0,"
                    + "seconds INT NOT NULL DEFAULT 0,"
                    + "thirds INT NOT NULL DEFAULT 0,"
                    + "fourths INT NOT NULL DEFAULT 0,"
                    + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                    + ")");
            available = true;
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to init ranked database: " + e.getMessage());
            available = false;
        }
    }

    private Connection openConnection() throws SQLException {
        if (user == null || user.isBlank()) {
            return DriverManager.getConnection(jdbcUrl);
        }
        return DriverManager.getConnection(jdbcUrl, user, password);
    }

    private static String resolveJdbcUrl(JavaPlugin plugin) {
        String jdbcUrl = plugin.getConfig().getString("database.jdbcUrl");
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            return jdbcUrl;
        }
        String host = plugin.getConfig().getString("database.host", "localhost");
        int port = plugin.getConfig().getInt("database.port", 3306);
        String database = plugin.getConfig().getString("database.database", "mahjong");
        String params = plugin.getConfig().getString("database.params", "useSSL=false&allowPublicKeyRetrieval=true");
        StringBuilder builder = new StringBuilder("jdbc:mysql://").append(host).append(":").append(port).append("/").append(database);
        if (params != null && !params.isBlank()) {
            if (!params.startsWith("?")) {
                builder.append("?");
            }
            builder.append(params);
        }
        return builder.toString();
    }
}
