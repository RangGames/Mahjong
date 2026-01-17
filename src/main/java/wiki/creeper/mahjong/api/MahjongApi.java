package wiki.creeper.mahjong.api;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;
import wiki.creeper.mahjong.table.GameTable;
import wiki.creeper.mahjong.table.TableManager;

public interface MahjongApi {
    TableManager getTableManager();

    Collection<GameTable> getTables();

    Optional<GameTable> getTable(UUID tableId);

    Optional<GameTable> getTableByPlayer(Player player);

    Optional<GameTable> getTableBySpectator(Player player);

    GameTable createTable(Player owner);

    boolean joinTable(Player player, UUID tableId);

    boolean spectateTable(Player player, UUID tableId);

    boolean leaveTable(Player player);
}
