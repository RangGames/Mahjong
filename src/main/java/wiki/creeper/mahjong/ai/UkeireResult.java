package wiki.creeper.mahjong.ai;

import java.util.List;
import wiki.creeper.mahjong.game.TileId;

public final class UkeireResult {
    private final int total;
    private final List<TileId> effectiveTiles;

    public UkeireResult(int total, List<TileId> effectiveTiles) {
        this.total = total;
        this.effectiveTiles = effectiveTiles == null ? List.of() : List.copyOf(effectiveTiles);
    }

    public int getTotal() {
        return total;
    }

    public List<TileId> getEffectiveTiles() {
        return effectiveTiles;
    }
}
