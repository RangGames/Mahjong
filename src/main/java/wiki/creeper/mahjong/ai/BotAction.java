package wiki.creeper.mahjong.ai;

import java.util.Objects;
import wiki.creeper.mahjong.game.CallType;
import wiki.creeper.mahjong.game.Tile;

public final class BotAction {
    public enum Type {
        DISCARD,
        CALL,
        RIICHI,
        WIN
    }

    private final Type type;
    private final Tile discardTile;
    private final CallType callType;
    private final int chiIndex;
    private final boolean tsumo;

    private BotAction(Type type, Tile discardTile, CallType callType, int chiIndex, boolean tsumo) {
        this.type = Objects.requireNonNull(type, "type");
        this.discardTile = discardTile;
        this.callType = callType;
        this.chiIndex = chiIndex;
        this.tsumo = tsumo;
    }

    public static BotAction discard(Tile tile) {
        return new BotAction(Type.DISCARD, Objects.requireNonNull(tile, "tile"), null, 0, false);
    }

    public static BotAction call(CallType callType, int chiIndex) {
        return new BotAction(Type.CALL, null, Objects.requireNonNull(callType, "callType"), chiIndex, false);
    }

    public static BotAction riichi() {
        return new BotAction(Type.RIICHI, null, null, 0, false);
    }

    public static BotAction win(boolean tsumo) {
        return new BotAction(Type.WIN, null, null, 0, tsumo);
    }

    public Type getType() {
        return type;
    }

    public Tile getDiscardTile() {
        return discardTile;
    }

    public CallType getCallType() {
        return callType;
    }

    public int getChiIndex() {
        return chiIndex;
    }

    public boolean isTsumo() {
        return tsumo;
    }
}
