package wiki.creeper.mahjong.rank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import wiki.creeper.mahjong.api.event.MahjongRankUpdateEvent;
import wiki.creeper.mahjong.game.PlayerState;
import wiki.creeper.mahjong.game.SeatWind;
import wiki.creeper.mahjong.table.GameTable;

public final class RankedManager {
    private static final RankedTier DEFAULT_TIER = new RankedTier("default", "랭크", 0.0);

    private final JavaPlugin plugin;
    private final RankedDatabase database;
    private final Map<UUID, RankedProfile> profiles = new HashMap<>();
    private RankedSettings settings;
    private List<RankedTier> tiers = new ArrayList<>();

    public RankedManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.settings = RankedSettings.load(plugin);
        this.database = new RankedDatabase(plugin);
        reloadTiers();
        loadProfiles();
    }

    public RankedSettings getSettings() {
        return settings;
    }

    public void reloadSettings() {
        this.settings = RankedSettings.load(plugin);
        reloadTiers();
    }

    public boolean isAvailable() {
        return settings.isEnabled() && database.isAvailable();
    }

    public RankedTier resolveTier(double rating) {
        if (tiers.isEmpty()) {
            return DEFAULT_TIER;
        }
        RankedTier current = tiers.get(0);
        for (RankedTier tier : tiers) {
            if (rating >= tier.getMinRating()) {
                current = tier;
            } else {
                break;
            }
        }
        return current;
    }

    public RankedTier getTier(UUID playerId) {
        RankedProfile profile = profiles.get(playerId);
        if (profile == null) {
            return DEFAULT_TIER;
        }
        return resolveTier(profile.getRating());
    }

    public RankedProfile getProfile(UUID playerId) {
        return profiles.computeIfAbsent(playerId, id -> new RankedProfile(id, null, 0.0, 0, 0, 0, 0, 0));
    }

    public RankedProfile getProfile(Player player) {
        if (player == null) {
            return null;
        }
        RankedProfile profile = getProfile(player.getUniqueId());
        profile.setLastKnownName(player.getName());
        return profile;
    }

    public void updateOnGameEnd(GameTable table, Map<UUID, Integer> pointsAfter) {
        if (table == null || pointsAfter == null || pointsAfter.isEmpty()) {
            return;
        }
        if (!isAvailable() || !table.isRanked()) {
            return;
        }
        int[] uma = settings.getUmaForLength();
        List<RankEntry> entries = buildEntries(table, pointsAfter);
        if (entries.size() != 4) {
            return;
        }
        Map<UUID, Double> deltas = new HashMap<>();
        Map<UUID, Double> ratings = new HashMap<>();
        Map<UUID, RankedTier> tierChanges = new HashMap<>();
        List<RankedProfile> updatedProfiles = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            RankEntry entry = entries.get(i);
            double base = (entry.points - settings.getTargetPoints()) / 1000.0;
            double delta = base + uma[i];
            if (i == 0) {
                delta += settings.getOka();
            }
            delta = roundOneDecimal(delta);
            RankedProfile profile = getProfile(entry.playerId);
            Player online = plugin.getServer().getPlayer(entry.playerId);
            if (online != null) {
                profile.setLastKnownName(online.getName());
            }
            RankedTier beforeTier = resolveTier(profile.getRating());
            profile.applyResult(i + 1, delta);
            RankedTier afterTier = resolveTier(profile.getRating());
            if (!beforeTier.getId().equalsIgnoreCase(afterTier.getId())) {
                tierChanges.put(entry.playerId, afterTier);
            }
            deltas.put(entry.playerId, delta);
            ratings.put(entry.playerId, profile.getRating());
            updatedProfiles.add(profile);
        }
        persistProfilesAsync(updatedProfiles);
        Bukkit.getPluginManager().callEvent(new MahjongRankUpdateEvent(table, deltas, ratings));
        notifyPlayers(deltas, ratings, tierChanges);
    }

    public List<RankedProfile> getTopProfiles(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<RankedProfile> list = new ArrayList<>(profiles.values());
        list.sort(Comparator.comparingDouble(RankedProfile::getRating).reversed()
                .thenComparing(Comparator.comparingInt(RankedProfile::getGames).reversed()));
        if (list.size() <= limit) {
            return list;
        }
        return list.subList(0, limit);
    }

    public void save() {
        if (!database.isAvailable()) {
            return;
        }
        database.saveProfiles(new ArrayList<>(profiles.values()));
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

    private void notifyPlayers(Map<UUID, Double> deltas, Map<UUID, Double> ratings, Map<UUID, RankedTier> tierChanges) {
        for (Map.Entry<UUID, Double> entry : deltas.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            double delta = entry.getValue();
            double rating = ratings.getOrDefault(entry.getKey(), 0.0);
            RankedTier tier = resolveTier(rating);
            player.sendMessage("랭크 포인트 " + formatDelta(delta) + " (현재 " + formatScore(rating) + ", " + tier.getName() + ")");
            RankedTier changed = tierChanges.get(entry.getKey());
            if (changed != null) {
                player.sendMessage("랭크가 변경됐어요: " + changed.getName());
            }
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
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private void loadProfiles() {
        profiles.clear();
        if (!database.isAvailable()) {
            if (settings.isEnabled()) {
                plugin.getLogger().warning("Ranked database is unavailable. Ranked data will not persist.");
            }
            return;
        }
        for (RankedProfile profile : database.loadAllProfiles()) {
            profiles.put(profile.getPlayerId(), profile);
        }
    }

    private void persistProfilesAsync(List<RankedProfile> profilesToSave) {
        if (!database.isAvailable() || profilesToSave == null || profilesToSave.isEmpty()) {
            return;
        }
        List<RankedProfile> snapshot = new ArrayList<>(profilesToSave);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.saveProfiles(snapshot));
    }

    private void reloadTiers() {
        List<RankedTier> loaded = new ArrayList<>();
        for (Map<?, ?> entry : plugin.getConfig().getMapList("ranked.tiers")) {
            Object idObj = entry.get("id");
            Object nameObj = entry.get("name");
            Object minObj = entry.get("minRating");
            if (idObj == null || nameObj == null || minObj == null) {
                continue;
            }
            String id = idObj.toString();
            String name = nameObj.toString();
            double minRating;
            if (minObj instanceof Number) {
                minRating = ((Number) minObj).doubleValue();
            } else {
                try {
                    minRating = Double.parseDouble(minObj.toString());
                } catch (NumberFormatException e) {
                    continue;
                }
            }
            loaded.add(new RankedTier(id, name, minRating));
        }
        if (loaded.isEmpty()) {
            loaded.add(DEFAULT_TIER);
        }
        loaded.sort(Comparator.comparingDouble(RankedTier::getMinRating));
        tiers = loaded;
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
