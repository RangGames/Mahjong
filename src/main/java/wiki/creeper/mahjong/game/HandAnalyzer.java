package wiki.creeper.mahjong.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class HandAnalyzer {

    private HandAnalyzer() {
    }

    public static Optional<HandAnalysis> analyzeStandard(Hand hand) {
        List<HandAnalysis> analyses = analyzeAllStandard(hand);
        if (analyses.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(analyses.get(0));
    }

    public static List<HandAnalysis> analyzeAllStandard(Hand hand) {
        List<HandMeld> melds = new ArrayList<>();
        int openMelds = 0;
        for (Meld meld : hand.getMelds()) {
            HandMeld converted = convertMeld(meld);
            if (converted == null) {
                return List.of();
            }
            melds.add(converted);
            openMelds++;
        }
        int meldsNeeded = 4 - openMelds;
        if (meldsNeeded < 0) {
            return List.of();
        }
        int[] counts = toCounts(hand.getConcealed());
        List<HandAnalysis> results = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] >= 2) {
                counts[i] -= 2;
                TileId pair = tileIdFromIndex(i);
                List<HandMeld> meldCopy = new ArrayList<>(melds);
                collectMelds(counts, openMelds + meldsNeeded, meldCopy, results, pair);
                counts[i] += 2;
            }
        }
        return results;
    }

    private static HandMeld convertMeld(Meld meld) {
        List<Tile> tiles = meld.getTiles();
        if (tiles.isEmpty()) {
            return null;
        }
        TileId base = normalize(tiles.get(0).getId());
        switch (meld.getType()) {
            case CHI:
                TileId seqBase = minTileId(tiles);
                return new HandMeld(HandMeldType.SEQUENCE, seqBase, true);
            case PON:
                return new HandMeld(HandMeldType.TRIPLET, base, true);
            case KAN_OPEN:
            case KAN_ADDED:
                return new HandMeld(HandMeldType.KAN, base, true);
            case KAN_CLOSED:
                return new HandMeld(HandMeldType.KAN, base, false);
            default:
                return null;
        }
    }

    private static void collectMelds(int[] counts, int targetMelds, List<HandMeld> melds,
                                     List<HandAnalysis> results, TileId pair) {
        int next = firstIndex(counts);
        if (next == -1) {
            if (melds.size() == targetMelds) {
                results.add(new HandAnalysis(new ArrayList<>(melds), pair));
            }
            return;
        }
        if (melds.size() >= targetMelds) {
            return;
        }
        if (counts[next] >= 3) {
            counts[next] -= 3;
            melds.add(new HandMeld(HandMeldType.TRIPLET, tileIdFromIndex(next), false));
            collectMelds(counts, targetMelds, melds, results, pair);
            melds.remove(melds.size() - 1);
            counts[next] += 3;
        }
        if (isSuitIndex(next)) {
            int rank = (next % 9) + 1;
            if (rank <= 7 && counts[next + 1] > 0 && counts[next + 2] > 0) {
                counts[next]--;
                counts[next + 1]--;
                counts[next + 2]--;
                melds.add(new HandMeld(HandMeldType.SEQUENCE, tileIdFromIndex(next), false));
                collectMelds(counts, targetMelds, melds, results, pair);
                melds.remove(melds.size() - 1);
                counts[next]++;
                counts[next + 1]++;
                counts[next + 2]++;
            }
        }
    }

    private static int firstIndex(int[] counts) {
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) {
                return i;
            }
        }
        return -1;
    }

    private static int[] toCounts(List<Tile> tiles) {
        int[] counts = new int[34];
        for (Tile tile : tiles) {
            counts[tileIndex(normalize(tile.getId()))]++;
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

    private static TileId tileIdFromIndex(int index) {
        if (index < 9) {
            return TileId.of(TileSuit.MAN, index + 1, false);
        }
        if (index < 18) {
            return TileId.of(TileSuit.PIN, index - 9 + 1, false);
        }
        if (index < 27) {
            return TileId.of(TileSuit.SOU, index - 18 + 1, false);
        }
        return TileId.of(TileSuit.HONOR, index - 27 + 1, false);
    }

    private static boolean isSuitIndex(int index) {
        return index >= 0 && index < 27;
    }

    private static TileId normalize(TileId id) {
        if (id.isRed()) {
            return TileId.of(id.getSuit(), id.getRank(), false);
        }
        return id;
    }

    private static TileId minTileId(List<Tile> tiles) {
        TileId min = normalize(tiles.get(0).getId());
        for (Tile tile : tiles) {
            TileId id = normalize(tile.getId());
            if (id.getSuit() != min.getSuit()) {
                continue;
            }
            if (id.getRank() < min.getRank()) {
                min = id;
            }
        }
        return min;
    }
}
