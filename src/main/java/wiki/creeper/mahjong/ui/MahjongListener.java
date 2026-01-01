package wiki.creeper.mahjong.ui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import wiki.creeper.mahjong.table.TableManager;

public class MahjongListener implements Listener {

    private final TableManager tableManager;

    public MahjongListener(TableManager tableManager) {
        this.tableManager = tableManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof HandInventory)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != top) {
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        HandInventory hand = (HandInventory) holder;
        if (!hand.getPlayerId().equals(player.getUniqueId())) {
            return;
        }
        tableManager.getTableByPlayer(player).ifPresent(table -> table.requestDiscard(player, item));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        tableManager.leaveTable(event.getPlayer());
    }
}
