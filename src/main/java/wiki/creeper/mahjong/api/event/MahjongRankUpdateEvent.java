package wiki.creeper.mahjong.api.event;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import wiki.creeper.mahjong.table.GameTable;

public final class MahjongRankUpdateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final GameTable table;
    private final Map<UUID, Double> deltas;
    private final Map<UUID, Double> ratings;

    public MahjongRankUpdateEvent(GameTable table, Map<UUID, Double> deltas, Map<UUID, Double> ratings) {
        this.table = table;
        this.deltas = deltas == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(deltas));
        this.ratings = ratings == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(ratings));
    }

    public GameTable getTable() {
        return table;
    }

    public Map<UUID, Double> getDeltas() {
        return deltas;
    }

    public Map<UUID, Double> getRatings() {
        return ratings;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
