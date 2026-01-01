package wiki.creeper.mahjong.ai;

import java.util.List;
import java.util.UUID;
import wiki.creeper.mahjong.game.GameRules;
import wiki.creeper.mahjong.game.Hand;
import wiki.creeper.mahjong.game.Meld;
import wiki.creeper.mahjong.game.PlayerState;
import wiki.creeper.mahjong.game.RoundState;
import wiki.creeper.mahjong.game.ScoreCalculator;
import wiki.creeper.mahjong.game.ScoreResult;
import wiki.creeper.mahjong.game.SeatWind;
import wiki.creeper.mahjong.game.Tile;
import wiki.creeper.mahjong.game.TileId;

public final class ExpectedValueCalculator {
    private ExpectedValueCalculator() {
    }

    public static int estimateTenpaiRon(List<Tile> concealed, List<TileId> winningTiles, int[] remainingCounts,
                                        List<Meld> melds, SeatWind seatWind, RoundState round,
                                        List<Tile> doraIndicators, GameRules rules, boolean riichiDeclared) {
        if (concealed == null || winningTiles == null || remainingCounts == null || round == null || rules == null || seatWind == null) {
            return 0;
        }
        PlayerState temp = new PlayerState(UUID.randomUUID(), seatWind, 25000);
        Hand hand = temp.getHand();
        for (Tile tile : concealed) {
            hand.addTile(new Tile(tile.getId(), -1));
        }
        if (melds != null) {
            for (Meld meld : melds) {
                hand.addMeld(meld);
            }
        }
        hand.setRiichiDeclared(riichiDeclared);
        hand.setIppatsuEligible(false);
        int totalWeight = 0;
        long totalValue = 0;
        for (TileId winId : winningTiles) {
            if (winId == null) {
                continue;
            }
            int idx = TileCounter.tileIndex(winId);
            int weight = idx >= 0 && idx < remainingCounts.length ? remainingCounts[idx] : 0;
            if (weight <= 0) {
                continue;
            }
            Tile winTile = new Tile(winId, -1);
            hand.addTile(winTile);
            List<Tile> dora = doraIndicators == null ? List.of() : doraIndicators;
            ScoreResult result = ScoreCalculator.calculate(
                    temp,
                    round,
                    false,
                    winTile,
                    dora,
                    List.of(),
                    rules.isRedDoraEnabled(),
                    rules.isOpenTanyaoEnabled(),
                    rules.isIppatsuEnabled(),
                    false
            );
            if (result != null) {
                totalValue += (long) result.getRonPayment() * weight;
                totalWeight += weight;
            }
            hand.removeTile(winTile);
        }
        if (totalWeight == 0) {
            return 0;
        }
        return (int) Math.round(totalValue / (double) totalWeight);
    }
}
