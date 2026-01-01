package wiki.creeper.mahjong.game;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class CallRequest {

    private final UUID playerId;
    private final CallType type;
    private final List<Tile> tiles;

    public CallRequest(UUID playerId, CallType type, List<Tile> tiles) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.type = Objects.requireNonNull(type, "type");
        this.tiles = List.copyOf(Objects.requireNonNull(tiles, "tiles"));
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public CallType getType() {
        return type;
    }

    public List<Tile> getTiles() {
        return tiles;
    }
}
