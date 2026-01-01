package wiki.creeper.mahjong.ai;

import java.util.ArrayList;
import java.util.List;
import wiki.creeper.mahjong.game.Tile;
import wiki.creeper.mahjong.game.TileId;

public final class UkeireCalculator {
    private UkeireCalculator() {
    }

    public static UkeireResult calculate(List<Tile> tiles, int openMelds, int baseShanten, int[] remainingCounts) {
        if (tiles == null || remainingCounts == null) {
            return new UkeireResult(0, List.of());
        }
        List<TileId> effective = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < remainingCounts.length; i++) {
            int remaining = remainingCounts[i];
            if (remaining <= 0) {
                continue;
            }
            TileId id = TileCounter.tileIdFromIndex(i);
            if (isEffective(tiles, openMelds, baseShanten, id)) {
                effective.add(id);
                total += remaining;
            }
        }
        return new UkeireResult(total, effective);
    }

    private static boolean isEffective(List<Tile> tiles, int openMelds, int baseShanten, TileId drawId) {
        List<Tile> test = new ArrayList<>(tiles);
        test.add(new Tile(drawId, -1));
        if (baseShanten <= 0) {
            int shanten = ShantenCalculator.calculate(test, openMelds);
            return shanten < 0;
        }
        int next = ShantenCalculator.minShantenAfterDiscard(test, openMelds);
        return next < baseShanten;
    }
}
