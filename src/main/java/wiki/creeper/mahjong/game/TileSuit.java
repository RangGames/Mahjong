package wiki.creeper.mahjong.game;

public enum TileSuit {
    MAN('m'),
    PIN('p'),
    SOU('s'),
    HONOR('z');

    private final char code;

    TileSuit(char code) {
        this.code = code;
    }

    public char getCode() {
        return code;
    }

    public static TileSuit fromCode(char code) {
        char lower = Character.toLowerCase(code);
        for (TileSuit suit : values()) {
            if (suit.code == lower) {
                return suit;
            }
        }
        throw new IllegalArgumentException("Unknown suit code: " + code);
    }

    public static boolean isSuited(TileSuit suit) {
        return suit == MAN || suit == PIN || suit == SOU;
    }
}
