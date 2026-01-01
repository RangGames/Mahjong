package wiki.creeper.mahjong.ui;

public enum WorldUiAction {
    CHI("CHI"),
    PON("PON"),
    KAN("KAN"),
    RON("RON"),
    RIICHI("RIICHI"),
    TSUMO("TSUMO");

    private final String label;

    WorldUiAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
