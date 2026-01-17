package wiki.creeper.mahjong.api.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import wiki.creeper.mahjong.game.Tile;
import wiki.creeper.mahjong.table.GameTable;

public final class MahjongDiscardEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final GameTable table;
    private final UUID playerId;
    private final Player player;
    private final Tile tile;

    public MahjongDiscardEvent(GameTable table, UUID playerId, Player player, Tile tile) {
        this.table = table;
        this.playerId = playerId;
        this.player = player;
        this.tile = tile;
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

    public Tile getTile() {
        return tile;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
