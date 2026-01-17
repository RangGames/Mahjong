package wiki.creeper.mahjong.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import wiki.creeper.mahjong.table.GameTable;

public final class MahjongTableCreateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final GameTable table;
    private final Player owner;

    public MahjongTableCreateEvent(GameTable table, Player owner) {
        this.table = table;
        this.owner = owner;
    }

    public GameTable getTable() {
        return table;
    }

    public Player getOwner() {
        return owner;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
