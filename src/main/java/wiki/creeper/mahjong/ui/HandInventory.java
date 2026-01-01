package wiki.creeper.mahjong.ui;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class HandInventory implements InventoryHolder {

    private final UUID playerId;
    private final Inventory inventory;

    public HandInventory(UUID playerId) {
        this.playerId = playerId;
        this.inventory = Bukkit.createInventory(this, 54, "Mahjong Hand");
    }

    public UUID getPlayerId() {
        return playerId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
