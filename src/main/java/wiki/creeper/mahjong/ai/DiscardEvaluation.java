package wiki.creeper.mahjong.ai;

import java.util.List;
import java.util.Objects;
import wiki.creeper.mahjong.game.Tile;
import wiki.creeper.mahjong.game.TileId;

public final class DiscardEvaluation {
    private final Tile tile;
    private final TileId tileId;
    private final int shanten;
    private final UkeireResult ukeire;
    private final int safety;
    private final int expectedValue;
    private final boolean dora;
    private final boolean red;

    public DiscardEvaluation(Tile tile, TileId tileId, int shanten, UkeireResult ukeire,
                             int safety, int expectedValue, boolean dora, boolean red) {
        this.tile = Objects.requireNonNull(tile, "tile");
        this.tileId = Objects.requireNonNull(tileId, "tileId");
        this.shanten = shanten;
        this.ukeire = ukeire == null ? new UkeireResult(0, List.of()) : ukeire;
        this.safety = safety;
        this.expectedValue = expectedValue;
        this.dora = dora;
        this.red = red;
    }

    public Tile getTile() {
        return tile;
    }

    public TileId getTileId() {
        return tileId;
    }

    public int getShanten() {
        return shanten;
    }

    public UkeireResult getUkeire() {
        return ukeire;
    }

    public int getSafety() {
        return safety;
    }

    public int getExpectedValue() {
        return expectedValue;
    }

    public boolean isDora() {
        return dora;
    }

    public boolean isRed() {
        return red;
    }
}
