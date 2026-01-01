package wiki.creeper.mahjong.ai;

import java.util.List;

public final class CoachAdvice {
    private final List<CoachSuggestion> suggestions;
    private final boolean riichiAvailable;
    private final int riichiValue;
    private final int keepValue;
    private final boolean riichiRecommended;

    public CoachAdvice(List<CoachSuggestion> suggestions, boolean riichiAvailable,
                       int riichiValue, int keepValue, boolean riichiRecommended) {
        this.suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        this.riichiAvailable = riichiAvailable;
        this.riichiValue = riichiValue;
        this.keepValue = keepValue;
        this.riichiRecommended = riichiRecommended;
    }

    public List<CoachSuggestion> getSuggestions() {
        return suggestions;
    }

    public boolean isRiichiAvailable() {
        return riichiAvailable;
    }

    public int getRiichiValue() {
        return riichiValue;
    }

    public int getKeepValue() {
        return keepValue;
    }

    public boolean isRiichiRecommended() {
        return riichiRecommended;
    }
}
