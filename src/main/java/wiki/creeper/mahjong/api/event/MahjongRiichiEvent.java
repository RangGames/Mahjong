package wiki.creeper.mahjong.api.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import wiki.creeper.mahjong.table.GameTable;

public final class MahjongRiichiEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final GameTable table;
    private final UUID playerId;
    private final Player player;

    public MahjongRiichiEvent(GameTable table, UUID playerId, Player player) {
        this.table = table;
        this.playerId = playerId;
        this.player = player;
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

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
