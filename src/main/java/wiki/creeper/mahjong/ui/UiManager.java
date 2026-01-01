package wiki.creeper.mahjong.ui;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
import wiki.creeper.mahjong.game.TileId;
import wiki.creeper.mahjong.game.TileSuit;

public class UiManager {

    private static final HexFormat HEX = HexFormat.of();

    private final JavaPlugin plugin;
    private final NamespacedKey tileInstanceKey;
    private final NamespacedKey tileIdKey;
    private final NamespacedKey tileNonceKey;
    private final NamespacedKey tileSignatureKey;
    private final byte[] signatureSalt;

    public UiManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tileInstanceKey = new NamespacedKey(plugin, "tile_instance");
        this.tileIdKey = new NamespacedKey(plugin, "tile_id");
        this.tileNonceKey = new NamespacedKey(plugin, "tile_nonce");
        this.tileSignatureKey = new NamespacedKey(plugin, "tile_sig");
        this.signatureSalt = new byte[16];
        new SecureRandom().nextBytes(signatureSalt);
    }

    public HandInventory createHandInventory(UUID playerId) {
        return new HandInventory(playerId);
    }

    public void renderHand(Inventory inventory, PlayerState state) {
        inventory.clear();
        List<Tile> tiles = new ArrayList<>(state.getHand().getConcealed());
        tiles.sort(Comparator
                .comparingInt((Tile tile) -> tileSortKey(tile.getId()))
                .thenComparing(tile -> tile.getId().isRed() ? 1 : 0));
        for (int i = 0; i < tiles.size() && i < inventory.getSize(); i++) {
            inventory.setItem(i, createTileItem(tiles.get(i)));
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
        String tileIdRaw = container.get(tileIdKey, PersistentDataType.STRING);
        Integer nonce = container.get(tileNonceKey, PersistentDataType.INTEGER);
        String signature = container.get(tileSignatureKey, PersistentDataType.STRING);
        if (tileIdRaw == null || nonce == null || signature == null) {
            return Optional.empty();
        }
        for (Tile tile : state.getHand().getConcealed()) {
            if (tile.getInstanceId() == instance) {
                if (!tile.getId().toShortString().equals(tileIdRaw)) {
                    return Optional.empty();
                }
                if (!verifySignature(tileIdRaw, instance, nonce, signature)) {
                    return Optional.empty();
                }
                return Optional.of(tile);
            }
        }
        return Optional.empty();
    }

    public ItemStack createTileItem(Tile tile) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(tile.getId().toShortString()));
        if (plugin.getConfig().getBoolean("resourcePack.enabled", false)) {
            NamespacedKey modelKey = TileItemModel.resolve(plugin, tile.getId());
            if (modelKey != null) {
                meta.setItemModel(modelKey);
            }
        }
        meta.getPersistentDataContainer().set(tileInstanceKey, PersistentDataType.INTEGER, tile.getInstanceId());
        meta.getPersistentDataContainer().set(tileIdKey, PersistentDataType.STRING, tile.getId().toShortString());
        int nonce = ThreadLocalRandom.current().nextInt();
        String signature = sign(tile.getId().toShortString(), tile.getInstanceId(), nonce);
        meta.getPersistentDataContainer().set(tileNonceKey, PersistentDataType.INTEGER, nonce);
        meta.getPersistentDataContainer().set(tileSignatureKey, PersistentDataType.STRING, signature);
        item.setItemMeta(meta);
        return item;
    }

    private String sign(String tileId, int instanceId, int nonce) {
        MessageDigest digest = newDigest();
        digest.update(signatureSalt);
        digest.update((byte) ':');
        digest.update(tileId.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) ':');
        byte[] buffer = new byte[8];
        buffer[0] = (byte) (instanceId >>> 24);
        buffer[1] = (byte) (instanceId >>> 16);
        buffer[2] = (byte) (instanceId >>> 8);
        buffer[3] = (byte) instanceId;
        buffer[4] = (byte) (nonce >>> 24);
        buffer[5] = (byte) (nonce >>> 16);
        buffer[6] = (byte) (nonce >>> 8);
        buffer[7] = (byte) nonce;
        digest.update(buffer);
        return HEX.formatHex(digest.digest());
    }

    private boolean verifySignature(String tileId, int instanceId, int nonce, String signature) {
        return sign(tileId, instanceId, nonce).equals(signature);
    }

    private MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private int tileSortKey(TileId id) {
        int suitBase;
        switch (id.getSuit()) {
            case MAN:
                suitBase = 0;
                break;
            case PIN:
                suitBase = 9;
                break;
            case SOU:
                suitBase = 18;
                break;
            case HONOR:
            default:
                suitBase = 27;
                break;
        }
        return suitBase + id.getRank();
    }
}
