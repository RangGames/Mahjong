package wiki.creeper.mahjong.table;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class TableManager {

    private final JavaPlugin plugin;
    private final Map<UUID, GameTable> tables = new HashMap<>();
    private final Map<UUID, UUID> playerToTable = new HashMap<>();
    private final Map<String, UUID> roomCodeToTable = new HashMap<>();

    public TableManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Optional<GameTable> getTable(UUID tableId) {
        return Optional.ofNullable(tables.get(tableId));
    }

    public Optional<GameTable> getTableByPlayer(Player player) {
        UUID tableId = playerToTable.get(player.getUniqueId());
        if (tableId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tables.get(tableId));
    }

    public GameTable createTable(Player owner) {
        GameTable table = new GameTable(plugin);
        tables.put(table.getId(), table);
        joinTable(owner, table.getId());
        return table;
    }

    public GameTable createRoom(Player owner) {
        GameTable table = new GameTable(plugin);
        String code = generateRoomCode();
        table.enableRoom(owner, code);
        tables.put(table.getId(), table);
        roomCodeToTable.put(code.toUpperCase(Locale.ROOT), table.getId());
        joinTable(owner, table.getId());
        return table;
    }

    public boolean joinTable(Player player, UUID tableId) {
        if (playerToTable.containsKey(player.getUniqueId())) {
            return false;
        }
        GameTable table = tables.get(tableId);
        if (table == null) {
            return false;
        }
        if (!table.addPlayer(player)) {
            return false;
        }
        playerToTable.put(player.getUniqueId(), tableId);
        return true;
    }

    public Optional<GameTable> getRoomByCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        UUID tableId = roomCodeToTable.get(code.toUpperCase(Locale.ROOT));
        if (tableId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tables.get(tableId));
    }

    public boolean joinRoom(Player player, String code) {
        Optional<GameTable> room = getRoomByCode(code);
        if (room.isEmpty()) {
            return false;
        }
        return joinTable(player, room.get().getId());
    }

    public boolean leaveTable(Player player) {
        UUID tableId = playerToTable.remove(player.getUniqueId());
        if (tableId == null) {
            return false;
        }
        GameTable table = tables.get(tableId);
        if (table == null) {
            return false;
        }
        table.removePlayer(player);
        if (table.isEmpty()) {
            table.shutdown();
            tables.remove(tableId);
            removeRoomCode(table);
        }
        return true;
    }

    public Collection<GameTable> getTables() {
        return Collections.unmodifiableCollection(tables.values());
    }

    public boolean disbandTable(UUID tableId) {
        GameTable table = tables.remove(tableId);
        if (table == null) {
            return false;
        }
        removeRoomCode(table);
        for (UUID playerId : table.getPlayers()) {
            playerToTable.remove(playerId);
        }
        table.shutdown();
        return true;
    }

    public void shutdown() {
        for (GameTable table : tables.values()) {
            table.shutdown();
        }
        tables.clear();
        playerToTable.clear();
        roomCodeToTable.clear();
    }

    private void removeRoomCode(GameTable table) {
        if (table == null) {
            return;
        }
        String code = table.getRoomCode();
        if (code != null) {
            roomCodeToTable.remove(code.toUpperCase(Locale.ROOT));
        }
    }

    private String generateRoomCode() {
        String charset = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int attempt = 0; attempt < 100; attempt++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                int idx = ThreadLocalRandom.current().nextInt(charset.length());
                sb.append(charset.charAt(idx));
            }
            String code = sb.toString();
            if (!roomCodeToTable.containsKey(code)) {
                return code;
            }
        }
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }
}
