package wiki.creeper.mahjong.ui;

public enum WorldUiAction {
    CHI("치"),
    PON("퐁"),
    KAN("깡"),
    RON("론"),
    RIICHI("리치"),
    TSUMO("쯔모");

    private final String label;

    WorldUiAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
