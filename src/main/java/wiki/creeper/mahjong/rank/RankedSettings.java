package wiki.creeper.mahjong.rank;

import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;
import wiki.creeper.mahjong.game.GameRules;
import wiki.creeper.mahjong.game.SeatWind;

public final class RankedSettings {
    private static final int[] UMA_EAST_DEFAULT = new int[] {10, 5, -5, -10};
    private static final int[] UMA_SOUTH_DEFAULT = new int[] {20, 10, -10, -20};

    private final boolean enabled;
    private final boolean allowBots;
    private final boolean allowCoach;
    private final GameRules rules;
    private final SeatWind length;
    private final int startingPoints;
    private final int targetPoints;
    private final int oka;
    private final int[] umaEast;
    private final int[] umaSouth;

    public RankedSettings(boolean enabled, boolean allowBots, boolean allowCoach, GameRules rules, SeatWind length,
                          int startingPoints, int targetPoints, int oka, int[] umaEast, int[] umaSouth) {
        this.enabled = enabled;
        this.allowBots = allowBots;
        this.allowCoach = allowCoach;
        this.rules = rules;
        this.length = length;
        this.startingPoints = startingPoints;
        this.targetPoints = targetPoints;
        this.oka = oka;
        this.umaEast = umaEast == null ? UMA_EAST_DEFAULT.clone() : umaEast.clone();
        this.umaSouth = umaSouth == null ? UMA_SOUTH_DEFAULT.clone() : umaSouth.clone();
    }

    public static RankedSettings load(JavaPlugin plugin) {
        boolean enabled = plugin.getConfig().getBoolean("ranked.enabled", true);
        boolean allowBots = plugin.getConfig().getBoolean("ranked.allowBots", false);
        boolean allowCoach = plugin.getConfig().getBoolean("ranked.allowCoach", false);
        boolean redDora = plugin.getConfig().getBoolean("ranked.rules.redDora", true);
        boolean openTanyao = plugin.getConfig().getBoolean("ranked.rules.openTanyao", true);
        boolean ippatsu = plugin.getConfig().getBoolean("ranked.rules.ippatsu", true);
        boolean uraDora = plugin.getConfig().getBoolean("ranked.rules.uraDora", true);
        GameRules rules = new GameRules(redDora, openTanyao, ippatsu, uraDora);
        SeatWind length = parseLength(plugin.getConfig().getString("ranked.length", "SOUTH"));
        int startingPoints = plugin.getConfig().getInt("ranked.startingPoints", 25000);
        int targetPoints = plugin.getConfig().getInt("ranked.targetPoints", 30000);
        int oka = plugin.getConfig().getInt("ranked.oka", 20);
        int[] umaEast = parseUma(plugin.getConfig().getIntegerList("ranked.uma.east"), UMA_EAST_DEFAULT);
        int[] umaSouth = parseUma(plugin.getConfig().getIntegerList("ranked.uma.south"), UMA_SOUTH_DEFAULT);
        return new RankedSettings(enabled, allowBots, allowCoach, rules, length, startingPoints, targetPoints, oka, umaEast, umaSouth);
    }

    private static SeatWind parseLength(String value) {
        if (value == null) {
            return SeatWind.SOUTH;
        }
        String key = value.trim().toUpperCase();
        switch (key) {
            case "EAST":
            case "TONPUU":
                return SeatWind.EAST;
            case "SOUTH":
            case "HANCHAN":
            default:
                return SeatWind.SOUTH;
        }
    }

    private static int[] parseUma(List<Integer> values, int[] fallback) {
        if (values == null || values.size() != 4) {
            return fallback.clone();
        }
        int[] result = new int[4];
        for (int i = 0; i < 4; i++) {
            Integer value = values.get(i);
            result[i] = value == null ? fallback[i] : value;
        }
        return result;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAllowBots() {
        return allowBots;
    }

    public boolean isAllowCoach() {
        return allowCoach;
    }

    public GameRules getRules() {
        return rules;
    }

    public SeatWind getLength() {
        return length;
    }

    public int getStartingPoints() {
        return startingPoints;
    }

    public int getTargetPoints() {
        return targetPoints;
    }

    public int getOka() {
        return oka;
    }

    public int[] getUmaForLength() {
        return length == SeatWind.EAST ? umaEast.clone() : umaSouth.clone();
    }
}
