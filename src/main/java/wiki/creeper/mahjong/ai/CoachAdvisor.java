package wiki.creeper.mahjong.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import wiki.creeper.mahjong.game.Tile;
import wiki.creeper.mahjong.game.TileId;

public final class CoachAdvisor {
    private CoachAdvisor() {
    }

    public static CoachAdvice buildAdvice(PrivateState privateState, boolean canRiichi) {
        if (privateState == null) {
            return new CoachAdvice(List.of(), false, 0, 0, false);
        }
        List<Tile> tiles = privateState.getConcealed();
        if (tiles.isEmpty()) {
            return new CoachAdvice(List.of(), false, 0, 0, false);
        }
        int openMelds = privateState.getMelds().size();
        int[] remainingCounts = TileCounter.buildRemainingCountsFromPrivate(privateState);
        List<DiscardEvaluation> evaluations = new ArrayList<>();
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            List<Tile> remaining = new ArrayList<>(tiles);
            remaining.remove(i);
            int shanten = ShantenCalculator.calculate(remaining, openMelds);
            UkeireResult ukeire = UkeireCalculator.calculate(remaining, openMelds, shanten, remainingCounts);
            TileId tileId = tile.getId();
            evaluations.add(new DiscardEvaluation(tile, tileId, shanten, ukeire, 0, 0, false, tileId.isRed()));
        }
        evaluations.sort(Comparator
                .comparingInt(DiscardEvaluation::getShanten)
                .thenComparing((DiscardEvaluation eval) -> -eval.getUkeire().getTotal()));
        List<CoachSuggestion> suggestions = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        int limit = Math.min(3, evaluations.size());
        for (DiscardEvaluation eval : evaluations) {
            if (suggestions.size() >= limit) {
                break;
            }
            String key = eval.getTile().getId().toShortString();
            if (!seen.add(key)) {
                continue;
            }
            suggestions.add(new CoachSuggestion(eval.getTile().getId(), eval.getShanten(), eval.getUkeire().getTotal()));
        }
        boolean riichiAvailable = canRiichi;
        int riichiValue = 0;
        int keepValue = 0;
        boolean riichiRecommended = false;
        if (riichiAvailable && !suggestions.isEmpty() && openMelds == 0) {
            int ukeire = suggestions.get(0).getUkeire();
            // SIMPLIFIED: compare values based on ukeire only.
            riichiValue = ukeire * 1000;
            keepValue = ukeire * 800;
            riichiRecommended = riichiValue >= keepValue;
        }
        return new CoachAdvice(suggestions, riichiAvailable, riichiValue, keepValue, riichiRecommended);
    }
}
