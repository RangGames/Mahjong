package wiki.creeper.mahjong.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import wiki.creeper.mahjong.game.GameRules;
import wiki.creeper.mahjong.game.RoundState;
import wiki.creeper.mahjong.table.GameTable;

public final class MahjongGameStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final GameTable table;
    private final long seed;
    private final GameRules rules;
    private final RoundState roundState;

    public MahjongGameStartEvent(GameTable table, long seed, GameRules rules, RoundState roundState) {
        this.table = table;
        this.seed = seed;
        this.rules = rules;
        this.roundState = roundState;
    }

    public GameTable getTable() {
        return table;
    }

    public long getSeed() {
        return seed;
    }

    public GameRules getRules() {
        return rules;
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
