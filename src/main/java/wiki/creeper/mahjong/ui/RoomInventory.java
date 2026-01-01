package wiki.creeper.mahjong.ui;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class RoomInventory implements InventoryHolder {
    public static final int SIZE = 27;

    public static final int SLOT_LOBBY_INFO = 4;
    public static final int SLOT_LOBBY_EAST = 10;
    public static final int SLOT_LOBBY_SOUTH = 12;
    public static final int SLOT_LOBBY_WEST = 14;
    public static final int SLOT_LOBBY_NORTH = 16;
    public static final int SLOT_LOBBY_READY = 19;
    public static final int SLOT_LOBBY_START = 21;
    public static final int SLOT_LOBBY_RULES = 23;
    public static final int SLOT_LOBBY_LEAVE = 25;

    public static final int SLOT_RULES_INFO = 4;
    public static final int SLOT_RULES_RED_DORA = 10;
    public static final int SLOT_RULES_OPEN_TANYAO = 11;
    public static final int SLOT_RULES_IPPATSU = 12;
    public static final int SLOT_RULES_URA_DORA = 13;
    public static final int SLOT_RULES_BOTS = 14;
    public static final int SLOT_RULES_COACH = 15;
    public static final int SLOT_RULES_COACH_RANK = 16;
    public static final int SLOT_RULES_PRESET_DEFAULT = 19;
    public static final int SLOT_RULES_PRESET_KUITAN = 20;
    public static final int SLOT_RULES_PRESET_CLASSIC = 21;
    public static final int SLOT_RULES_BACK = 25;

    private final UUID tableId;
    private final RoomMenuType type;
    private final Inventory inventory;

    public RoomInventory(UUID tableId, RoomMenuType type, String title) {
        this.tableId = tableId;
        this.type = type;
        this.inventory = Bukkit.createInventory(this, SIZE, title);
    }

    public UUID getTableId() {
        return tableId;
    }

    public RoomMenuType getType() {
        return type;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
