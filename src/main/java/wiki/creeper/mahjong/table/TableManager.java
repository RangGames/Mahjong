package wiki.creeper.mahjong.table;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
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

    public boolean showRoomMenu(Player player) {
        if (player == null) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("ui.enableDialogs", false)) {
            return false;
        }
        Optional<GameTable> current = getTableByPlayer(player);
        if (current.isPresent() && current.get().isRoomMode()) {
            current.get().showRoomLobby(player);
            return true;
        }
        boolean alreadyInTable = current.isPresent();
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("방을 만들거나 입장할 수 있어요.", NamedTextColor.GRAY)));
        body.add(DialogBody.plainMessage(Component.text("입장하려면 방 코드를 입력해 주세요.", NamedTextColor.DARK_GRAY)));
        DialogInput codeInput = DialogInput.text("room_code", Component.text("방 코드", NamedTextColor.AQUA))
                .labelVisible(true)
                .maxLength(8)
                .width(160)
                .build();
        List<DialogInput> inputs = List.of(codeInput);

        UUID targetId = player.getUniqueId();
        List<ActionButton> actions = new ArrayList<>();
        boolean canCreate = !alreadyInTable;
        DialogAction createAction = canCreate ? DialogAction.customClick((view, audience) -> {
            if (!(audience instanceof Player clicker)) {
                return;
            }
            if (!clicker.getUniqueId().equals(targetId)) {
                return;
            }
            if (getTableByPlayer(clicker).isPresent()) {
                clicker.sendMessage("이미 다른 테이블에 참여 중이에요. 먼저 나가주세요.");
                return;
            }
            GameTable room = createRoom(clicker);
            clicker.sendMessage("방을 만들었어요. 코드: " + room.getRoomCode());
            room.showRoomLobby(clicker);
        }, ClickCallback.Options.builder().uses(1).build()) : null;
        actions.add(ActionButton.create(
                Component.text("방 만들기", canCreate ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY),
                Component.text(canCreate ? "새 방을 만들어요" : "먼저 현재 테이블에서 나가주세요", NamedTextColor.GRAY),
                180,
                createAction
        ));
        boolean canJoin = !alreadyInTable;
        DialogAction joinAction = canJoin ? DialogAction.customClick((view, audience) -> {
            if (!(audience instanceof Player clicker)) {
                return;
            }
            if (!clicker.getUniqueId().equals(targetId)) {
                return;
            }
            if (getTableByPlayer(clicker).isPresent()) {
                clicker.sendMessage("이미 다른 테이블에 참여 중이에요. 먼저 나가주세요.");
                return;
            }
            String code = view.getText("room_code");
            if (code == null || code.isBlank()) {
                clicker.sendMessage("방 코드를 입력해 주세요.");
                return;
            }
            String trimmed = code.trim();
            if (!joinRoom(clicker, trimmed)) {
                clicker.sendMessage("방을 찾을 수 없어요: " + trimmed);
                return;
            }
            clicker.sendMessage("방에 참가했어요: " + trimmed.toUpperCase(Locale.ROOT));
            getTableByPlayer(clicker).ifPresent(room -> room.showRoomLobby(clicker));
        }, ClickCallback.Options.builder().uses(1).build()) : null;
        actions.add(ActionButton.create(
                Component.text("방 입장", canJoin ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY),
                Component.text(canJoin ? "코드로 입장해요" : "먼저 현재 테이블에서 나가주세요", NamedTextColor.GRAY),
                180,
                joinAction
        ));
        actions.add(ActionButton.create(
                Component.text("닫기", NamedTextColor.DARK_GRAY),
                Component.text("창 닫기", NamedTextColor.GRAY),
                120,
                null
        ));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("방 메뉴", NamedTextColor.GOLD))
                        .body(body)
                        .inputs(inputs)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(actions).build())
        );
        player.showDialog(dialog);
        return true;
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
