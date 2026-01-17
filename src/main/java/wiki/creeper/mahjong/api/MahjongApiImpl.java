package wiki.creeper.mahjong.api;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;
import wiki.creeper.mahjong.table.GameTable;
import wiki.creeper.mahjong.table.TableManager;

public final class MahjongApiImpl implements MahjongApi {
    private final TableManager tableManager;

    public MahjongApiImpl(TableManager tableManager) {
        this.tableManager = tableManager;
    }

    @Override
    public TableManager getTableManager() {
        return tableManager;
    }

    @Override
    public Collection<GameTable> getTables() {
        return tableManager.getTables();
    }

    @Override
    public Optional<GameTable> getTable(UUID tableId) {
        return tableManager.getTable(tableId);
    }

    @Override
    public Optional<GameTable> getTableByPlayer(Player player) {
        return tableManager.getTableByPlayer(player);
    }

    @Override
    public Optional<GameTable> getTableBySpectator(Player player) {
        return tableManager.getTableBySpectator(player);
    }

    @Override
    public GameTable createTable(Player owner) {
        return tableManager.createTable(owner);
    }

    @Override
    public boolean joinTable(Player player, UUID tableId) {
        return tableManager.joinTable(player, tableId);
    }

    @Override
    public boolean spectateTable(Player player, UUID tableId) {
        return tableManager.spectateTable(player, tableId);
    }

    @Override
    public boolean leaveTable(Player player) {
        return tableManager.leaveTable(player);
    }
}
