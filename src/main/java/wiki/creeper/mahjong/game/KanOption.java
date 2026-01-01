package wiki.creeper.mahjong.game;

import java.util.List;
import java.util.Objects;

public class KanOption {
    private final MeldType type;
    private final TileId tileId;
    private final List<Tile> tiles;

    public KanOption(MeldType type, TileId tileId, List<Tile> tiles) {
        this.type = Objects.requireNonNull(type, "type");
        this.tileId = Objects.requireNonNull(tileId, "tileId");
        this.tiles = List.copyOf(Objects.requireNonNull(tiles, "tiles"));
    }

    public MeldType getType() {
        return type;
    }

    public TileId getTileId() {
        return tileId;
    }

    public List<Tile> getTiles() {
        return tiles;
    }
}
