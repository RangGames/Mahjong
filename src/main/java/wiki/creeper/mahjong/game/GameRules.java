package wiki.creeper.mahjong.game;

public class GameRules {

    private final boolean redDora;
    private final boolean openTanyao;
    private final boolean ippatsu;
    private final boolean uraDora;

    public GameRules(boolean redDora, boolean openTanyao, boolean ippatsu, boolean uraDora) {
        this.redDora = redDora;
        this.openTanyao = openTanyao;
        this.ippatsu = ippatsu;
        this.uraDora = uraDora;
    }

    public boolean isRedDoraEnabled() {
        return redDora;
    }

    public boolean isOpenTanyaoEnabled() {
        return openTanyao;
    }

    public boolean isIppatsuEnabled() {
        return ippatsu;
    }

    public boolean isUraDoraEnabled() {
        return uraDora;
    }
}
