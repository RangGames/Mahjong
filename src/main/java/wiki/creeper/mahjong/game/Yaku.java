package wiki.creeper.mahjong.game;

public enum Yaku {
    RIICHI("Riichi", 1, 0, false),
    IPPATSU("Ippatsu", 1, 0, false),
    MENZEN_TSUMO("Menzen Tsumo", 1, 0, false),
    TANYAO("Tanyao", 1, 1, false),
    YAKUHAI_DRAGON("Yakuhai (Dragon)", 1, 1, false),
    YAKUHAI_SEAT("Yakuhai (Seat Wind)", 1, 1, false),
    YAKUHAI_ROUND("Yakuhai (Round Wind)", 1, 1, false),
    PINFU("Pinfu", 1, 0, false),
    IIPEIKOU("Iipeikou", 1, 0, false),
    SANSHOKU("Sanshoku Doujun", 2, 1, false),
    TOITOI("Toitoi", 2, 2, false),
    HONITSU("Honitsu", 3, 2, false),
    CHINITSU("Chinitsu", 6, 5, false),
    CHIITOI("Chiitoitsu", 2, 0, false),
    KOKUSHI("Kokushi Musou", 13, 0, true);

    private final String displayName;
    private final int hanClosed;
    private final int hanOpen;
    private final boolean yakuman;

    Yaku(String displayName, int hanClosed, int hanOpen, boolean yakuman) {
        this.displayName = displayName;
        this.hanClosed = hanClosed;
        this.hanOpen = hanOpen;
        this.yakuman = yakuman;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getHan(boolean closed) {
        return closed ? hanClosed : hanOpen;
    }

    public boolean isYakuman() {
        return yakuman;
    }
}
