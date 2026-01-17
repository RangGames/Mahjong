package wiki.creeper.mahjong.rank;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import wiki.creeper.mahjong.api.event.MahjongRankUpdateEvent;
import wiki.creeper.mahjong.game.PlayerState;
import wiki.creeper.mahjong.game.SeatWind;
import wiki.creeper.mahjong.table.GameTable;

public final class RankedManager {
    private final JavaPlugin plugin;
    private final Map<UUID, RankedProfile> profiles = new HashMap<>();
    private RankedSettings settings;
    private File file;

    public RankedManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.settings = RankedSettings.load(plugin);
        this.file = new File(plugin.getDataFolder(), "ranked.yml");
        load();
    }

    public RankedSettings getSettings() {
        return settings;
    }

    public void reloadSettings() {
        this.settings = RankedSettings.load(plugin);
    }

    public RankedProfile getProfile(UUID playerId) {
        return profiles.computeIfAbsent(playerId, id -> new RankedProfile(id, 0.0, 0, 0, 0, 0, 0));
    }

    public void updateOnGameEnd(GameTable table, Map<UUID, Integer> pointsAfter) {
        if (table == null || pointsAfter == null || pointsAfter.isEmpty()) {
            return;
        }
        if (!settings.isEnabled() || !table.isRanked()) {
            return;
        }
        int[] uma = settings.getUmaForLength();
        List<RankEntry> entries = buildEntries(table, pointsAfter);
        if (entries.size() != 4) {
            return;
        }
        Map<UUID, Double> deltas = new HashMap<>();
        Map<UUID, Double> ratings = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            RankEntry entry = entries.get(i);
            double base = (entry.points - settings.getTargetPoints()) / 1000.0;
            double delta = base + uma[i];
            if (i == 0) {
                delta += settings.getOka();
            }
            delta = roundOneDecimal(delta);
            RankedProfile profile = getProfile(entry.playerId);
            profile.applyResult(i + 1, delta);
            deltas.put(entry.playerId, delta);
            ratings.put(entry.playerId, profile.getRating());
        }
        save();
        Bukkit.getPluginManager().callEvent(new MahjongRankUpdateEvent(table, deltas, ratings));
        notifyPlayers(deltas, ratings);
    }

    private List<RankEntry> buildEntries(GameTable table, Map<UUID, Integer> pointsAfter) {
        List<RankEntry> entries = new ArrayList<>();
        for (UUID playerId : table.getPlayers()) {
            PlayerState state = table.getEngine() != null ? table.getEngine().getPlayerState(playerId) : null;
            SeatWind wind = state != null ? state.getSeatWind() : SeatWind.EAST;
            int points = pointsAfter.getOrDefault(playerId, 0);
            entries.add(new RankEntry(playerId, points, wind));
        }
        entries.sort(Comparator
                .comparingInt((RankEntry entry) -> entry.points).reversed()
                .thenComparingInt(entry -> seatOrder(entry.wind)));
        return entries;
    }

    private int seatOrder(SeatWind wind) {
        if (wind == null) {
            return 0;
        }
        switch (wind) {
            case EAST:
                return 0;
            case SOUTH:
                return 1;
            case WEST:
                return 2;
            case NORTH:
            default:
                return 3;
        }
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private void notifyPlayers(Map<UUID, Double> deltas, Map<UUID, Double> ratings) {
        for (Map.Entry<UUID, Double> entry : deltas.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            double delta = entry.getValue();
            double rating = ratings.getOrDefault(entry.getKey(), 0.0);
            player.sendMessage("랭크 포인트 " + formatDelta(delta) + " (현재 " + formatScore(rating) + ")");
        }
    }

    private String formatDelta(double value) {
        String sign = value >= 0 ? "+" : "";
        return sign + formatScore(value);
    }

    private String formatScore(double value) {
        if (Math.abs(value - Math.round(value)) < 0.0001) {
            return Integer.toString((int) Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private void load() {
        profiles.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection section = players.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                double rating = section.getDouble("rating", 0.0);
                int games = section.getInt("games", 0);
                int firsts = section.getInt("firsts", 0);
                int seconds = section.getInt("seconds", 0);
                int thirds = section.getInt("thirds", 0);
                int fourths = section.getInt("fourths", 0);
                profiles.put(id, new RankedProfile(id, rating, games, firsts, seconds, thirds, fourths));
            } catch (IllegalArgumentException ignored) {
                // ignore invalid UUIDs
            }
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection players = config.createSection("players");
        for (RankedProfile profile : profiles.values()) {
            ConfigurationSection section = players.createSection(profile.getPlayerId().toString());
            section.set("rating", profile.getRating());
            section.set("games", profile.getGames());
            section.set("firsts", profile.getFirsts());
            section.set("seconds", profile.getSeconds());
            section.set("thirds", profile.getThirds());
            section.set("fourths", profile.getFourths());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save ranked.yml: " + e.getMessage());
        }
    }

    private static final class RankEntry {
        private final UUID playerId;
        private final int points;
        private final SeatWind wind;

        private RankEntry(UUID playerId, int points, SeatWind wind) {
            this.playerId = playerId;
            this.points = points;
            this.wind = wind;
        }
    }
}
