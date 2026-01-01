package wiki.creeper.mahjong.game;

import java.util.Objects;

public final class Tile {

    private final TileId id;
    private final int instanceId;

    public Tile(TileId id, int instanceId) {
        this.id = Objects.requireNonNull(id, "id");
        this.instanceId = instanceId;
    }

    public TileId getId() {
        return id;
    }

    public int getInstanceId() {
        return instanceId;
    }

    @Override
    public String toString() {
        return id.toShortString() + "#" + instanceId;
    }
}
