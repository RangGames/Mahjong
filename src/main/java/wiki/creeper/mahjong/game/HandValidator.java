package wiki.creeper.mahjong.game;

import java.util.List;

public final class HandValidator {

    private static final List<TileId> ALL_TILE_IDS = buildAllTileIds();

    private HandValidator() {
    }

    public static boolean isComplete(Hand hand) {
        if (hand == null) {
            return false;
        }
        if (isThirteenOrphans(hand) || isSevenPairs(hand)) {
            return true;
        }
        return !HandAnalyzer.analyzeAllStandard(hand).isEmpty();
    }

    public static boolean isCompleteWith(Hand hand, Tile tile) {
        if (hand == null || tile == null) {
            return false;
        }
        Hand temp = new Hand();
        for (Tile existing : hand.getConcealed()) {
            temp.addTile(existing);
        }
        for (Meld meld : hand.getMelds()) {
            temp.addMeld(meld);
        }
        temp.addTile(tile);
        return isComplete(temp);
    }

    public static boolean isComplete(List<Tile> tiles) {
        return isStandardComplete(tiles) || isSevenPairs(tiles) || isThirteenOrphans(tiles);
    }

    public static boolean isStandardComplete(List<Tile> tiles) {
        if (tiles == null || tiles.size() % 3 != 2) {
            return false;
        }
        int[] counts = toCounts(tiles);
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] >= 2) {
                counts[i] -= 2;
                if (canFormMelds(counts)) {
                    counts[i] += 2;
                    return true;
                }
                counts[i] += 2;
            }
        }
        return false;
    }

    public static boolean isTenpai(List<Tile> tiles) {
        if (tiles == null || tiles.size() % 3 != 1) {
            return false;
        }
        for (TileId id : ALL_TILE_IDS) {
            List<Tile> test = new java.util.ArrayList<>(tiles);
            test.add(new Tile(id, -1));
            if (isComplete(test)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTenpai(Hand hand) {
        if (hand == null) {
            return false;
        }
        List<Tile> concealed = hand.getConcealed();
        if (concealed.size() % 3 != 1) {
            return false;
        }
        for (TileId id : ALL_TILE_IDS) {
            Tile testTile = new Tile(id, -1);
            if (isCompleteWith(hand, testTile)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSevenPairs(Hand hand) {
        if (hand == null || !hand.getMelds().isEmpty()) {
            return false;
        }
        return isSevenPairs(hand.getConcealed());
    }

    public static boolean isThirteenOrphans(Hand hand) {
        if (hand == null || !hand.getMelds().isEmpty()) {
            return false;
        }
        return isThirteenOrphans(hand.getConcealed());
    }

    public static boolean isSevenPairs(List<Tile> tiles) {
        if (tiles == null || tiles.size() != 14) {
            return false;
        }
        int[] counts = toCounts(tiles);
        int pairs = 0;
        for (int count : counts) {
            if (count == 2) {
                pairs++;
            } else if (count != 0) {
                return false;
            }
        }
        return pairs == 7;
    }

    public static boolean isThirteenOrphans(List<Tile> tiles) {
        if (tiles == null || tiles.size() != 14) {
            return false;
        }
        int[] counts = toCounts(tiles);
        int[] required = new int[] {
                0, 8, 9, 17, 18, 26, // 1/9 man,pin,sou
                27, 28, 29, 30, 31, 32, 33 // honors 1-7
        };
        boolean hasPair = false;
        for (int idx : required) {
            if (counts[idx] == 0) {
                return false;
            }
            if (counts[idx] >= 2) {
                hasPair = true;
            }
        }
        int totalRequired = 0;
        for (int idx : required) {
            totalRequired += counts[idx];
        }
        return hasPair && totalRequired == tiles.size();
    }

    private static boolean canFormMelds(int[] counts) {
        int first = -1;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) {
                first = i;
                break;
            }
        }
        if (first == -1) {
            return true;
        }
        if (counts[first] >= 3) {
            counts[first] -= 3;
            if (canFormMelds(counts)) {
                counts[first] += 3;
                return true;
            }
            counts[first] += 3;
        }
        if (isSuitIndex(first)) {
            int rank = (first % 9) + 1;
            if (rank <= 7 && counts[first + 1] > 0 && counts[first + 2] > 0) {
                counts[first]--;
                counts[first + 1]--;
                counts[first + 2]--;
                if (canFormMelds(counts)) {
                    counts[first]++;
                    counts[first + 1]++;
                    counts[first + 2]++;
                    return true;
                }
                counts[first]++;
                counts[first + 1]++;
                counts[first + 2]++;
            }
        }
        return false;
    }

    private static int[] toCounts(List<Tile> tiles) {
        int[] counts = new int[34];
        for (Tile tile : tiles) {
            int idx = tileIndex(tile.getId());
            counts[idx]++;
        }
        return counts;
    }

    private static int tileIndex(TileId id) {
        switch (id.getSuit()) {
            case MAN:
                return id.getRank() - 1;
            case PIN:
                return 9 + id.getRank() - 1;
            case SOU:
                return 18 + id.getRank() - 1;
            case HONOR:
            default:
                return 27 + id.getRank() - 1;
        }
    }

    private static boolean isSuitIndex(int index) {
        return index >= 0 && index < 27;
    }

    private static List<TileId> buildAllTileIds() {
        List<TileId> ids = new java.util.ArrayList<>();
        for (TileSuit suit : TileSuit.values()) {
            int max = suit == TileSuit.HONOR ? 7 : 9;
            for (int rank = 1; rank <= max; rank++) {
                ids.add(TileId.of(suit, rank, false));
            }
        }
        return java.util.Collections.unmodifiableList(ids);
    }
}
