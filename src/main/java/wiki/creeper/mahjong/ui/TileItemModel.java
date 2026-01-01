package wiki.creeper.mahjong.ui;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import wiki.creeper.mahjong.game.TileId;

public final class TileItemModel {
    private TileItemModel() {
    }

    public static NamespacedKey resolve(JavaPlugin plugin, TileId tileId) {
        if (plugin == null || tileId == null) {
            return null;
        }
        return new NamespacedKey(plugin, "tile/" + tileId.toShortString());
    }
}
