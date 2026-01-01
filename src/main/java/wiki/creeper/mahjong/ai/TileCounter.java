package wiki.creeper.mahjong.ai;

import java.util.Arrays;
import java.util.List;
import wiki.creeper.mahjong.game.Tile;
import wiki.creeper.mahjong.game.TileId;
import wiki.creeper.mahjong.game.TileSuit;

public final class TileCounter {
    private static final TileId[] INDEX_TO_ID = buildIndexLookup();

    private TileCounter() {
    }

    public static int[] countTiles(List<Tile> tiles) {
        int[] counts = new int[34];
        if (tiles == null) {
            return counts;
        }
        for (Tile tile : tiles) {
            if (tile == null) {
                continue;
            }
            counts[tileIndex(tile.getId())]++;
        }
        return counts;
    }

    public static int[] countTileIds(List<TileId> ids) {
        int[] counts = new int[34];
        if (ids == null) {
            return counts;
        }
        for (TileId id : ids) {
            if (id == null) {
                continue;
            }
            counts[tileIndex(id)]++;
        }
        return counts;
    }

    public static int[] buildRemainingCounts(PublicState publicState, PrivateState privateState) {
        int[] counts = new int[34];
        Arrays.fill(counts, 4);
        if (publicState != null) {
            subtractTileIds(counts, publicState.getDoraIndicators());
            for (List<TileId> ids : publicState.getDiscards().values()) {
                subtractTileIds(counts, ids);
            }
            for (List<TileId> ids : publicState.getMeldTiles().values()) {
                subtractTileIds(counts, ids);
            }
        }
        if (privateState != null) {
            subtractTiles(counts, privateState.getConcealed());
        }
        return counts;
    }

    public static int[] buildRemainingCountsFromPrivate(PrivateState privateState) {
        int[] counts = new int[34];
        Arrays.fill(counts, 4);
        if (privateState == null) {
            return counts;
        }
        subtractTiles(counts, privateState.getConcealed());
        for (Tile tile : privateState.getMelds().stream().flatMap(meld -> meld.getTiles().stream()).toList()) {
            if (tile != null) {
                counts[tileIndex(tile.getId())] = Math.max(0, counts[tileIndex(tile.getId())] - 1);
            }
        }
        return counts;
    }

    public static TileId normalize(TileId id) {
        if (id == null) {
            return null;
        }
        if (!id.isRed()) {
            return id;
        }
        return TileId.of(id.getSuit(), id.getRank(), false);
    }

    public static int tileIndex(TileId id) {
        TileId normalized = normalize(id);
        if (normalized == null) {
            return 0;
        }
        switch (normalized.getSuit()) {
            case MAN:
                return normalized.getRank() - 1;
            case PIN:
                return 9 + normalized.getRank() - 1;
            case SOU:
                return 18 + normalized.getRank() - 1;
            case HONOR:
            default:
                return 27 + normalized.getRank() - 1;
        }
    }

    public static TileId tileIdFromIndex(int index) {
        if (index < 0 || index >= INDEX_TO_ID.length) {
            return INDEX_TO_ID[0];
        }
        return INDEX_TO_ID[index];
    }

    private static void subtractTiles(int[] counts, List<Tile> tiles) {
        if (tiles == null) {
            return;
        }
        for (Tile tile : tiles) {
            if (tile == null) {
                continue;
            }
            int idx = tileIndex(tile.getId());
            counts[idx] = Math.max(0, counts[idx] - 1);
        }
    }

    private static void subtractTileIds(int[] counts, List<TileId> ids) {
        if (ids == null) {
            return;
        }
        for (TileId id : ids) {
            if (id == null) {
                continue;
            }
            int idx = tileIndex(id);
            counts[idx] = Math.max(0, counts[idx] - 1);
        }
    }

    private static TileId[] buildIndexLookup() {
        TileId[] ids = new TileId[34];
        int idx = 0;
        for (TileSuit suit : TileSuit.values()) {
            int max = suit == TileSuit.HONOR ? 7 : 9;
            for (int rank = 1; rank <= max; rank++) {
                ids[idx++] = TileId.of(suit, rank, false);
            }
        }
        return ids;
    }
}
