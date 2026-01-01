package wiki.creeper.mahjong.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Meld {

    private final MeldType type;
    private final List<Tile> tiles;
    private final UUID calledFrom;

    public Meld(MeldType type, List<Tile> tiles, UUID calledFrom) {
        this.type = Objects.requireNonNull(type, "type");
        this.tiles = new ArrayList<>(Objects.requireNonNull(tiles, "tiles"));
        this.calledFrom = calledFrom;
    }

    public MeldType getType() {
        return type;
    }

    public List<Tile> getTiles() {
        return Collections.unmodifiableList(tiles);
    }

    public UUID getCalledFrom() {
        return calledFrom;
    }
}
