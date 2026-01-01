package wiki.creeper.mahjong.game;


public final class FuCalculator {

    private FuCalculator() {
    }

    public static int calculateStandard(HandAnalysis analysis, Hand hand, Tile winningTile, boolean tsumo, SeatWind seat, SeatWind round) {
        int fu = 20;
        if (tsumo) {
            fu += 2;
        }
        if (!tsumo && hand.isClosed()) {
            fu += 10;
        }
        TileId pair = analysis.getPair();
        if (pair.getSuit() == TileSuit.HONOR) {
            if (pair.getRank() >= 5) {
                fu += 2;
            }
            if (pair.getRank() == windRank(seat)) {
                fu += 2;
            }
            if (pair.getRank() == windRank(round)) {
                fu += 2;
            }
        }
        for (HandMeld meld : analysis.getMelds()) {
            if (meld.isSequence()) {
                continue;
            }
            boolean terminalOrHonor = meld.getBase().isTerminal() || meld.getBase().isHonor();
            if (meld.isKan()) {
                if (meld.isOpen()) {
                    fu += terminalOrHonor ? 16 : 8;
                } else {
                    fu += terminalOrHonor ? 32 : 16;
                }
            } else if (meld.isTriplet()) {
                if (meld.isOpen()) {
                    fu += terminalOrHonor ? 4 : 2;
                } else {
                    fu += terminalOrHonor ? 8 : 4;
                }
            }
        }
        fu += waitFu(analysis, winningTile);
        if (fu < 20) {
            fu = 20;
        }
        return roundUpToTen(fu);
    }

    private static int waitFu(HandAnalysis analysis, Tile winningTile) {
        if (winningTile == null || analysis == null) {
            return 0;
        }
        TileId winId = normalize(winningTile.getId());
        if (analysis.getPair().getSuit() == winId.getSuit() && analysis.getPair().getRank() == winId.getRank()) {
            return 2;
        }
        for (HandMeld meld : analysis.getMelds()) {
            if (!meld.isSequence()) {
                continue;
            }
            TileId base = meld.getBase();
            if (base.getSuit() != winId.getSuit()) {
                continue;
            }
            int baseRank = base.getRank();
            if (winId.getRank() < baseRank || winId.getRank() > baseRank + 2) {
                continue;
            }
            if (winId.getRank() == baseRank + 1) {
                return 2; // closed wait
            }
            if (baseRank == 1 && winId.getRank() == 3) {
                return 2; // edge wait 1-2-3
            }
            if (baseRank == 7 && winId.getRank() == 7) {
                return 2; // edge wait 7-8-9
            }
        }
        return 0;
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

    private static TileId normalize(TileId id) {
        if (id.isRed()) {
            return TileId.of(id.getSuit(), id.getRank(), false);
        }
        return id;
    }

    private static int roundUpToTen(int value) {
        if (value % 10 == 0) {
            return value;
        }
        return ((value / 10) + 1) * 10;
    }
}
