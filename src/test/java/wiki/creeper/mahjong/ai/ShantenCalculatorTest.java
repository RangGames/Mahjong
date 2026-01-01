package wiki.creeper.mahjong.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import wiki.creeper.mahjong.game.Tile;
import wiki.creeper.mahjong.game.TileId;

class ShantenCalculatorTest {
    @Test
    void standardCompleteHandIsMinusOne() {
        List<Tile> tiles = tiles(
                "m1", "m2", "m3",
                "m4", "m5", "m6",
                "p1", "p2", "p3",
                "s7", "s8", "s9",
                "z1", "z1"
        );
        assertEquals(-1, ShantenCalculator.calculate(tiles, 0));
    }

    @Test
    void standardTenpaiIsZero() {
        List<Tile> tiles = tiles(
                "m1", "m2", "m3",
                "m4", "m5", "m6",
                "p1", "p2", "p3",
                "s7", "s8",
                "z1", "z1"
        );
        assertEquals(0, ShantenCalculator.calculate(tiles, 0));
    }

    @Test
    void chiitoiHandsAreHandled() {
        List<Tile> complete = tiles(
                "m1", "m1",
                "m2", "m2",
                "m3", "m3",
                "m4", "m4",
                "p5", "p5",
                "s6", "s6",
                "z1", "z1"
        );
        assertEquals(-1, ShantenCalculator.calculate(complete, 0));

        List<Tile> tenpai = tiles(
                "m1", "m1",
                "m2", "m2",
                "m3", "m3",
                "m4", "m4",
                "p5", "p5",
                "s6", "s6",
                "z1"
        );
        assertEquals(0, ShantenCalculator.calculate(tenpai, 0));
    }

    @Test
    void kokushiHandsAreHandled() {
        List<Tile> tenpai = tiles(
                "m1", "m9",
                "p1", "p9",
                "s1", "s9",
                "z1", "z2", "z3", "z4", "z5", "z6", "z7"
        );
        assertEquals(0, ShantenCalculator.calculate(tenpai, 0));

        List<Tile> complete = tiles(
                "m1", "m1",
                "m9",
                "p1", "p9",
                "s1", "s9",
                "z1", "z2", "z3", "z4", "z5", "z6", "z7"
        );
        assertEquals(-1, ShantenCalculator.calculate(complete, 0));
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
