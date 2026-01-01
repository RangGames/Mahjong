package wiki.creeper.mahjong.ai;

import java.util.UUID;
import wiki.creeper.mahjong.game.GameState;
import wiki.creeper.mahjong.game.TileId;

public final class TurnContext {
    private final String lastAction;
    private final GameState state;
    private final TileId lastDiscard;
    private final UUID lastDiscarder;
    private final boolean callWindow;
    private final int remainingSeconds;

    public TurnContext(String lastAction, GameState state, TileId lastDiscard, UUID lastDiscarder,
                       boolean callWindow, int remainingSeconds) {
        this.lastAction = lastAction;
        this.state = state;
        this.lastDiscard = lastDiscard;
        this.lastDiscarder = lastDiscarder;
        this.callWindow = callWindow;
        this.remainingSeconds = remainingSeconds;
    }

    public String getLastAction() {
        return lastAction;
    }

    public GameState getState() {
        return state;
    }

    public TileId getLastDiscard() {
        return lastDiscard;
    }

    public UUID getLastDiscarder() {
        return lastDiscarder;
    }

    public boolean isCallWindow() {
        return callWindow;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }
}
