package wiki.creeper.mahjong.game;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ScoreResult {

    private final int han;
    private final int fu;
    private final int dora;
    private final boolean dealer;
    private final String limitName;
    private final int ronPayment;
    private final int tsumoFromDealer;
    private final int tsumoFromOthers;
    private final List<Yaku> yaku;

    public ScoreResult(int han, int fu, int dora, boolean dealer, String limitName, int ronPayment,
                       int tsumoFromDealer, int tsumoFromOthers, List<Yaku> yaku) {
        this.han = han;
        this.fu = fu;
        this.dora = dora;
        this.dealer = dealer;
        this.limitName = limitName;
        this.ronPayment = ronPayment;
        this.tsumoFromDealer = tsumoFromDealer;
        this.tsumoFromOthers = tsumoFromOthers;
        this.yaku = List.copyOf(Objects.requireNonNull(yaku, "yaku"));
    }

    public int getHan() {
        return han;
    }

    public int getFu() {
        return fu;
    }

    public int getDora() {
        return dora;
    }

    public boolean isDealer() {
        return dealer;
    }

    public String getLimitName() {
        return limitName;
    }

    public int getRonPayment() {
        return ronPayment;
    }

    public int getTsumoFromDealer() {
        return tsumoFromDealer;
    }

    public int getTsumoFromOthers() {
        return tsumoFromOthers;
    }

    public List<Yaku> getYaku() {
        return Collections.unmodifiableList(yaku);
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        if (han > 0) {
            sb.append(han).append(" han");
            if (fu > 0) {
                sb.append(" ").append(fu).append(" fu");
            }
            if (dora > 0) {
                sb.append(" (dora ").append(dora).append(")");
            }
            if (limitName != null && !limitName.isEmpty()) {
                sb.append(" ").append(limitName);
            }
        } else if (limitName != null && !limitName.isEmpty()) {
            sb.append(limitName);
        } else {
            sb.append("0 han");
        }
        return sb.toString();
    }
}
