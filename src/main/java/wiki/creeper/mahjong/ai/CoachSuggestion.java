package wiki.creeper.mahjong.ai;

import wiki.creeper.mahjong.game.TileId;

public final class CoachSuggestion {
    private final TileId discard;
    private final int shanten;
    private final int ukeire;

    public CoachSuggestion(TileId discard, int shanten, int ukeire) {
        this.discard = discard;
        this.shanten = shanten;
        this.ukeire = ukeire;
    }

    public TileId getDiscard() {
        return discard;
    }

    public int getShanten() {
        return shanten;
    }

    public int getUkeire() {
        return ukeire;
    }
}
