package wiki.creeper.mahjong.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import wiki.creeper.mahjong.table.GameTable;

public final class MahjongTableJoinEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final GameTable table;
    private final Player player;
    private final boolean spectator;

    public MahjongTableJoinEvent(GameTable table, Player player, boolean spectator) {
        this.table = table;
        this.player = player;
        this.spectator = spectator;
    }

    public GameTable getTable() {
        return table;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean isSpectator() {
        return spectator;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
