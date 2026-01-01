package wiki.creeper.mahjong.game;

import java.util.List;

public final class DoraCalculator {

    private DoraCalculator() {
    }

    public static int countDora(Hand hand, List<Tile> indicators, boolean includeRed) {
        int count = 0;
        for (Tile tile : hand.getAllTiles()) {
            TileId id = tile.getId();
            if (includeRed && id.isRed()) {
                count++;
            }
            for (Tile indicator : indicators) {
                TileId dora = nextDora(indicator.getId());
                if (id.getSuit() == dora.getSuit() && id.getRank() == dora.getRank()) {
                    count++;
                }
            }
        }
        return count;
    }

    public static int countRedDora(Hand hand) {
        int count = 0;
        for (Tile tile : hand.getAllTiles()) {
            if (tile.getId().isRed()) {
                count++;
            }
        }
        return count;
    }

    public static TileId nextDora(TileId indicator) {
        if (indicator.getSuit() == TileSuit.HONOR) {
            int rank = indicator.getRank();
            if (rank >= 5) {
                int next = (rank == 7) ? 5 : rank + 1;
                return TileId.of(TileSuit.HONOR, next, false);
            }
            int next = (rank == 4) ? 1 : rank + 1;
            return TileId.of(TileSuit.HONOR, next, false);
        }
        int next = (indicator.getRank() == 9) ? 1 : indicator.getRank() + 1;
        return TileId.of(indicator.getSuit(), next, false);
    }
}
