package wiki.creeper.mahjong.table;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import wiki.creeper.mahjong.ai.BotDifficulty;
import wiki.creeper.mahjong.ai.BotProfile;
import wiki.creeper.mahjong.game.GameRules;
import wiki.creeper.mahjong.game.GameState;
import wiki.creeper.mahjong.game.SeatWind;
import wiki.creeper.mahjong.ui.RoomInventory;
import wiki.creeper.mahjong.ui.RoomMenuType;

final class GameTableRoomUi {
    private static final int MAX_PLAYERS = GameTable.MAX_PLAYERS;

    private final GameTable table;
    private final JavaPlugin plugin;
    private final List<UUID> players;
    private final Map<SeatWind, UUID> seatAssignments;
    private final Map<UUID, SeatWind> playerSeats;
    private final Set<UUID> readyPlayers;
    private final Set<UUID> coachPlayers;
    private final Map<UUID, BotProfile> bots;
    private RoomInventory lobbyMenu;
    private RoomInventory rulesMenu;
    private BossBar roomBossBar;
    private BossBar roomRulesBossBar;

    GameTableRoomUi(GameTable table) {
        this.table = table;
        this.plugin = table.getPlugin();
        this.players = table.getPlayers();
        this.seatAssignments = table.getSeatAssignments();
        this.playerSeats = table.getPlayerSeats();
        this.readyPlayers = table.getReadyPlayers();
        this.coachPlayers = table.getCoachPlayers();
        this.bots = table.getBots();
    }

    List<String> getRoomStatusLines() {
        List<String> lines = new ArrayList<>();
        String code = table.getRoomCode() == null ? "-" : table.getRoomCode();
        String hostName = table.getHostId() == null ? "-" : resolveName(table.getHostId());
        lines.add("테이블" + code + " / 호스트 " + hostName + " / 인원=" + players.size() + "/" + MAX_PLAYERS);
        lines.add("좌석: " + seatAssignments.size() + "/" + MAX_PLAYERS
                + " / 준비 " + getReadyCount() + "/" + players.size());
        lines.add("규칙: " + describeRules(getRulesSnapshot()));
        String seatSummary = buildSeatSummaryLine();
        if (!seatSummary.isEmpty()) {
            lines.add("좌석 배치: " + seatSummary);
        }
        return lines;
    }

    void openRoomLobbyGui(Player player) {
        if (player == null || !roomGuiEnabled()) {
            return;
        }
        updateRoomLobbyMenu();
        player.openInventory(getLobbyMenu().getInventory());
    }

    void openRoomRulesGui(Player player) {
        if (player == null || !roomGuiEnabled()) {
            return;
        }
        updateRoomRulesMenu();
        player.openInventory(getRulesMenu().getInventory());
    }

    void showRoomRules(Player player) {
        if (player == null) {
            return;
        }
        if (!table.isRoomMode()) {
            player.sendMessage("테이블이 로비 모드가 아니에요.");
            return;
        }
        if (!isHost(player.getUniqueId())) {
            player.sendMessage("호스트만 규칙을 변경할 수 있어요.");
            return;
        }
        if (table.getState() != GameState.LOBBY) {
            player.sendMessage("아직 게임이 시작되어 있어요.");
            return;
        }
        if (roomGuiEnabled()) {
            openRoomRulesGui(player);
            return;
        }
        if (table.getDialogs().isEnabled()) {
            showRoomRulesDialog(player);
            return;
        }
        sendRoomRulesSummary(player);
    }

    void showRoomLobby(Player player) {
        if (player == null) {
            return;
        }
        if (!table.isRoomMode()) {
            player.sendMessage("테이블이 로비 모드가 아니에요.");
            return;
        }
        if (table.getState() != GameState.LOBBY) {
            player.sendMessage("아직 게임이 시작되어 있어요.");
            return;
        }
        updateRoomLobbyUi();
        if (roomGuiEnabled()) {
            openRoomLobbyGui(player);
            return;
        }
        if (table.getDialogs().isEnabled()) {
            showRoomLobbyDialog(player);
            return;
        }
        for (String line : getRoomStatusLines()) {
            player.sendMessage(line);
        }
    }

    private void showRoomLobbyDialog(Player player) {
        if (player == null) {
            return;
        }
        List<DialogBody> body = new ArrayList<>();
        String code = table.getRoomCode() == null ? "-" : table.getRoomCode();
        String hostName = table.getHostId() == null ? "-" : resolveName(table.getHostId());
        body.add(DialogBody.plainMessage(Component.text("테이블 코드: " + code, NamedTextColor.GRAY)));
        body.add(DialogBody.plainMessage(Component.text("호스트: " + hostName, NamedTextColor.GRAY)));
        body.add(DialogBody.plainMessage(Component.text("인원: " + players.size() + "/" + MAX_PLAYERS, NamedTextColor.GRAY)));
        body.add(DialogBody.plainMessage(Component.text("좌석: " + seatAssignments.size() + "/" + MAX_PLAYERS, NamedTextColor.GRAY)));
        body.add(DialogBody.plainMessage(Component.text("준비: " + getReadyCount() + "/" + players.size(), NamedTextColor.GRAY)));
        body.add(DialogBody.plainMessage(Component.text("규칙: " + describeRules(getRulesSnapshot()), NamedTextColor.GOLD)));
        for (SeatWind seat : SeatWind.values()) {
            UUID occupant = seatAssignments.get(seat);
            String seatName = seatLabel(seat);
            String occupantName;
            NamedTextColor color;
            if (occupant == null) {
                occupantName = "빈자리";
                color = NamedTextColor.DARK_GRAY;
            } else {
                String status = buildPlayerStatusTag(occupant);
                occupantName = resolveName(occupant) + (status.isEmpty() ? "" : " (" + status + ")");
                color = isBot(occupant) ? NamedTextColor.AQUA
                        : (readyPlayers.contains(occupant) ? NamedTextColor.GREEN : NamedTextColor.WHITE);
            }
            body.add(DialogBody.plainMessage(Component.text(seatName + ": " + occupantName, color)));
        }
        List<ActionButton> actions = new ArrayList<>();
        UUID targetId = player.getUniqueId();
        for (SeatWind seat : SeatWind.values()) {
            UUID occupant = seatAssignments.get(seat);
            boolean mine = occupant != null && occupant.equals(targetId);
            boolean available = occupant == null || mine;
            NamedTextColor color = mine ? NamedTextColor.GREEN : (occupant == null ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY);
            String label = "좌석 " + seatLabel(seat) + (mine ? " (내 자리)" : "");
            DialogAction action = available
                    ? dialogAction(targetId, clicker -> {
                        assignSeat(clicker, seat);
                        showRoomLobbyDialog(clicker);
                    })
                    : null;
            actions.add(ActionButton.create(
                    Component.text(label, color),
                    Component.text(occupant == null ? "좌석 선택" : (mine ? "좌석 비우기" : "사용 중"), NamedTextColor.GRAY),
                    170,
                    action
            ));
        }
        SeatWind mySeat = playerSeats.get(targetId);
        if (mySeat != null) {
            actions.add(ActionButton.create(
                    Component.text("좌석 비우기", NamedTextColor.RED),
                    Component.text("좌석 해제", NamedTextColor.GRAY),
                    160,
                    dialogAction(targetId, clicker -> {
                        clearSeat(targetId, true);
                        showRoomLobbyDialog(clicker);
                    })
            ));
        }
        boolean isReady = readyPlayers.contains(targetId);
        boolean canReady = playerSeats.containsKey(targetId);
        actions.add(ActionButton.create(
                Component.text(isReady ? "준비 해제" : "준비", isReady ? NamedTextColor.RED : NamedTextColor.GREEN),
                Component.text(canReady ? "준비 상태 변경" : "좌석을 먼저 선택해 주세요", NamedTextColor.GRAY),
                160,
                canReady ? dialogAction(targetId, clicker -> {
                    toggleReady(clicker);
                    showRoomLobbyDialog(clicker);
                }) : null
        ));
        boolean coachAllowed = isCoachAllowed();
        boolean coachOn = coachPlayers.contains(targetId);
        String coachLabel = coachAllowed ? (coachOn ? "코치 ON" : "코치 OFF") : "코치 불가";
        NamedTextColor coachColor = coachAllowed ? (coachOn ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY) : NamedTextColor.RED;
        String coachHint = coachAllowed ? "코치 켜기" : "현재 테이블에서는 코치가 비활성화돼요";
        DialogAction coachAction = coachAllowed
                ? dialogAction(targetId, clicker -> {
                    setCoach(clicker, !coachOn);
                    showRoomLobbyDialog(clicker);
                })
                : null;
        actions.add(ActionButton.create(
                Component.text(coachLabel, coachColor),
                Component.text(coachHint, NamedTextColor.GRAY),
                160,
                coachAction
        ));
        if (isHost(targetId)) {
            actions.add(ActionButton.create(
                    Component.text("규칙", NamedTextColor.AQUA),
                    Component.text("테이블 규칙 설정", NamedTextColor.GRAY),
                    160,
                    dialogAction(targetId, clicker -> showRoomRules(clicker))
            ));
            actions.add(ActionButton.create(
                    Component.text("봇", NamedTextColor.LIGHT_PURPLE),
                    Component.text("봇 관리", NamedTextColor.GRAY),
                    160,
                    dialogAction(targetId, clicker -> showBotDialog(clicker))
            ));
            boolean canStart = table.getState() == GameState.LOBBY && players.size() == MAX_PLAYERS && areAllReady();
            actions.add(ActionButton.create(
                    Component.text(canStart ? "시작" : "시작 (모두 준비 필요)", NamedTextColor.GOLD),
                    Component.text("게임 시작", NamedTextColor.GRAY),
                    200,
                    canStart ? dialogAction(targetId, clicker -> requestStart(clicker)) : null
            ));
        }
        actions.add(ActionButton.create(
                Component.text("로비로 갱신", NamedTextColor.GRAY),
                Component.text("로비 새로고침", NamedTextColor.GRAY),
                140,
                dialogAction(targetId, clicker -> showRoomLobbyDialog(clicker))
        ));
        actions.add(ActionButton.create(
                Component.text("닫기", NamedTextColor.DARK_GRAY),
                Component.text("창 닫기", NamedTextColor.GRAY),
                120,
                null
        ));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("테이블 로비", NamedTextColor.AQUA))
                        .body(body)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(actions).build())
        );
        player.showDialog(dialog);
    }

    private void showRoomRulesDialog(Player player) {
        if (player == null) {
            return;
        }
        GameRules current = getRulesSnapshot();
        List<DialogBody> body = new ArrayList<>();
        String code = table.getRoomCode() == null ? "-" : table.getRoomCode();
        body.add(DialogBody.plainMessage(Component.text("테이블 코드: " + code, NamedTextColor.GRAY)));
        body.add(DialogBody.plainMessage(Component.text("규칙", NamedTextColor.GOLD)));
        body.add(DialogBody.plainMessage(Component.text(formatRuleLine("빨간 도라", current.isRedDoraEnabled()), NamedTextColor.WHITE)));
        body.add(DialogBody.plainMessage(Component.text(formatRuleLine("오픈 탄야오", current.isOpenTanyaoEnabled()), NamedTextColor.WHITE)));
        body.add(DialogBody.plainMessage(Component.text(formatRuleLine("일발", current.isIppatsuEnabled()), NamedTextColor.WHITE)));
        body.add(DialogBody.plainMessage(Component.text(formatRuleLine("우라 도라", current.isUraDoraEnabled()), NamedTextColor.WHITE)));
        body.add(DialogBody.plainMessage(Component.text("옵션", NamedTextColor.GOLD)));
        body.add(DialogBody.plainMessage(Component.text(formatRuleLine("봇", table.areBotsEnabled()), NamedTextColor.WHITE)));
        body.add(DialogBody.plainMessage(Component.text(formatRuleLine("코치", table.isCoachEnabled()), NamedTextColor.WHITE)));
        body.add(DialogBody.plainMessage(Component.text(formatRuleLine("코치 랭크 잠금", table.isCoachRankDisabled()), NamedTextColor.WHITE)));

        List<ActionButton> actions = new ArrayList<>();
        UUID targetId = player.getUniqueId();
        actions.add(buildRuleToggleButton(targetId, "빨간 도라", current.isRedDoraEnabled(), "redDora"));
        actions.add(buildRuleToggleButton(targetId, "오픈 탄야오", current.isOpenTanyaoEnabled(), "openTanyao"));
        actions.add(buildRuleToggleButton(targetId, "일발", current.isIppatsuEnabled(), "ippatsu"));
        actions.add(buildRuleToggleButton(targetId, "우라 도라", current.isUraDoraEnabled(), "uraDora"));
        actions.add(buildRuleToggleButton(targetId, "봇", table.areBotsEnabled(), "bots"));
        actions.add(buildRuleToggleButton(targetId, "코치", table.isCoachEnabled(), "coach"));
        actions.add(buildRuleToggleButton(targetId, "코치 랭크", table.isCoachRankDisabled(), "coachRank"));
        actions.add(buildPresetButton(targetId, "기본 프리셋", "default"));
        actions.add(buildPresetButton(targetId, "쿠이탄 프리셋", "kuitan"));
        actions.add(buildPresetButton(targetId, "클래식 프리셋", "classic"));
        actions.add(ActionButton.create(
                Component.text("로비", NamedTextColor.GRAY),
                Component.text("로비로 돌아가기", NamedTextColor.GRAY),
                120,
                dialogAction(targetId, clicker -> showRoomLobbyDialog(clicker))
        ));
        actions.add(ActionButton.create(
                Component.text("닫기", NamedTextColor.DARK_GRAY),
                Component.text("창 닫기", NamedTextColor.GRAY),
                120,
                null
        ));

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("테이블 규칙", NamedTextColor.AQUA))
                        .body(body)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(actions).build())
        );
        player.showDialog(dialog);
    }

    private void showBotDialog(Player player) {
        if (player == null) {
            return;
        }
        if (!table.isRoomMode()) {
            player.sendMessage("테이블이 로비 모드가 아니에요.");
            return;
        }
        if (!isHost(player.getUniqueId())) {
            player.sendMessage("호스트만 봇을 관리할 수 있어요.");
            return;
        }
        if (table.getState() != GameState.LOBBY) {
            player.sendMessage("이미 게임이 시작되어 있어요.");
            return;
        }
        if (!table.getDialogs().isEnabled()) {
            player.sendMessage("봇 관리는 /mj bot add|remove <난이도> 를 사용해 주세요.");
            return;
        }
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("봇 " + onOff(table.areBotsEnabled()), NamedTextColor.GRAY)));
        body.add(DialogBody.plainMessage(Component.text("인원: " + players.size() + "/" + MAX_PLAYERS, NamedTextColor.GRAY)));
        if (bots.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text("봇 없음", NamedTextColor.DARK_GRAY)));
        } else {
            body.add(DialogBody.plainMessage(Component.text("봇 목록", NamedTextColor.GOLD)));
            for (BotProfile profile : bots.values()) {
                body.add(DialogBody.plainMessage(Component.text("- " + profile.getName() + " (" + profile.getDifficulty() + ")",
                        NamedTextColor.WHITE)));
            }
        }
        List<ActionButton> actions = new ArrayList<>();
        UUID targetId = player.getUniqueId();
        boolean canAdd = table.areBotsEnabled() && players.size() < MAX_PLAYERS;
        actions.add(buildBotActionButton(targetId, "봇 추가 (초급)", BotDifficulty.BEGINNER, canAdd));
        actions.add(buildBotActionButton(targetId, "봇 추가 (중급)", BotDifficulty.NORMAL, canAdd));
        actions.add(buildBotActionButton(targetId, "봇 추가 (상급)", BotDifficulty.HARD, canAdd));
        actions.add(buildBotRemoveButton(targetId, "봇 제거 (초급)", BotDifficulty.BEGINNER));
        actions.add(buildBotRemoveButton(targetId, "봇 제거 (중급)", BotDifficulty.NORMAL));
        actions.add(buildBotRemoveButton(targetId, "봇 제거 (상급)", BotDifficulty.HARD));
        actions.add(ActionButton.create(
                Component.text("로비", NamedTextColor.GRAY),
                Component.text("로비로 돌아가기", NamedTextColor.GRAY),
                120,
                dialogAction(targetId, clicker -> showRoomLobbyDialog(clicker))
        ));
        actions.add(ActionButton.create(
                Component.text("닫기", NamedTextColor.DARK_GRAY),
                Component.text("창 닫기", NamedTextColor.GRAY),
                120,
                null
        ));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("봇 관리", NamedTextColor.LIGHT_PURPLE))
                        .body(body)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(actions).build())
        );
        player.showDialog(dialog);
    }

    private ActionButton buildBotActionButton(UUID targetId, String label, BotDifficulty difficulty, boolean enabled) {
        DialogAction action = enabled ? dialogAction(targetId, clicker -> {
            addBot(difficulty);
            showBotDialog(clicker);
        }) : null;
        NamedTextColor color = enabled ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY;
        String hint = enabled ? "봇 추가" : "봇 비활성화 또는 인원 가득 참";
        return ActionButton.create(Component.text(label, color), Component.text(hint, NamedTextColor.GRAY), 160, action);
    }

    private ActionButton buildBotRemoveButton(UUID targetId, String label, BotDifficulty difficulty) {
        boolean enabled = hasBotDifficulty(difficulty);
        DialogAction action = enabled ? dialogAction(targetId, clicker -> {
            removeBot(difficulty);
            showBotDialog(clicker);
        }) : null;
        NamedTextColor color = enabled ? NamedTextColor.RED : NamedTextColor.DARK_GRAY;
        String hint = enabled ? "봇 제거" : "해당 봇 없음";
        return ActionButton.create(Component.text(label, color), Component.text(hint, NamedTextColor.GRAY), 160, action);
    }

    private ActionButton buildRuleToggleButton(UUID targetId, String label, boolean enabled, String ruleKey) {
        NamedTextColor color = enabled ? NamedTextColor.GREEN : NamedTextColor.RED;
        String state = enabled ? "ON" : "OFF";
        DialogAction action = dialogAction(targetId, clicker -> {
            updateRule(ruleKey, null);
            showRoomRulesDialog(clicker);
        });
        return ActionButton.create(
                Component.text(label + " " + state, color),
                Component.text(label + " 변경", NamedTextColor.GRAY),
                180,
                action
        );
    }

    private ActionButton buildPresetButton(UUID targetId, String label, String presetKey) {
        DialogAction action = dialogAction(targetId, clicker -> {
            applyPreset(presetKey);
            showRoomRulesDialog(clicker);
        });
        return ActionButton.create(
                Component.text(label, NamedTextColor.GOLD),
                Component.text("프리셋 적용", NamedTextColor.GRAY),
                180,
                action
        );
    }

    void broadcastRoomRules() {
        if (!table.isRoomMode()) {
            return;
        }
        broadcast("테이블 규칙: " + describeRules(getRulesSnapshot()));
        updateRoomLobbyUi();
    }

    void updateRoomLobbyUi() {
        if (!table.isRoomMode() || table.getState() != GameState.LOBBY) {
            clearRoomBossBar();
            return;
        }
        updateRoomBossBar();
        String status = buildRoomStatusLine();
        if (!roomGuiEnabled()) {
            String seatSummary = buildSeatSummaryLine();
            if (!seatSummary.isEmpty()) {
                status = seatSummary;
            }
        }
        if (!roomGuiEnabled()) {
            for (UUID playerId : players) {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) {
                    player.sendActionBar(status);
                }
            }
        }
        if (roomGuiEnabled()) {
            updateRoomLobbyMenu();
            updateRoomRulesMenu();
        }
    }

    private boolean roomGuiEnabled() {
        return plugin.getConfig().getBoolean("ui.roomGui", true);
    }

    private RoomInventory getLobbyMenu() {
        if (lobbyMenu == null) {
            lobbyMenu = new RoomInventory(table.getId(), RoomMenuType.LOBBY, "마작 테이블");
        }
        return lobbyMenu;
    }

    private RoomInventory getRulesMenu() {
        if (rulesMenu == null) {
            rulesMenu = new RoomInventory(table.getId(), RoomMenuType.RULES, "테이블 규칙");
        }
        return rulesMenu;
    }

    private void updateRoomLobbyMenu() {
        if (!table.isRoomMode() || table.getState() != GameState.LOBBY) {
            return;
        }
        Inventory inventory = getLobbyMenu().getInventory();
        inventory.clear();
        String code = table.getRoomCode() == null ? "-" : table.getRoomCode();
        String hostName = table.getHostId() == null ? "-" : resolveName(table.getHostId());
        List<String> infoLore = new ArrayList<>();
        infoLore.add("호스트: " + hostName);
        infoLore.add("인원: " + players.size() + "/" + MAX_PLAYERS);
        infoLore.add("준비: " + getReadyCount() + "/" + players.size());
        infoLore.add("규칙: " + describeRules(getRulesSnapshot()));
        inventory.setItem(RoomInventory.SLOT_LOBBY_INFO,
                buildMenuItem(Material.PAPER, "테이블 코드: " + code, NamedTextColor.GOLD, infoLore));
        inventory.setItem(RoomInventory.SLOT_LOBBY_EAST, buildSeatItem(SeatWind.EAST));
        inventory.setItem(RoomInventory.SLOT_LOBBY_SOUTH, buildSeatItem(SeatWind.SOUTH));
        inventory.setItem(RoomInventory.SLOT_LOBBY_WEST, buildSeatItem(SeatWind.WEST));
        inventory.setItem(RoomInventory.SLOT_LOBBY_NORTH, buildSeatItem(SeatWind.NORTH));
        inventory.setItem(RoomInventory.SLOT_LOBBY_READY, buildReadyItem());
        inventory.setItem(RoomInventory.SLOT_LOBBY_START, buildStartItem());
        inventory.setItem(RoomInventory.SLOT_LOBBY_RULES, buildRulesItem());
        inventory.setItem(RoomInventory.SLOT_LOBBY_LEAVE, buildLeaveItem());
    }

    private void updateRoomRulesMenu() {
        if (!table.isRoomMode() || table.getState() != GameState.LOBBY) {
            return;
        }
        Inventory inventory = getRulesMenu().getInventory();
        inventory.clear();
        GameRules snapshot = getRulesSnapshot();
        String code = table.getRoomCode() == null ? "-" : table.getRoomCode();
        String hostName = table.getHostId() == null ? "-" : resolveName(table.getHostId());
        List<String> infoLore = new ArrayList<>();
        infoLore.add("호스트: " + hostName);
        infoLore.add("인원: " + players.size() + "/" + MAX_PLAYERS);
        infoLore.add("준비: " + getReadyCount() + "/" + players.size());
        inventory.setItem(RoomInventory.SLOT_RULES_INFO,
                buildMenuItem(Material.PAPER, "테이블 코드: " + code, NamedTextColor.GOLD, infoLore));
        inventory.setItem(RoomInventory.SLOT_RULES_RED_DORA, buildToggleItem("빨간 도라", snapshot.isRedDoraEnabled()));
        inventory.setItem(RoomInventory.SLOT_RULES_OPEN_TANYAO, buildToggleItem("오픈 탄야오", snapshot.isOpenTanyaoEnabled()));
        inventory.setItem(RoomInventory.SLOT_RULES_IPPATSU, buildToggleItem("일발", snapshot.isIppatsuEnabled()));
        inventory.setItem(RoomInventory.SLOT_RULES_URA_DORA, buildToggleItem("우라 도라", snapshot.isUraDoraEnabled()));
        inventory.setItem(RoomInventory.SLOT_RULES_BOTS, buildToggleItem("봇 사용", table.areBotsEnabled()));
        inventory.setItem(RoomInventory.SLOT_RULES_COACH, buildToggleItem("코치", table.isCoachEnabled()));
        inventory.setItem(RoomInventory.SLOT_RULES_COACH_RANK, buildToggleItem("코치 랭크 잠금", table.isCoachRankDisabled()));
        inventory.setItem(RoomInventory.SLOT_RULES_PRESET_DEFAULT, buildPresetItem("기본 프리셋"));
        inventory.setItem(RoomInventory.SLOT_RULES_PRESET_KUITAN, buildPresetItem("쿠이탄 프리셋"));
        inventory.setItem(RoomInventory.SLOT_RULES_PRESET_CLASSIC, buildPresetItem("클래식 프리셋"));
        inventory.setItem(RoomInventory.SLOT_RULES_BACK,
                buildMenuItem(Material.ARROW, "로비로", NamedTextColor.GRAY, List.of("클릭: 로비로 이동")));
    }

    private ItemStack buildMenuItem(Material material, String title, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(title, color));
        if (lore != null && !lore.isEmpty()) {
            List<Component> loreLines = new ArrayList<>();
            for (String line : lore) {
                loreLines.add(Component.text(line, NamedTextColor.GRAY));
            }
            meta.lore(loreLines);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildSeatItem(SeatWind seat) {
        UUID occupant = seatAssignments.get(seat);
        if (occupant == null) {
            return buildMenuItem(Material.GRAY_STAINED_GLASS_PANE,
                    seatLabel(seat) + ": 빈자리",
                    NamedTextColor.DARK_GRAY,
                    List.of("클릭: 좌석 선택"));
        }
        boolean bot = isBot(occupant);
        boolean ready = bot || readyPlayers.contains(occupant);
        Material material = bot ? Material.CYAN_WOOL : (ready ? Material.LIME_WOOL : Material.YELLOW_WOOL);
        NamedTextColor color = bot ? NamedTextColor.AQUA : (ready ? NamedTextColor.GREEN : NamedTextColor.YELLOW);
        List<String> lore = new ArrayList<>();
        lore.add("상태: " + (ready ? "준비" : "대기"));
        if (bot) {
            lore.add("봇");
        }
        if (table.getHostId() != null && table.getHostId().equals(occupant)) {
            lore.add("호스트");
        }
        lore.add("클릭: 좌석 선택/해제");
        return buildMenuItem(material, seatLabel(seat) + ": " + resolveName(occupant), color, lore);
    }

    private ItemStack buildReadyItem() {
        return buildMenuItem(Material.SLIME_BALL,
                "준비/ 취소",
                NamedTextColor.GREEN,
                List.of("클릭: 준비 상태 변경", "좌석 선택 후 사용"));
    }

    private ItemStack buildStartItem() {
        boolean canStart = players.size() >= MAX_PLAYERS && areAllReady();
        Material material = canStart ? Material.NETHER_STAR : Material.GRAY_DYE;
        NamedTextColor color = canStart ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY;
        String state = canStart ? "클릭: 게임 시작" : "시작 조건 미충족";
        return buildMenuItem(material,
                "게임 시작 (호스트 전용)",
                color,
                List.of(state));
    }

    private ItemStack buildRulesItem() {
        return buildMenuItem(Material.BOOK,
                "규칙 설정 (호스트 전용)",
                NamedTextColor.AQUA,
                List.of("클릭: 규칙 화면"));
    }

    private ItemStack buildLeaveItem() {
        return buildMenuItem(Material.BARRIER,
                "테이블 나가기",
                NamedTextColor.RED,
                List.of("클릭: 테이블에서 나가기"));
    }

    private ItemStack buildToggleItem(String label, boolean enabled) {
        String state = enabled ? "켜짐" : "꺼짐";
        Material material = enabled ? Material.LIME_DYE : Material.RED_DYE;
        NamedTextColor color = enabled ? NamedTextColor.GREEN : NamedTextColor.RED;
        return buildMenuItem(material,
                label + ": " + state,
                color,
                List.of("클릭: 변경 (호스트 전용)"));
    }

    private ItemStack buildPresetItem(String label) {
        return buildMenuItem(Material.NOTE_BLOCK,
                label,
                NamedTextColor.GOLD,
                List.of("클릭: 프리셋 적용"));
    }

    private void updateRoomBossBar() {
        if (!table.isRoomMode() || table.getState() != GameState.LOBBY) {
            clearRoomBossBar();
            return;
        }
        if (roomBossBar == null) {
            roomBossBar = Bukkit.createBossBar(buildRoomBossBarTitle(), BarColor.BLUE, BarStyle.SEGMENTED_10);
        }
        if (roomRulesBossBar == null) {
            roomRulesBossBar = Bukkit.createBossBar(buildRoomRulesLine(), BarColor.GREEN, BarStyle.SOLID);
        }
        roomBossBar.setTitle(buildRoomBossBarTitle());
        roomRulesBossBar.setTitle(buildRoomRulesLine());
        double progress = players.isEmpty() ? 0.0 : (double) readyPlayers.size() / players.size();
        roomBossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        roomRulesBossBar.setProgress(1.0);
        roomBossBar.removeAll();
        roomRulesBossBar.removeAll();
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                roomBossBar.addPlayer(player);
                roomRulesBossBar.addPlayer(player);
            }
        }
        roomBossBar.setVisible(true);
        roomRulesBossBar.setVisible(true);
    }

    void clearRoomBossBar() {
        if (roomBossBar != null) {
            roomBossBar.removeAll();
            roomBossBar.setVisible(false);
            roomBossBar = null;
        }
        if (roomRulesBossBar != null) {
            roomRulesBossBar.removeAll();
            roomRulesBossBar.setVisible(false);
            roomRulesBossBar = null;
        }
    }

    private String buildRoomBossBarTitle() {
        return buildRoomStatusLine();
    }

    private String buildRoomStatusLine() {
        String code = table.getRoomCode() == null ? "-" : table.getRoomCode();
        String hostName = table.getHostId() == null ? "-" : resolveName(table.getHostId());
        return "테이블" + code
                + " | 인원 " + players.size() + "/" + MAX_PLAYERS
                + " | 좌석 " + seatAssignments.size() + "/" + MAX_PLAYERS
                + " | 준비 " + getReadyCount() + "/" + players.size()
                + " | 호스트 " + hostName;
    }

    private String buildRoomRulesLine() {
        return describeRules(getRulesSnapshot());
    }

    private String buildSeatSummaryLine() {
        if (seatAssignments.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (SeatWind seat : SeatWind.values()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            UUID occupant = seatAssignments.get(seat);
            String seatName = seatLabel(seat);
            if (occupant == null) {
                sb.append(seatName).append(": 빈자리");
                continue;
            }
            String status = buildPlayerStatusTag(occupant);
            sb.append(seatName).append(": ").append(resolveName(occupant));
            if (!status.isEmpty()) {
                sb.append(" (").append(status).append(")");
            }
        }
        return sb.toString();
    }

    private String buildPlayerStatusTag(UUID playerId) {
        if (playerId == null) {
            return "";
        }
        String base = isBot(playerId) ? "봇" : (readyPlayers.contains(playerId) ? "준비" : "대기");
        if (coachPlayers.contains(playerId)) {
            return base + "/코치";
        }
        return base;
    }

    private DialogAction dialogAction(UUID targetId, Consumer<Player> action) {
        return table.dialogAction(targetId, action);
    }

    private String resolveName(UUID playerId) {
        return table.resolveName(playerId);
    }

    private String seatLabel(SeatWind seat) {
        return table.seatLabel(seat);
    }

    private String describeRules(GameRules rules) {
        return table.describeRules(rules);
    }

    private GameRules getRulesSnapshot() {
        return table.getRulesSnapshot();
    }

    private int getReadyCount() {
        return table.getReadyCount();
    }

    private boolean isBot(UUID playerId) {
        return table.isBot(playerId);
    }

    private boolean isHost(UUID playerId) {
        return table.isHost(playerId);
    }

    private boolean isCoachAllowed() {
        return table.isCoachAllowed();
    }

    private boolean areAllReady() {
        return table.areAllReady();
    }

    private void requestStart(Player player) {
        table.requestStart(player);
    }

    private boolean assignSeat(Player player, SeatWind seat) {
        return table.assignSeat(player, seat);
    }

    private void clearSeat(UUID playerId, boolean updateUi) {
        table.clearSeat(playerId, updateUi);
    }

    private boolean toggleReady(Player player) {
        return table.toggleReady(player);
    }

    private boolean setCoach(Player player, boolean enabled) {
        return table.setCoach(player, enabled);
    }

    private void sendRoomRulesSummary(Player player) {
        table.sendRoomRulesSummary(player);
    }

    private boolean updateRule(String ruleKey, Boolean value) {
        return table.updateRule(ruleKey, value);
    }

    private boolean applyPreset(String presetKey) {
        return table.applyPreset(presetKey);
    }

    private void addBot(BotDifficulty difficulty) {
        table.addBot(difficulty);
    }

    private void removeBot(BotDifficulty difficulty) {
        table.removeBot(difficulty);
    }

    private boolean hasBotDifficulty(BotDifficulty difficulty) {
        return table.hasBotDifficulty(difficulty);
    }

    private String formatRuleLine(String label, boolean enabled) {
        return table.formatRuleLine(label, enabled);
    }

    private String onOff(boolean enabled) {
        return table.onOff(enabled);
    }

    private void broadcast(String message) {
        table.broadcast(message);
    }
}
