package wiki.creeper.mahjong.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TileIdTest {
    @Test
    void parsesSuitedAndHonorTiles() {
        TileId man = TileId.parse("m1");
        assertEquals(TileSuit.MAN, man.getSuit());
        assertEquals(1, man.getRank());
        assertFalse(man.isRed());
        assertEquals("m1", man.toShortString());

        TileId honor = TileId.parse("z7");
        assertEquals(TileSuit.HONOR, honor.getSuit());
        assertTrue(honor.isHonor());
        assertEquals("z7", honor.toShortString());
    }

    @Test
    void parsesRedFives() {
        TileId redZero = TileId.parse("p0");
        assertTrue(redZero.isRed());
        assertEquals(5, redZero.getRank());
        assertEquals("p0", redZero.toShortString());

        TileId redSuffix = TileId.parse("s5r");
        assertTrue(redSuffix.isRed());
        assertEquals(5, redSuffix.getRank());
        assertEquals("s0", redSuffix.toShortString());
    }

    @Test
    void rejectsInvalidIds() {
        assertThrows(IllegalArgumentException.class, () -> TileId.parse("m10"));
        assertThrows(IllegalArgumentException.class, () -> TileId.parse("z0"));
        assertThrows(IllegalArgumentException.class, () -> TileId.parse("x1"));
    }
}
