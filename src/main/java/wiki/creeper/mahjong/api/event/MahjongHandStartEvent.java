package wiki.creeper.mahjong.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import wiki.creeper.mahjong.game.RoundState;
import wiki.creeper.mahjong.table.GameTable;

public final class MahjongHandStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final GameTable table;
    private final RoundState roundState;

    public MahjongHandStartEvent(GameTable table, RoundState roundState) {
        this.table = table;
        this.roundState = roundState;
    }

    public GameTable getTable() {
        return table;
    }

    public RoundState getRoundState() {
        return roundState;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
