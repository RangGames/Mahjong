package wiki.creeper.mahjong.ai;

import java.util.Objects;

public final class BotDecision {
    private final BotAction action;
    private final DiscardEvaluation evaluation;
    private final boolean declareRiichi;
    private final String reason;

    public BotDecision(BotAction action, DiscardEvaluation evaluation, boolean declareRiichi, String reason) {
        this.action = Objects.requireNonNull(action, "action");
        this.evaluation = evaluation;
        this.declareRiichi = declareRiichi;
        this.reason = reason;
    }

    public BotAction getAction() {
        return action;
    }

    public DiscardEvaluation getEvaluation() {
        return evaluation;
    }

    public boolean isDeclareRiichi() {
        return declareRiichi;
    }

    public String getReason() {
        return reason;
    }
}
