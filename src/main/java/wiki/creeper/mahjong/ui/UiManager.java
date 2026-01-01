package wiki.creeper.mahjong.ui;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import wiki.creeper.mahjong.game.PlayerState;
import wiki.creeper.mahjong.game.Tile;

public class UiManager {

    private final NamespacedKey tileInstanceKey;
    private final NamespacedKey tileIdKey;

    public UiManager(JavaPlugin plugin) {
        this.tileInstanceKey = new NamespacedKey(plugin, "tile_instance");
        this.tileIdKey = new NamespacedKey(plugin, "tile_id");
    }

    public HandInventory createHandInventory(UUID playerId) {
        return new HandInventory(playerId);
    }

    public void renderHand(Inventory inventory, PlayerState state) {
        inventory.clear();
        List<Tile> tiles = state.getHand().getConcealed();
        for (int i = 0; i < tiles.size() && i < inventory.getSize(); i++) {
            inventory.setItem(i, toItem(tiles.get(i)));
        }
    }

    public Optional<Tile> readTile(ItemStack item, PlayerState state) {
        if (item == null || item.getType() == Material.AIR) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        Integer instance = container.get(tileInstanceKey, PersistentDataType.INTEGER);
        if (instance == null) {
            return Optional.empty();
        }
        for (Tile tile : state.getHand().getConcealed()) {
            if (tile.getInstanceId() == instance) {
                return Optional.of(tile);
            }
        }
        return Optional.empty();
    }

    private ItemStack toItem(Tile tile) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(tile.getId().toShortString()));
        meta.getPersistentDataContainer().set(tileInstanceKey, PersistentDataType.INTEGER, tile.getInstanceId());
        meta.getPersistentDataContainer().set(tileIdKey, PersistentDataType.STRING, tile.getId().toShortString());
        item.setItemMeta(meta);
        return item;
    }
}
