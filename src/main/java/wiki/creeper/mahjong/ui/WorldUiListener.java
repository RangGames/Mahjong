package wiki.creeper.mahjong.ui;

import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import wiki.creeper.mahjong.table.GameTable;
import wiki.creeper.mahjong.table.TableManager;

public class WorldUiListener implements Listener {
    private final TableManager tableManager;
    private final NamespacedKey tableKey;
    private final NamespacedKey actionKey;
    private final NamespacedKey handTileKey;
    private final NamespacedKey handOwnerKey;

    public WorldUiListener(JavaPlugin plugin, TableManager tableManager) {
        this.tableManager = tableManager;
        this.tableKey = new NamespacedKey(plugin, "table_id");
        this.actionKey = new NamespacedKey(plugin, "action");
        this.handTileKey = new NamespacedKey(plugin, "hand_tile");
        this.handOwnerKey = new NamespacedKey(plugin, "hand_owner");
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) {    
            return;
        }
        PersistentDataContainer container = interaction.getPersistentDataContainer();
        String tableIdRaw = container.get(tableKey, PersistentDataType.STRING);
        if (tableIdRaw == null) {
            return;
        }
        event.setCancelled(true);
        UUID tableId;
        try {
            tableId = UUID.fromString(tableIdRaw);
        } catch (IllegalArgumentException ex) {
            return;
        }
        GameTable table = tableManager.getTable(tableId).orElse(null);
        if (table == null) {
            return;
        }
        Player player = event.getPlayer();
        if (tableManager.getTableByPlayer(player).map(GameTable::getId).filter(tableId::equals).isEmpty()) {
            return;
        }
        Integer handTile = container.get(handTileKey, PersistentDataType.INTEGER);
        if (handTile != null) {
            String ownerRaw = container.get(handOwnerKey, PersistentDataType.STRING);
            if (ownerRaw != null && !ownerRaw.equals(player.getUniqueId().toString())) {
                return;
            }
            table.requestDiscardByInstance(player, handTile);
            return;
        }
        String actionRaw = container.get(actionKey, PersistentDataType.STRING);
        if (actionRaw == null) {
            return;
        }
        WorldUiAction action;
        try {
            action = WorldUiAction.valueOf(actionRaw);
        } catch (IllegalArgumentException ex) {
            return;
        }
        switch (action) {
            case CHI:
                table.requestChiSelection(player);
                break;
            case PON:
                table.requestPon(player);
                break;
            case KAN:
                table.requestKan(player);
                break;
            case RON:
                table.requestRon(player);
                break;
            case RIICHI:
                table.requestRiichi(player);
                break;
            case TSUMO:
                table.requestTsumo(player);
                break;
            default:
                break;
        }
    }
}
