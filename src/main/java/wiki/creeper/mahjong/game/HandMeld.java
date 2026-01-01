package wiki.creeper.mahjong.game;

import java.util.Objects;

public class HandMeld {

    private final HandMeldType type;
    private final TileId base;
    private final boolean open;

    public HandMeld(HandMeldType type, TileId base, boolean open) {
        this.type = Objects.requireNonNull(type, "type");
        this.base = Objects.requireNonNull(base, "base");
        this.open = open;
    }

    public HandMeldType getType() {
        return type;
    }

    public TileId getBase() {
        return base;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isSequence() {
        return type == HandMeldType.SEQUENCE;
    }

    public boolean isTriplet() {
        return type == HandMeldType.TRIPLET;
    }

    public boolean isKan() {
        return type == HandMeldType.KAN;
    }
}
