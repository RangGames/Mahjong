package wiki.creeper.mahjong.table;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import wiki.creeper.mahjong.game.CallRequest;
import wiki.creeper.mahjong.game.CallType;
import wiki.creeper.mahjong.game.GameEngine;
import wiki.creeper.mahjong.game.GameState;
import wiki.creeper.mahjong.game.KanOption;
import wiki.creeper.mahjong.game.MeldType;
import wiki.creeper.mahjong.game.RoundState;
import wiki.creeper.mahjong.game.SeatWind;
import wiki.creeper.mahjong.game.Tile;
import wiki.creeper.mahjong.game.TileId;
import wiki.creeper.mahjong.ui.WorldUiManager;

final class GameTableDialogs {
    private static final int MAX_PLAYERS = GameTable.MAX_PLAYERS;

    private final GameTable table;
    private final JavaPlugin plugin;
    private final List<UUID> players;
    private final Set<UUID> callDialogPlayers = new HashSet<>();

    GameTableDialogs(GameTable table) {
        this.table = table;
        this.plugin = table.getPlugin();
        this.players = table.getPlayers();
    }

    void showHandResultDialogs(GameTable.HandResult result) {
        if (result == null) {
            return;
        }
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                showHandResultDialog(player, result);
            }
        }
    }

    private void showHandResultDialog(Player player, GameTable.HandResult result) {
        if (player == null || result == null) {
            return;
        }
        List<DialogBody> body = new ArrayList<>();
        for (Component line : buildHandResultLines(result)) {
            body.add(DialogBody.plainMessage(line));
        }
        List<ActionButton> actions = new ArrayList<>();
        UUID targetId = player.getUniqueId();
        GameEngine engine = table.getEngine();
        boolean canNext = !result.gameOver && engine != null && engine.getState() == GameState.HAND_END
                && players.size() == MAX_PLAYERS;
        if (canNext) {
            actions.add(ActionButton.create(
                    Component.text("다음 핸드", NamedTextColor.GREEN),
                    Component.text("다음 핸드를 시작해요", NamedTextColor.DARK_GRAY),
                    100,
                    dialogAction(targetId, table::requestNextHand)
            ));
        }
        if (result.gameOver && table.isRoomMode()) {
            actions.add(ActionButton.create(
                    Component.text("로비", NamedTextColor.AQUA),
                    Component.text("로비로 돌아가기", NamedTextColor.DARK_GRAY),
                    80,
                    dialogAction(targetId, table::showRoomLobby)
            ));
        }
        actions.add(ActionButton.create(
                Component.text("닫기", NamedTextColor.DARK_GRAY),
                Component.text("닫기", NamedTextColor.GRAY),
                10,
                null
        ));
        String title = result.gameOver ? "게임 결과" : "핸드 결과";
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title, NamedTextColor.GOLD))
                        .body(body)
                        .build())
                .type(DialogType.multiAction(actions).build())
        );
        player.showDialog(dialog);
    }

    void showActionDialog(Player player, boolean canRiichi, boolean canTsumo, boolean canKan) {
        if (player == null || (!canRiichi && !canTsumo && !canKan)) {
            return;
        }
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("가능한 행동을 선택해 주세요.", NamedTextColor.GRAY)));
        List<ActionButton> actions = new ArrayList<>();
        UUID targetId = player.getUniqueId();
        if (canRiichi) {
            actions.add(ActionButton.create(
                    Component.text("리치", NamedTextColor.GOLD),
                    Component.text("리치를 선언합니다", NamedTextColor.GRAY),
                    140,
                    dialogAction(targetId, table::requestRiichi)
            ));
        }
        if (canTsumo) {
            actions.add(ActionButton.create(
                    Component.text("쯔모", NamedTextColor.GOLD),
                    Component.text("쯔모로 승리합니다", NamedTextColor.GRAY),
                    140,
                    dialogAction(targetId, table::requestTsumo)
            ));
        }
        if (canKan) {
            actions.add(ActionButton.create(
                    Component.text("깡", NamedTextColor.GOLD),
                    Component.text("깡을 선언합니다", NamedTextColor.GRAY),
                    140,
                    dialogAction(targetId, table::requestKan)
            ));
        }
        actions.add(ActionButton.create(
                Component.text("닫기", NamedTextColor.DARK_GRAY),
                Component.text("닫기", NamedTextColor.GRAY),
                100,
                null
        ));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("행동 선택", NamedTextColor.AQUA))
                        .body(body)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(actions).build())
        );
        player.showDialog(dialog);
    }

    void showDiscardConfirmDialog(Player player, Tile tile, ItemStack item) {
        if (player == null || tile == null || item == null) {
            return;
        }
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(
                Component.text("버릴 패: " + tile.getId().toShortString(), NamedTextColor.GRAY)));
        List<ActionButton> actions = new ArrayList<>();
        UUID targetId = player.getUniqueId();
        actions.add(ActionButton.create(
                Component.text("버리기", NamedTextColor.RED),
                Component.text("선택한 패를 버립니다", NamedTextColor.GRAY),
                120,
                dialogAction(targetId, clicker -> table.handleDiscardInternal(clicker, item))
        ));
        actions.add(ActionButton.create(
                Component.text("취소", NamedTextColor.DARK_GRAY),
                Component.text("돌아가기", NamedTextColor.GRAY),
                90,
                null
        ));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("버림 확인", NamedTextColor.GOLD))
                        .body(body)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(actions).build())
        );
        player.showDialog(dialog);
    }

    private List<Component> buildHandResultLines(GameTable.HandResult result) {
        List<Component> lines = new ArrayList<>();
        if (result.winnerId != null) {
            String winnerName = resolveName(result.winnerId);
            String winType = result.tsumo ? "쯔모" : "론";
            lines.add(Component.text("승자: " + winnerName + " (" + winType + ")", NamedTextColor.GOLD));
            if (!result.tsumo && result.discarderId != null) {
                lines.add(Component.text("방총: " + resolveName(result.discarderId), NamedTextColor.GRAY));
            }
            if (result.score != null) {
                lines.add(Component.text("점수: " + result.score.summary(), NamedTextColor.WHITE));
                String paymentLine = buildPaymentLine(result);
                if (!paymentLine.isEmpty()) {
                    lines.add(Component.text(paymentLine, NamedTextColor.GRAY));
                }
            }
            if (result.riichiPotApplied > 0) {
                int riichiStick = plugin.getConfig().getInt("scoring.riichiStick", 1000);
                int bonus = result.riichiPotApplied * riichiStick;
                lines.add(Component.text("공탁: " + result.riichiPotApplied + " (" + bonus + "점)", NamedTextColor.GRAY));
            }
        } else {
            lines.add(Component.text("유국", NamedTextColor.GOLD));
            if (result.tenpaiPlayers == null || result.tenpaiPlayers.isEmpty()) {
                lines.add(Component.text("텐파이 없음", NamedTextColor.GRAY));
            } else {
                List<String> names = new ArrayList<>();
                for (UUID playerId : result.tenpaiPlayers) {
                    names.add(resolveName(playerId));
                }
                lines.add(Component.text("텐파이 " + String.join(", ", names), NamedTextColor.GRAY));
            }
        }
        lines.add(Component.text("점수:", NamedTextColor.YELLOW));
        for (UUID playerId : players) {
            int delta = result.pointDeltas.getOrDefault(playerId, 0);
            int points = result.pointsAfter.getOrDefault(playerId, 0);
            NamedTextColor color;
            if (delta > 0) {
                color = NamedTextColor.GREEN;
            } else if (delta < 0) {
                color = NamedTextColor.RED;
            } else {
                color = NamedTextColor.GRAY;
            }
            lines.add(Component.text("- " + resolveName(playerId) + ": " + formatDelta(delta) + " => " + points, color));
        }
        if (result.gameOver) {
            lines.add(Component.text("게임이 종료되었어요.", NamedTextColor.RED));
        } else if (result.nextRound != null) {
            String nextLine = formatNextRoundLine(result.nextRound);
            if (!nextLine.isEmpty()) {
                lines.add(Component.text(nextLine, NamedTextColor.GRAY));
            }
        }
        return lines;
    }

    void updateWorldHandResult(GameTable.HandResult result) {
        WorldUiManager worldUi = table.getWorldUi();
        if (!worldUi.isSpawned()) {
            return;
        }
        if (result == null) {
            worldUi.clearHandResult();
            return;
        }
        worldUi.updateHandResult(buildHandResultTextLines(result));
    }

    private List<String> buildHandResultTextLines(GameTable.HandResult result) {
        List<String> lines = new ArrayList<>();
        if (result.winnerId != null) {
            String winnerName = resolveName(result.winnerId);
            String winType = result.tsumo ? "쯔모" : "론";
            lines.add("승자: " + winnerName + " (" + winType + ")");
            if (!result.tsumo && result.discarderId != null) {
                lines.add("방총: " + resolveName(result.discarderId));
            }
            if (result.score != null) {
                lines.add("점수: " + result.score.summary());
                String paymentLine = buildPaymentLine(result);
                if (!paymentLine.isEmpty()) {
                    lines.add(paymentLine);
                }
            }
            if (result.riichiPotApplied > 0) {
                int riichiStick = plugin.getConfig().getInt("scoring.riichiStick", 1000);
                int bonus = result.riichiPotApplied * riichiStick;
                lines.add("공탁: " + result.riichiPotApplied + " (" + bonus + "점)");
            }
        } else {
            lines.add("유국");
            if (result.tenpaiPlayers == null || result.tenpaiPlayers.isEmpty()) {
                lines.add("텐파이 없음");
            } else {
                List<String> names = new ArrayList<>();
                for (UUID playerId : result.tenpaiPlayers) {
                    names.add(resolveName(playerId));
                }
                lines.add("텐파이 " + String.join(", ", names));
            }
        }
        lines.add("점수:");
        for (UUID playerId : players) {
            int delta = result.pointDeltas.getOrDefault(playerId, 0);
            int points = result.pointsAfter.getOrDefault(playerId, 0);
            lines.add("- " + resolveName(playerId) + ": " + formatDelta(delta) + " => " + points);
        }
        if (result.gameOver) {
            lines.add("게임이 종료되었어요.");
        } else if (result.nextRound != null) {
            String nextLine = formatNextRoundLine(result.nextRound);
            if (!nextLine.isEmpty()) {
                lines.add(nextLine);
            }
        }
        return lines;
    }

    private String buildPaymentLine(GameTable.HandResult result) {
        if (result == null || result.score == null) {
            return "";
        }
        int honba = result.honbaApplied;
        int honbaRonBonus = plugin.getConfig().getInt("scoring.honbaRonBonus", 300);
        int honbaTsumoBonus = plugin.getConfig().getInt("scoring.honbaTsumoBonus", 100);
        String suffix = honba > 0 ? " (본장 " + honba + ")" : "";
        if (result.tsumo) {
            int dealerPay = result.score.getTsumoFromDealer() + (honba * honbaTsumoBonus);
            int otherPay = result.score.getTsumoFromOthers() + (honba * honbaTsumoBonus);
            if (result.score.isDealer()) {
                return "쯔모 지불 전원 " + dealerPay + suffix;
            }
            return "쯔모 지불 딜러 " + dealerPay + ", 기타 " + otherPay + suffix;
        }
        int ronPay = result.score.getRonPayment() + (honba * honbaRonBonus);
        return "론 지불 " + ronPay + suffix;
    }

    private String formatNextRoundLine(RoundState round) {
        if (round == null) {
            return "";
        }
        return "다음: " + seatLabel(round.getRoundWind()) + " " + round.getKyoku()
                + " (딜러 " + seatLabel(round.getDealerWind()) + ", 본장 " + round.getHonba() + ")";
    }

    private String formatDelta(int delta) {
        if (delta > 0) {
            return "+" + delta;
        }
        return Integer.toString(delta);
    }

    void showCallDialog(Player player, List<GameTable.CallOption> options, int callWindowSeconds) {
        if (player == null || options == null || options.isEmpty()) {
            return;
        }
        GameEngine engine = table.getEngine();
        Tile lastDiscard = engine == null ? null : engine.getLastDiscard();
        List<DialogBody> body = new ArrayList<>();
        String lastText = lastDiscard == null ? "-" : lastDiscard.getId().toShortString();
        body.add(DialogBody.plainMessage(Component.text("마지막 버린패: " + lastText, NamedTextColor.GRAY)));
        if (callWindowSeconds >= 0) {
            body.add(DialogBody.plainMessage(Component.text("남은 시간: " + callWindowSeconds + "초", NamedTextColor.DARK_GRAY)));
        }
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("울기 선택", NamedTextColor.GOLD))
                        .body(body)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(buildCallButtons(player, options, lastDiscard, true)).build())
        );
        player.showDialog(dialog);
    }

    void showChiDialog(Player player, List<GameTable.CallOption> options) {
        if (player == null || options == null || options.isEmpty()) {
            return;
        }
        GameEngine engine = table.getEngine();
        Tile lastDiscard = engine == null ? null : engine.getLastDiscard();
        String lastText = lastDiscard == null ? "-" : lastDiscard.getId().toShortString();
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("치 선택: " + lastText + ".", NamedTextColor.GRAY)));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("치 옵션", NamedTextColor.GREEN))
                        .body(body)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(buildCallButtons(player, options, lastDiscard, true)).build())
        );
        player.showDialog(dialog);
    }

    void showSelfKanDialog(Player player, List<KanOption> options) {
        if (player == null || options == null || options.isEmpty()) {
            return;
        }
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("깡 옵션을 선택해 주세요.", NamedTextColor.GRAY)));
        UUID targetId = player.getUniqueId();
        List<ActionButton> actions = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            KanOption option = options.get(i);
            int index = i + 1;
            String label = formatKanOptionLabel(option, index);
            actions.add(ActionButton.create(
                    Component.text(label, NamedTextColor.GOLD),
                    Component.text("깡 선언", NamedTextColor.GRAY),
                    180,
                    dialogAction(targetId, clicker -> table.requestKan(clicker, index))
            ));
        }
        actions.add(ActionButton.create(
                Component.text("취소", NamedTextColor.DARK_GRAY),
                Component.text("깡 선언", NamedTextColor.GRAY),
                120,
                null
        ));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("깡 옵션", NamedTextColor.GOLD))
                        .body(body)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(actions).build())
        );
        player.showDialog(dialog);
    }

    private List<ActionButton> buildCallButtons(Player player, List<GameTable.CallOption> options,
                                                Tile lastDiscard, boolean includePass) {
        List<ActionButton> actions = new ArrayList<>();
        UUID targetId = player.getUniqueId();
        for (GameTable.CallOption option : options) {
            String label = describeCallOption(option, lastDiscard);
            DialogAction action = dialogAction(targetId, clicker -> handleCallOption(option, clicker));
            actions.add(ActionButton.create(
                    Component.text(label, callColor(option.getType())),
                    Component.text("호출 " + callName(option.getType()), NamedTextColor.GRAY),
                    150,
                    action
            ));
        }
        if (includePass) {
            actions.add(ActionButton.create(
                    Component.text("패스", NamedTextColor.DARK_GRAY),
                    Component.text("넘기기", NamedTextColor.GRAY),
                    110,
                    null
            ));
        }
        return actions;
    }

    private void handleCallOption(GameTable.CallOption option, Player player) {
        switch (option.getType()) {
            case RON:
                table.requestRon(player);
                break;
            case PON:
                table.requestPon(player);
                break;
            case KAN:
                table.requestKan(player);
                break;
            case CHI:
                table.requestChi(player, option.getChiIndex());
                break;
            default:
                break;
        }
    }

    DialogAction dialogAction(UUID targetId, Consumer<Player> action) {
        return DialogAction.customClick((view, audience) -> {
            if (!(audience instanceof Player player)) {
                return;
            }
            if (!player.getUniqueId().equals(targetId)) {
                return;
            }
            action.accept(player);
        }, ClickCallback.Options.builder().uses(1).build());
    }

    private String describeCallOption(GameTable.CallOption option, Tile lastDiscard) {
        String tiles = formatTilesForCall(option, lastDiscard);
        if (tiles.isEmpty()) {
            return callName(option.getType());
        }
        return callName(option.getType()) + " " + tiles;
    }

    private String formatKanOptionLabel(KanOption option, int index) {
        String tile = option.getTileId().toShortString();
        String type = option.getType() == MeldType.KAN_CLOSED ? "암깡" : "가깡";
        return index + ". " + type + " " + tile;
    }

    private String describeSelfKan(KanOption option) {
        String tile = option.getTileId().toShortString();
        String type = option.getType() == MeldType.KAN_CLOSED ? "암깡" : "가깡";
        return type + " (" + tile + ")";
    }

    private String formatTilesForCall(GameTable.CallOption option, Tile lastDiscard) {
        List<TileId> ids = new ArrayList<>();
        for (Tile tile : option.getTiles()) {
            ids.add(tile.getId());
        }
        if (lastDiscard != null && option.getType() != CallType.RON) {
            ids.add(lastDiscard.getId());
        } else if (lastDiscard != null && option.getType() == CallType.RON && ids.isEmpty()) {
            ids.add(lastDiscard.getId());
        }
        if (ids.isEmpty()) {
            return "";
        }
        ids.sort(Comparator.comparingInt(this::tileSortKey));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append("-");
            }
            sb.append(ids.get(i).toShortString());
        }
        return sb.toString();
    }

    String buildTilePayload(Tile tile) {
        if (tile == null) {
            return "";
        }
        return "tile=" + tile.getId().toShortString();
    }

    String buildDrawPayload(Tile tile, boolean rinshan) {
        if (tile == null) {
            return "";
        }
        return "tile=" + tile.getId().toShortString() + ";rinshan=" + rinshan;
    }

    String buildCallPayload(CallRequest request, Tile lastDiscard, UUID lastDiscarder) {
        if (request == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        List<TileId> ids = new ArrayList<>();
        for (Tile tile : request.getTiles()) {
            ids.add(tile.getId());
        }
        if (lastDiscard != null) {
            ids.add(lastDiscard.getId());
        }
        String tiles = formatTileIds(ids);
        if (!tiles.isEmpty()) {
            sb.append("tiles=").append(tiles);
        }
        if (lastDiscarder != null) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append("from=").append(lastDiscarder);
        }
        return sb.toString();
    }

    String buildKanPayload(KanOption option) {
        if (option == null) {
            return "";
        }
        return "tile=" + option.getTileId().toShortString();
    }

    String buildWinPayload(boolean tsumo, UUID discarder) {
        if (tsumo) {
            return "type=TSUMO";
        }
        if (discarder != null) {
            return "type=RON;discarder=" + discarder;
        }
        return "type=RON";
    }

    String buildRyuukyokuPayload(List<UUID> tenpaiPlayers) {
        if (tenpaiPlayers == null || tenpaiPlayers.isEmpty()) {
            return "tenpai=";
        }
        return "tenpai=" + formatPlayerIdList(tenpaiPlayers);
    }

    private String formatPlayerIdList(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (UUID playerId : ids) {
            values.add(playerId.toString());
        }
        return String.join(",", values);
    }

    private String formatTileIds(List<TileId> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        List<TileId> sorted = new ArrayList<>(ids);
        sorted.sort(Comparator.comparingInt(this::tileSortKey));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                sb.append("-");
            }
            sb.append(sorted.get(i).toShortString());
        }
        return sb.toString();
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

    private NamedTextColor callColor(CallType type) {
        switch (type) {
            case RON:
                return NamedTextColor.RED;
            case KAN:
                return NamedTextColor.GOLD;
            case PON:
                return NamedTextColor.YELLOW;
            case CHI:
                return NamedTextColor.GREEN;
            default:
                return NamedTextColor.WHITE;
        }
    }

    String callName(CallType type) {
        if (type == null) {
            return "호출";
        }
        switch (type) {
            case RON:
                return "론";
            case KAN:
                return "깡";
            case PON:
                return "퐁";
            case CHI:
                return "치";
            default:
                return type.name();
        }
    }

    void showCallPopup(Player player, List<String> options, int remainingSeconds) {
        if (player == null || options == null || options.isEmpty()) {
            return;
        }
        String text = "울기 가능: " + String.join("/", options);
        if (remainingSeconds >= 0) {
            text += " (남은 " + remainingSeconds + "초)";
        }
        player.sendActionBar(text);
    }

    void updateCallPopups(int remainingSeconds) {
        GameEngine engine = table.getEngine();
        if (engine == null || engine.getState() != GameState.CALL_WINDOW) {
            return;
        }
        for (UUID playerId : players) {
            if (playerId.equals(engine.getLastDiscarder())) {
                continue;
            }
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            List<String> options = table.resolveCallOptions(playerId, player);
            if (!options.isEmpty()) {
                showCallPopup(player, options, remainingSeconds);
            }
        }
    }

    void clearCallPopups() {
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.sendActionBar("");
            }
        }
    }

    void clearCallDialogs() {
        if (!isEnabled()) {
            callDialogPlayers.clear();
            return;
        }
        for (UUID playerId : callDialogPlayers) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.closeDialog();
            }
        }
        callDialogPlayers.clear();
    }

    void closeDialogsForAll() {
        if (!isEnabled()) {
            return;
        }
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.closeDialog();
            }
        }
    }

    void closeDialog(Player player) {
        if (player == null || !isEnabled()) {
            return;
        }
        player.closeDialog();
    }

    boolean isEnabled() {
        return plugin.getConfig().getBoolean("ui.enableDialogs", false);
    }

    boolean shouldConfirmDiscard() {
        return isEnabled() && plugin.getConfig().getBoolean("ui.confirmDiscard", false);
    }

    private String resolveName(UUID playerId) {
        return table.resolveName(playerId);
    }

    private String seatLabel(SeatWind seat) {
        return table.seatLabel(seat);
    }
}
