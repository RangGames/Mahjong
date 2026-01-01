package wiki.creeper.mahjong.game;

import java.util.ArrayList;
import java.util.List;

public final class ScoreCalculator {
    private ScoreCalculator() {
    }

    public static boolean hasYaku(PlayerState winner, RoundState round, boolean tsumo,
                                  Tile winningTile, boolean openTanyao, boolean ippatsuEnabled) {
        Hand hand = winner.getHand();
        if (HandValidator.isThirteenOrphans(hand) || HandValidator.isSevenPairs(hand)) {
            return true;
        }
        List<HandAnalysis> analyses = HandAnalyzer.analyzeAllStandard(hand);
        for (HandAnalysis analysis : analyses) {
            List<Yaku> yaku = YakuEvaluator.evaluateForAnalysis(winner, round, tsumo, openTanyao, ippatsuEnabled, winningTile, analysis);
            if (!yaku.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static ScoreResult calculate(PlayerState winner, RoundState round, boolean tsumo, Tile winningTile,
                                        List<Tile> doraIndicators, List<Tile> uraIndicators,
                                        boolean redDoraEnabled, boolean openTanyao, boolean ippatsuEnabled,
                                        boolean uraDoraEnabled) {
        Hand hand = winner.getHand();
        boolean dealer = winner.getSeatWind() == round.getDealerWind();

        int doraCount = DoraCalculator.countDora(hand, doraIndicators, redDoraEnabled);
        int uraCount = 0;
        if (uraDoraEnabled && hand.isRiichiDeclared()) {
            uraCount = DoraCalculator.countDora(hand, uraIndicators, redDoraEnabled);
        }

        List<ScoreResult> results = new ArrayList<>();

        if (HandValidator.isThirteenOrphans(hand)) {
            results.add(buildResult(List.of(Yaku.KOKUSHI), 0, doraCount, uraCount, tsumo, dealer, hand.isClosed()));
        }

        if (HandValidator.isSevenPairs(hand)) {
            List<Yaku> yaku = new ArrayList<>();
            yaku.add(Yaku.CHIITOI);
            yaku.addAll(YakuEvaluator.evaluateBase(winner, round, tsumo, openTanyao, ippatsuEnabled));
            results.add(buildResult(yaku, 25, doraCount, uraCount, tsumo, dealer, hand.isClosed()));
        }

        List<HandAnalysis> analyses = HandAnalyzer.analyzeAllStandard(hand);
        for (HandAnalysis analysis : analyses) {
            List<Yaku> yaku = YakuEvaluator.evaluateForAnalysis(winner, round, tsumo, openTanyao, ippatsuEnabled, winningTile, analysis);
            if (yaku.isEmpty()) {
                continue;
            }
            int fu;
            if (yaku.contains(Yaku.PINFU) && hand.isClosed()) {
                fu = tsumo ? 20 : 30;
            } else {
                fu = FuCalculator.calculateStandard(analysis, hand, winningTile, tsumo, winner.getSeatWind(), round.getRoundWind());
            }
            results.add(buildResult(yaku, fu, doraCount, uraCount, tsumo, dealer, hand.isClosed()));
        }

        ScoreResult best = null;
        int bestGain = -1;
        int bestHan = -1;
        int bestFu = -1;
        for (ScoreResult result : results) {
            int gain = winnerGain(result, tsumo, dealer);
            if (gain > bestGain) {
                best = result;
                bestGain = gain;
                bestHan = result.getHan();
                bestFu = result.getFu();
                continue;
            }
            if (gain == bestGain) {
                int han = result.getHan();
                int fu = result.getFu();
                if (han > bestHan || (han == bestHan && fu > bestFu)) {
                    best = result;
                    bestHan = han;
                    bestFu = fu;
                }
            }
        }

        return best;
    }

    private static ScoreResult buildResult(List<Yaku> yaku, int fu, int doraCount, int uraCount,
                                           boolean tsumo, boolean dealer, boolean closed) {
        int yakumanCount = 0;
        int han = 0;
        for (Yaku item : yaku) {
            if (item.isYakuman()) {
                yakumanCount++;
            } else {
                han += item.getHan(closed);
            }
        }

        int totalDora = doraCount + uraCount;
        if (yakumanCount == 0) {
            han += totalDora;
        } else {
            totalDora = 0;
        }

        int basePoints;
        String limitName = "";
        if (yakumanCount > 0) {
            basePoints = 8000 * yakumanCount;
            limitName = yakumanCount > 1 ? yakumanCount + "x Yakuman" : "Yakuman";
            fu = 0;
        } else {
            int rawBase = fu * (1 << (2 + han));
            basePoints = rawBase;
            if (han >= 13) {
                basePoints = 8000;
                limitName = "Kazoe Yakuman";
            } else if (han >= 11) {
                basePoints = 6000;
                limitName = "Sanbaiman";
            } else if (han >= 8) {
                basePoints = 4000;
                limitName = "Baiman";
            } else if (han >= 6) {
                basePoints = 3000;
                limitName = "Haneman";
            } else if (han >= 5 || (han == 4 && fu >= 40) || (han == 3 && fu >= 70)) {
                basePoints = 2000;
                limitName = "Mangan";
            }
        }

        int ronPayment = 0;
        int tsumoFromDealer = 0;
        int tsumoFromOthers = 0;
        if (tsumo) {
            if (dealer) {
                int each = roundUpToHundred(basePoints * 2);
                tsumoFromDealer = each;
                tsumoFromOthers = each;
            } else {
                tsumoFromDealer = roundUpToHundred(basePoints * 2);
                tsumoFromOthers = roundUpToHundred(basePoints);
            }
        } else {
            ronPayment = dealer ? roundUpToHundred(basePoints * 6) : roundUpToHundred(basePoints * 4);
        }

        return new ScoreResult(han, fu, totalDora, dealer, limitName, ronPayment, tsumoFromDealer, tsumoFromOthers, yaku);
    }

    private static int winnerGain(ScoreResult result, boolean tsumo, boolean dealer) {
        if (result == null) {
            return -1;
        }
        if (tsumo) {
            if (dealer) {
                return result.getTsumoFromDealer() * 3;
            }
            return result.getTsumoFromDealer() + (result.getTsumoFromOthers() * 2);
        }
        return result.getRonPayment();
    }

    private static int roundUpToHundred(int value) {
        if (value % 100 == 0) {
            return value;
        }
        return ((value / 100) + 1) * 100;
    }
}