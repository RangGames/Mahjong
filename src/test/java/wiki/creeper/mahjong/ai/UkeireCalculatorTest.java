package wiki.creeper.mahjong.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import wiki.creeper.mahjong.game.Tile;
import wiki.creeper.mahjong.game.TileId;

class UkeireCalculatorTest {
    @Test
    void effectiveTilesForSimpleTenpai() {
        List<Tile> tiles = tiles(
                "m1", "m2", "m3",
                "m4", "m5", "m6",
                "p1", "p2", "p3",
                "s7", "s8",
                "z1", "z1"
        );
        int baseShanten = ShantenCalculator.calculate(tiles, 0);
        assertEquals(0, baseShanten);

        int[] remainingCounts = remainingCounts(tiles);
        UkeireResult result = UkeireCalculator.calculate(tiles, 0, baseShanten, remainingCounts);

        assertEquals(8, result.getTotal());
        Set<TileId> expected = Set.of(TileId.parse("s6"), TileId.parse("s9"));
        assertEquals(expected, new HashSet<>(result.getEffectiveTiles()));
        assertTrue(result.getEffectiveTiles().size() <= expected.size());
    }

    private static int[] remainingCounts(List<Tile> tiles) {
        int[] remaining = new int[34];
        Arrays.fill(remaining, 4);
        int[] used = TileCounter.countTiles(tiles);
        for (int i = 0; i < remaining.length; i++) {
            remaining[i] = Math.max(0, remaining[i] - used[i]);
        }
        return remaining;
    }

    private static List<Tile> tiles(String... ids) {
        List<Tile> tiles = new ArrayList<>();
        int instanceId = 0;
        for (String id : ids) {
            tiles.add(new Tile(TileId.parse(id), instanceId++));
        }
        return tiles;
    }
}
