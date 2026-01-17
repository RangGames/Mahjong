package wiki.creeper.mahjong.api.event;

import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import wiki.creeper.mahjong.game.CallType;
import wiki.creeper.mahjong.game.Tile;
import wiki.creeper.mahjong.table.GameTable;

public final class MahjongCallEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final GameTable table;
    private final UUID playerId;
    private final Player player;
    private final CallType callType;
    private final List<Tile> tiles;
    private final UUID calledFrom;
    private final boolean selfKan;

    public MahjongCallEvent(GameTable table, UUID playerId, Player player, CallType callType,
                            List<Tile> tiles, UUID calledFrom, boolean selfKan) {
        this.table = table;
        this.playerId = playerId;
        this.player = player;
        this.callType = callType;
        this.tiles = tiles == null ? List.of() : List.copyOf(tiles);
        this.calledFrom = calledFrom;
        this.selfKan = selfKan;
    }

    public GameTable getTable() {
        return table;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Player getPlayer() {
        return player;
    }

    public CallType getCallType() {
        return callType;
    }

    public List<Tile> getTiles() {
        return tiles;
    }

    public UUID getCalledFrom() {
        return calledFrom;
    }

    public boolean isSelfKan() {
        return selfKan;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
