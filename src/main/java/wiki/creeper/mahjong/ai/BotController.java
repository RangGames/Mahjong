package wiki.creeper.mahjong.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import wiki.creeper.mahjong.game.DoraCalculator;
import wiki.creeper.mahjong.game.GameRules;
import wiki.creeper.mahjong.game.RoundState;
import wiki.creeper.mahjong.game.Tile;
import wiki.creeper.mahjong.game.TileId;

public final class BotController {
    private BotController() {
    }

    public static BotDecision decideDiscard(BotProfile profile,
                                            PublicState publicState,
                                            PrivateState privateState,
                                            TurnContext context,
                                            boolean canRiichi,
                                            Tile lastDrawnTile,
                                            GameRules rules,
                                            RoundState roundState,
                                            List<Tile> doraIndicators,
                                            int[] remainingCounts) {
        Objects.requireNonNull(profile, "profile");
        if (privateState == null || privateState.getConcealed().isEmpty()) {
            return null;
        }
        List<Tile> tiles = privateState.getConcealed();
        int openMelds = privateState.getMelds().size();
        boolean anyRiichi = hasOpponentRiichi(publicState, privateState.getPlayerId());
        List<DiscardEvaluation> evaluations = new ArrayList<>();
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            List<Tile> remaining = new ArrayList<>(tiles);
            remaining.remove(i);
            int shanten = ShantenCalculator.calculate(remaining, openMelds);
            UkeireResult ukeire = UkeireCalculator.calculate(remaining, openMelds, shanten, remainingCounts);
            TileId normalized = TileCounter.normalize(tile.getId());
            boolean dora = isDora(tile.getId(), doraIndicators, rules.isRedDoraEnabled());
            int safety = anyRiichi ? safetyScore(publicState, privateState.getPlayerId(), normalized) : 0;
            int expectedValue = 0;
            if (profile.getDifficulty() == BotDifficulty.HARD && shanten <= 0 && roundState != null) {
                expectedValue = ExpectedValueCalculator.estimateTenpaiRon(
                        remaining,
                        ukeire.getEffectiveTiles(),
                        remainingCounts,
                        privateState.getMelds(),
                        privateState.getSeatWind(),
                        roundState,
                        doraIndicators,
                        rules,
                        privateState.isRiichiDeclared()
                );
            }
            evaluations.add(new DiscardEvaluation(tile, normalized, shanten, ukeire, safety, expectedValue, dora, tile.getId().isRed()));
        }
        if (evaluations.isEmpty()) {
            return null;
        }
        int minShanten = evaluations.stream().mapToInt(DiscardEvaluation::getShanten).min().orElse(8);
        List<DiscardEvaluation> candidates = new ArrayList<>();
        for (DiscardEvaluation eval : evaluations) {
            if (eval.getShanten() == minShanten) {
                candidates.add(eval);
            }
        }
        DiscardEvaluation selected = switch (profile.getDifficulty()) {
            case BEGINNER -> pickRandom(profile.getRandom(), candidates);
            case NORMAL -> pickNormal(profile.getRandom(), candidates, context);
            case HARD -> pickHard(profile.getRandom(), candidates, context, anyRiichi);
        };
        boolean declareRiichi = shouldDeclareRiichi(profile.getDifficulty(), canRiichi, selected, lastDrawnTile);
        String reason = buildReason(profile.getDifficulty(), selected);
        return new BotDecision(BotAction.discard(selected.getTile()), selected, declareRiichi, reason);
    }

    private static DiscardEvaluation pickNormal(Random random, List<DiscardEvaluation> candidates, TurnContext context) {
        TileId avoid = context == null ? null : context.getLastDiscard();
        if (avoid != null) {
            List<DiscardEvaluation> filtered = new ArrayList<>();
            for (DiscardEvaluation eval : candidates) {
                if (!sameTile(eval.getTileId(), avoid)) {
                    filtered.add(eval);
                }
            }
            if (!filtered.isEmpty()) {
                candidates = filtered;
            }
        }
        int bestUkeire = candidates.stream().mapToInt(eval -> eval.getUkeire().getTotal()).max().orElse(0);
        List<DiscardEvaluation> best = new ArrayList<>();
        for (DiscardEvaluation eval : candidates) {
            if (eval.getUkeire().getTotal() == bestUkeire) {
                best.add(eval);
            }
        }
        return pickRandom(random, best);
    }

    private static DiscardEvaluation pickHard(Random random, List<DiscardEvaluation> candidates, TurnContext context, boolean anyRiichi) {
        TileId avoid = context == null ? null : context.getLastDiscard();
        int safetyWeight = anyRiichi ? 20 : 5;
        DiscardEvaluation best = null;
        int bestScore = Integer.MIN_VALUE;
        for (DiscardEvaluation eval : candidates) {
            int score = eval.getUkeire().getTotal() * 10;
            score += eval.getSafety() * safetyWeight;
            score += eval.getExpectedValue() / 100;
            if (eval.isDora()) {
                score -= 12;
            }
            if (eval.isRed()) {
                score -= 4;
            }
            if (avoid != null && sameTile(eval.getTileId(), avoid)) {
                score -= 3;
            }
            if (score > bestScore) {
                bestScore = score;
                best = eval;
            } else if (score == bestScore && random.nextBoolean()) {
                best = eval;
            }
        }
        return best == null ? pickRandom(random, candidates) : best;
    }

    private static DiscardEvaluation pickRandom(Random random, List<DiscardEvaluation> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private static boolean shouldDeclareRiichi(BotDifficulty difficulty, boolean canRiichi,
                                               DiscardEvaluation selected, Tile lastDrawnTile) {
        if (!canRiichi || selected == null || lastDrawnTile == null) {
            return false;
        }
        if (difficulty == BotDifficulty.BEGINNER) {
            return false;
        }
        if (selected.getShanten() > 0) {
            return false;
        }
        if (!selected.getTile().equals(lastDrawnTile)) {
            return false;
        }
        int ukeire = selected.getUkeire().getTotal();
        int threshold = difficulty == BotDifficulty.HARD ? 4 : 6;
        return ukeire >= threshold;
    }

    private static String buildReason(BotDifficulty difficulty, DiscardEvaluation eval) {
        if (eval == null) {
            return "no-eval";
        }
        return difficulty.name().toLowerCase() + ":shanten=" + eval.getShanten()
                + ",ukeire=" + eval.getUkeire().getTotal()
                + ",safety=" + eval.getSafety()
                + ",value=" + eval.getExpectedValue();
    }

    private static boolean hasOpponentRiichi(PublicState publicState, java.util.UUID selfId) {
        if (publicState == null) {
            return false;
        }
        for (var entry : publicState.getRiichiDeclared().entrySet()) {
            if (entry.getKey().equals(selfId)) {
                continue;
            }
            if (Boolean.TRUE.equals(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static int safetyScore(PublicState publicState, java.util.UUID selfId, TileId tileId) {
        if (publicState == null || tileId == null) {
            return 0;
        }
        int count = 0;
        for (var entry : publicState.getDiscards().entrySet()) {
            if (entry.getKey().equals(selfId)) {
                continue;
            }
            for (TileId discard : entry.getValue()) {
                if (sameTile(discard, tileId)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isDora(TileId tileId, List<Tile> indicators, boolean redEnabled) {
        if (tileId == null) {
            return false;
        }
        if (redEnabled && tileId.isRed()) {
            return true;
        }
        if (indicators == null) {
            return false;
        }
        for (Tile indicator : indicators) {
            if (indicator == null) {
                continue;
            }
            TileId dora = DoraCalculator.nextDora(indicator.getId());
            if (sameTile(tileId, dora)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameTile(TileId a, TileId b) {
        if (a == null || b == null) {
            return false;
        }
        TileId na = TileCounter.normalize(a);
        TileId nb = TileCounter.normalize(b);
        return na.getSuit() == nb.getSuit() && na.getRank() == nb.getRank();
    }
}
