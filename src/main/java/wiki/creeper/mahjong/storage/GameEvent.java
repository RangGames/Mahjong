package wiki.creeper.mahjong.storage;

import java.time.Instant;
import java.util.UUID;

public class GameEvent {

    private final Instant timestamp;
    private final UUID tableId;
    private final UUID playerId;
    private final GameEventType type;
    private final String payload;

    public GameEvent(UUID tableId, UUID playerId, GameEventType type, String payload) {
        this.timestamp = Instant.now();
        this.tableId = tableId;
        this.playerId = playerId;
        this.type = type;
        this.payload = payload == null ? "" : payload;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public UUID getTableId() {
        return tableId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public GameEventType getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public String toLine() {
        return timestamp + "|" + tableId + "|" + playerId + "|" + type + "|" + payload;
    }
}
