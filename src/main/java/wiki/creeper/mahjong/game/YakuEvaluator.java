package wiki.creeper.mahjong.game;

import java.util.ArrayList;
import java.util.List;

public final class YakuEvaluator {

    private YakuEvaluator() {
    }

    public static List<Yaku> evaluate(PlayerState winner, RoundState round, boolean tsumo,
                                      boolean openTanyao, boolean ippatsuEnabled, Tile winningTile) {
        Hand hand = winner.getHand();
        List<HandAnalysis> analyses = HandAnalyzer.analyzeAllStandard(hand);
        if (analyses.isEmpty()) {
            return buildBaseYaku(winner, round, tsumo, openTanyao, ippatsuEnabled);
        }
        List<Yaku> best = List.of();
        int bestHan = -1;
        for (HandAnalysis analysis : analyses) {
            List<Yaku> candidate = evaluateForAnalysis(winner, round, tsumo, openTanyao, ippatsuEnabled, winningTile, analysis);
            int han = totalHan(candidate, hand.isClosed());
            if (han > bestHan) {
                bestHan = han;
                best = candidate;
            }
        }
        return best;
    }

    public static List<Yaku> evaluateForAnalysis(PlayerState winner, RoundState round, boolean tsumo,
                                                 boolean openTanyao, boolean ippatsuEnabled, Tile winningTile,
                                                 HandAnalysis analysis) {
        List<Yaku> yaku = buildBaseYaku(winner, round, tsumo, openTanyao, ippatsuEnabled);
        Hand hand = winner.getHand();
        if (analysis != null) {
            if (isPinfu(analysis, hand, winningTile, winner.getSeatWind(), round.getRoundWind())) {
                yaku.add(Yaku.PINFU);
            }
            if (isIipeikou(analysis, hand)) {
                yaku.add(Yaku.IIPEIKOU);
            }
            if (isSanshoku(analysis)) {
                yaku.add(Yaku.SANSHOKU);
            }
            if (isToitoi(analysis)) {
                yaku.add(Yaku.TOITOI);
            }
        }
        return yaku;
    }

    public static List<Yaku> evaluateBase(PlayerState winner, RoundState round, boolean tsumo,
                                          boolean openTanyao, boolean ippatsuEnabled) {
        return buildBaseYaku(winner, round, tsumo, openTanyao, ippatsuEnabled);
    }

    private static List<Yaku> buildBaseYaku(PlayerState winner, RoundState round, boolean tsumo,
                                            boolean openTanyao, boolean ippatsuEnabled) {
        List<Yaku> yaku = new ArrayList<>();
        Hand hand = winner.getHand();
        if (hand.isRiichiDeclared()) {
            yaku.add(Yaku.RIICHI);
            if (ippatsuEnabled && hand.isIppatsuEligible()) {
                yaku.add(Yaku.IPPATSU);
            }
        }
        if (tsumo && hand.isClosed()) {
            yaku.add(Yaku.MENZEN_TSUMO);
        }
        if (isTanyao(hand, openTanyao)) {
            yaku.add(Yaku.TANYAO);
        }
        addYakuhai(yaku, hand, winner.getSeatWind(), round.getRoundWind());
        addFlushYaku(yaku, hand);
        return yaku;
    }

    private static boolean isTanyao(Hand hand, boolean openTanyao) {
        if (!openTanyao && !hand.isClosed()) {
            return false;
        }
        for (Tile tile : hand.getAllTiles()) {
            if (!tile.getId().isSimple()) {
                return false;
            }
        }
        return true;
    }

    private static void addYakuhai(List<Yaku> yaku, Hand hand, SeatWind seat, SeatWind round) {
        java.util.Map<TileId, Integer> counts = countTiles(hand);
        int seatRank = windRank(seat);
        int roundRank = windRank(round);
        for (java.util.Map.Entry<TileId, Integer> entry : counts.entrySet()) {
            TileId id = entry.getKey();
            if (id.getSuit() != TileSuit.HONOR) {
                continue;
            }
            if (entry.getValue() < 3) {
                continue;
            }
            int rank = id.getRank();
            if (rank >= 5) {
                yaku.add(Yaku.YAKUHAI_DRAGON);
            }
            if (rank == seatRank) {
                yaku.add(Yaku.YAKUHAI_SEAT);
            }
            if (rank == roundRank) {
                yaku.add(Yaku.YAKUHAI_ROUND);
            }
        }
    }

    private static java.util.Map<TileId, Integer> countTiles(Hand hand) {
        java.util.Map<TileId, Integer> counts = new java.util.HashMap<>();
        for (Tile tile : hand.getAllTiles()) {
            TileId id = normalize(tile.getId());
            counts.merge(id, 1, Integer::sum);
        }
        return counts;
    }

    private static int windRank(SeatWind wind) {
        switch (wind) {
            case EAST:
                return 1;
            case SOUTH:
                return 2;
            case WEST:
                return 3;
            case NORTH:
            default:
                return 4;
        }
    }

    private static boolean isPinfu(HandAnalysis analysis, Hand hand, Tile winningTile,
                                   SeatWind seat, SeatWind round) {
        if (!hand.isClosed()) {
            return false;
        }
        for (HandMeld meld : analysis.getMelds()) {
            if (!meld.isSequence()) {
                return false;
            }
        }
        if (!isRyanmenWait(analysis, winningTile)) {
            return false;
        }
        TileId pair = analysis.getPair();
        if (pair.getSuit() == TileSuit.HONOR) {
            int rank = pair.getRank();
            if (rank >= 5) {
                return false;
            }
            if (rank == windRank(seat) || rank == windRank(round)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIipeikou(HandAnalysis analysis, Hand hand) {
        if (!hand.isClosed()) {
            return false;
        }
        java.util.Map<String, Integer> sequences = new java.util.HashMap<>();
        for (HandMeld meld : analysis.getMelds()) {
            if (!meld.isSequence()) {
                continue;
            }
            TileId base = meld.getBase();
            String key = base.getSuit() + "-" + base.getRank();
            sequences.merge(key, 1, Integer::sum);
        }
        for (Integer count : sequences.values()) {
            if (count >= 2) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSanshoku(HandAnalysis analysis) {
        boolean[][] sequences = new boolean[3][10];
        for (HandMeld meld : analysis.getMelds()) {
            if (!meld.isSequence()) {
                continue;
            }
            TileId base = meld.getBase();
            int suitIndex = suitIndex(base.getSuit());
            if (suitIndex < 0) {
                continue;
            }
            sequences[suitIndex][base.getRank()] = true;
        }
        for (int rank = 1; rank <= 7; rank++) {
            if (sequences[0][rank] && sequences[1][rank] && sequences[2][rank]) {
                return true;
            }
        }
        return false;
    }

    private static boolean isToitoi(HandAnalysis analysis) {
        for (HandMeld meld : analysis.getMelds()) {
            if (meld.isSequence()) {
                return false;
            }
        }
        return true;
    }

    private static void addFlushYaku(List<Yaku> yaku, Hand hand) {
        boolean hasHonor = false;
        java.util.Set<TileSuit> suits = new java.util.HashSet<>();
        for (Tile tile : hand.getAllTiles()) {
            TileId id = tile.getId();
            if (id.getSuit() == TileSuit.HONOR) {
                hasHonor = true;
            } else {
                suits.add(id.getSuit());
            }
        }
        if (suits.size() == 1) {
            if (hasHonor) {
                yaku.add(Yaku.HONITSU);
            } else {
                yaku.add(Yaku.CHINITSU);
            }
        }
    }

    private static int suitIndex(TileSuit suit) {
        switch (suit) {
            case MAN:
                return 0;
            case PIN:
                return 1;
            case SOU:
                return 2;
            default:
                return -1;
        }
    }

    private static TileId normalize(TileId id) {
        if (id.isRed()) {
            return TileId.of(id.getSuit(), id.getRank(), false);
        }
        return id;
    }

    private static boolean isRyanmenWait(HandAnalysis analysis, Tile winningTile) {
        if (analysis == null || winningTile == null) {
            return false;
        }
        TileId win = normalize(winningTile.getId());
        TileId pair = analysis.getPair();
        if (pair.getSuit() == win.getSuit() && pair.getRank() == win.getRank()) {
            return false;
        }
        for (HandMeld meld : analysis.getMelds()) {
            if (!meld.isSequence()) {
                continue;
            }
            TileId base = meld.getBase();
            if (base.getSuit() != win.getSuit()) {
                continue;
            }
            int baseRank = base.getRank();
            if (win.getRank() < baseRank || win.getRank() > baseRank + 2) {
                continue;
            }
            if (win.getRank() == baseRank + 1) {
                continue;
            }
            if (baseRank == 1 && win.getRank() == 3) {
                continue;
            }
            if (baseRank == 7 && win.getRank() == 7) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static int totalHan(List<Yaku> yaku, boolean closed) {
        int han = 0;
        for (Yaku item : yaku) {
            if (item.isYakuman()) {
                continue;
            }
            han += item.getHan(closed);
        }
        return han;
    }
}
