package wiki.creeper.mahjong.command;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import wiki.creeper.mahjong.ai.BotDifficulty;
import wiki.creeper.mahjong.game.GameState;
import wiki.creeper.mahjong.table.GameTable;
import wiki.creeper.mahjong.table.TableManager;

public class MahjongCommand implements CommandExecutor, TabCompleter {

    private final TableManager tableManager;

    public MahjongCommand(TableManager tableManager) {
        this.tableManager = tableManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있어요.");
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create":
                if (tableManager.getTableByPlayer(player).isPresent()) {
                    player.sendMessage("이미 다른 테이블에 참여 중이에요. 먼저 나가주세요.");
                    return true;
                }
                GameTable table = tableManager.createTable(player);
                player.sendMessage("테이블을 만들었어요: " + table.getId());
                return true;
            case "room":
                return handleRoomCommand(player, args);
            case "join":
                if (tableManager.getTableByPlayer(player).isPresent()) {
                    player.sendMessage("이미 다른 테이블에 참여 중이에요. 먼저 나가주세요.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("사용법: /mj join <tableId>");
                    return true;
                }
                Optional<UUID> tableId = resolveTableId(args[1]);
                if (tableId.isEmpty()) {
                    player.sendMessage("테이블을 찾을 수 없어요: " + args[1]);
                    return true;
                }
                GameTable target = tableManager.getTable(tableId.get()).orElse(null);
                if (target == null) {
                    player.sendMessage("테이블을 찾을 수 없어요: " + args[1]);
                    return true;
                }
                if (target.getState() != GameState.LOBBY) {
                    player.sendMessage("게임이 이미 시작되어 입장할 수 없어요.");
                    return true;
                }
                if (target.getPlayers().size() >= 4) {
                    player.sendMessage("테이블이 가득 찼어요 (4인).");
                    return true;
                }
                if (tableManager.joinTable(player, tableId.get())) {
                    player.sendMessage("테이블에 참가했어요: " + tableId.get());
                } else {
                    player.sendMessage("테이블 입장에 실패했어요. 다시 시도해 주세요.");
                }
                return true;
            case "leave":
                if (tableManager.leaveTable(player)) {
                    player.sendMessage("테이블에서 나왔어요.");
                } else {
                    player.sendMessage("현재 테이블에 참여 중이 아니에요.");
                }
                return true;
            case "start":
                Optional<GameTable> current = tableManager.getTableByPlayer(player);
                if (current.isEmpty()) {
                    player.sendMessage("현재 테이블에 참여 중이 아니에요.");
                    return true;
                }
                GameTable tableToStart = current.get();
                tableToStart.requestStart(player);
                return true;
            case "hand":
                Optional<GameTable> tableForHand = tableManager.getTableByPlayer(player);
                if (tableForHand.isEmpty()) {
                    player.sendMessage("현재 테이블에 참여 중이 아니에요.");
                    return true;
                }
                tableForHand.get().openHand(player);
                return true;
            case "ron":
                Optional<GameTable> tableForRon = tableManager.getTableByPlayer(player);
                if (tableForRon.isEmpty()) {
                    player.sendMessage("현재 테이블에 참여 중이 아니에요.");
                    return true;
                }
                tableForRon.get().requestRon(player);
                return true;
            case "pon":
                Optional<GameTable> tableForPon = tableManager.getTableByPlayer(player);
                if (tableForPon.isEmpty()) {
                    player.sendMessage("현재 테이블에 참여 중이 아니에요.");
                    return true;
                }
                tableForPon.get().requestPon(player);
                return true;
            case "chi":
                Optional<GameTable> tableForChi = tableManager.getTableByPlayer(player);
                if (tableForChi.isEmpty()) {
                    player.sendMessage("현재 테이블에 참여 중이 아니에요.");
                    return true;
                }
                int optionIndex = 1;
                if (args.length >= 2) {
                    try {
                        optionIndex = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {
                        player.sendMessage("사용법: /mj chi [번호]");
                        return true;
                    }
                }
                tableForChi.get().requestChi(player, optionIndex);
                return true;
            case "kan":
                Optional<GameTable> tableForKan = tableManager.getTableByPlayer(player);
                if (tableForKan.isEmpty()) {
                    player.sendMessage("현재 테이블에 참여 중이 아니에요.");
                    return true;
                }
                int kanIndex = 0;
                if (args.length >= 2) {
                    try {
                        kanIndex = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {
                        player.sendMessage("사용법: /mj kan [번호]");
                        return true;
                    }
                }
                if (kanIndex > 0) {
                    tableForKan.get().requestKan(player, kanIndex);
                } else {
                    tableForKan.get().requestKan(player);
                }
                return true;
            case "riichi":
                Optional<GameTable> tableForRiichi = tableManager.getTableByPlayer(player);
                if (tableForRiichi.isEmpty()) {
                    player.sendMessage("현재 테이블에 참여 중이 아니에요.");
                    return true;
                }
                tableForRiichi.get().requestRiichi(player);
                return true;
            case "tsumo":
                Optional<GameTable> tableForTsumo = tableManager.getTableByPlayer(player);
                if (tableForTsumo.isEmpty()) {
                    player.sendMessage("현재 테이블에 참여 중이 아니에요.");
                    return true;
                }
                tableForTsumo.get().requestTsumo(player);
                return true;
            case "nexthand":
                Optional<GameTable> tableForNext = tableManager.getTableByPlayer(player);
                if (tableForNext.isEmpty()) {
                    player.sendMessage("현재 테이블에 참여 중이 아니에요.");
                    return true;
                }
                tableForNext.get().requestNextHand(player);
                return true;
            case "log":
                Optional<GameTable> tableForLog = tableManager.getTableByPlayer(player);
                if (tableForLog.isEmpty()) {
                    player.sendMessage("현재 테이블에 참여 중이 아니에요.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("사용법: /mj log export | replay [ticks]");
                    return true;
                }
                String logAction = args[1].toLowerCase(Locale.ROOT);
                switch (logAction) {
                    case "export":
                        Optional<Path> file = tableForLog.get().exportEventLog();
                        if (file.isPresent()) {
                            player.sendMessage("리플레이를 저장했어요: " + file.get());
                        } else {
                            player.sendMessage("리플레이 저장에 실패했어요.");
                        }
                        return true;
                    case "replay":
                        int ticks = 10;
                        if (args.length >= 3) {
                            try {
                                ticks = Integer.parseInt(args[2]);
                            } catch (NumberFormatException ignored) {
                                player.sendMessage("사용법: /mj log replay [ticks]");
                                return true;
                            }
                        }
                        tableForLog.get().replayEvents(player, ticks);
                        return true;
                    default:
                        player.sendMessage("사용법: /mj log export | replay [ticks]");
                        return true;
                }
            case "bot":
                return handleBotCommand(player, args);
            case "coach":
                return handleCoachCommand(player, args);
            case "list":
                if (tableManager.getTables().isEmpty()) {
                    player.sendMessage("현재 열려 있는 테이블이 없어요.");
                    return true;
                }
                player.sendMessage("테이블 목록:");
                for (GameTable t : tableManager.getTables()) {
                    player.sendMessage("- " + t.getId() + " 인원=" + t.getPlayers().size() + " 상태=" + t.getState());
                }
                return true;
            case "info":
                Optional<GameTable> tableForInfo = tableManager.getTableByPlayer(player);
                if (tableForInfo.isEmpty()) {
                    player.sendMessage("현재 테이블에 참여 중이 아니에요.");
                    return true;
                }
                player.sendMessage(tableForInfo.get().getStatusLine());
                return true;
            case "disband":
                if (!player.hasPermission("mahjong.admin")) {
                    player.sendMessage("테이블 해산 권한이 없어요.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("사용법: /mj disband <tableId>");
                    return true;
                }
                Optional<UUID> disbandId = resolveTableId(args[1]);
                if (disbandId.isEmpty()) {
                    player.sendMessage("테이블을 찾을 수 없어요: " + args[1]);
                    return true;
                }
                if (tableManager.disbandTable(disbandId.get())) {
                    player.sendMessage("테이블을 해산했어요: " + disbandId.get());
                } else {
                    player.sendMessage("테이블 해산에 실패했어요.");
                }
                return true;
            default:
                sendUsage(player);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("create");
            subs.add("room");
            subs.add("join");
            subs.add("leave");
            subs.add("start");
            subs.add("hand");
            subs.add("ron");
            subs.add("pon");
            subs.add("chi");
            subs.add("kan");
            subs.add("riichi");
            subs.add("tsumo");
            subs.add("nexthand");
            subs.add("log");
            subs.add("bot");
            subs.add("coach");
            subs.add("list");
            subs.add("info");
            subs.add("disband");
            return subs;
        }
        if (args.length == 2 && "log".equalsIgnoreCase(args[0])) {
            List<String> subs = new ArrayList<>();
            subs.add("export");
            subs.add("replay");
            return subs;
        }
        if (args.length == 2 && "room".equalsIgnoreCase(args[0])) {
            List<String> subs = new ArrayList<>();
            subs.add("create");
            subs.add("join");
            subs.add("rules");
            subs.add("ready");
            subs.add("status");
            return subs;
        }
        if (args.length == 2 && "bot".equalsIgnoreCase(args[0])) {
            return List.of("add", "remove");
        }
        if (args.length == 2 && "coach".equalsIgnoreCase(args[0])) {
            return List.of("on", "off");
        }
        if (args.length == 2 && "join".equalsIgnoreCase(args[0])) {
            List<String> ids = new ArrayList<>();
            for (GameTable t : tableManager.getTables()) {
                ids.add(t.getId().toString());
            }
            return ids;
        }
        if (args.length == 3 && "room".equalsIgnoreCase(args[0]) && "join".equalsIgnoreCase(args[1])) {
            List<String> codes = new ArrayList<>();
            for (GameTable t : tableManager.getTables()) {
                if (t.isRoomMode() && t.getRoomCode() != null) {
                    codes.add(t.getRoomCode());
                }
            }
            return codes;
        }
        if (args.length == 3 && "room".equalsIgnoreCase(args[0]) && "rules".equalsIgnoreCase(args[1])) {
            return List.of("redDora", "openTanyao", "ippatsu", "uraDora", "bots", "coach", "coachRank", "preset");
        }
        if (args.length == 3 && "bot".equalsIgnoreCase(args[0])) {
            return List.of("BEGINNER", "NORMAL", "HARD");
        }
        if (args.length == 4 && "room".equalsIgnoreCase(args[0]) && "rules".equalsIgnoreCase(args[1])) {
            if ("preset".equalsIgnoreCase(args[2])) {
                return List.of("default", "kuitan", "classic");
            }
            return List.of("on", "off");
        }
        return Collections.emptyList();
    }

    private boolean handleRoomCommand(Player player, String[] args) {
        if (args.length == 1) {
            if (tableManager.showRoomMenu(player)) {
                return true;
            }
            sendRoomUsage(player);
            return true;
        }
        if (args.length < 2) {
            sendRoomUsage(player);
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create":
                if (tableManager.getTableByPlayer(player).isPresent()) {
                    player.sendMessage("이미 다른 테이블에 참여 중이에요. 먼저 나가주세요.");
                    return true;
                }
                GameTable room = tableManager.createRoom(player);
                player.sendMessage("방을 만들었어요. 코드: " + room.getRoomCode());
                player.sendMessage("당신이 방장입니다. /mj room rules 또는 로비 화면에서 룰을 설정해 주세요.");
                room.showRoomLobby(player);
                return true;
            case "join":
                if (tableManager.getTableByPlayer(player).isPresent()) {
                    player.sendMessage("이미 다른 테이블에 참여 중이에요. 먼저 나가주세요.");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage("사용법: /mj room join <code>");
                    return true;
                }
                Optional<GameTable> targetRoom = tableManager.getRoomByCode(args[2]);
                if (targetRoom.isEmpty()) {
                    player.sendMessage("방을 찾을 수 없어요: " + args[2]);
                    return true;
                }
                GameTable roomToJoin = targetRoom.get();
                if (roomToJoin.getState() != GameState.LOBBY) {
                    player.sendMessage("게임이 이미 시작되어 입장할 수 없어요.");
                    return true;
                }
                if (roomToJoin.getPlayers().size() >= 4) {
                    player.sendMessage("방이 가득 찼어요 (4인).");
                    return true;
                }
                if (tableManager.joinRoom(player, args[2])) {
                    player.sendMessage("방에 참가했어요: " + roomToJoin.getRoomCode());
                    roomToJoin.showRoomLobby(player);
                } else {
                    player.sendMessage("방 입장에 실패했어요. 다시 시도해 주세요.");
                }
                return true;
            case "rules":
                Optional<GameTable> tableForRules = tableManager.getTableByPlayer(player);
                if (tableForRules.isEmpty()) {
                    player.sendMessage("현재 방에 참여 중이 아니에요.");
                    return true;
                }
                GameTable ruleRoom = tableForRules.get();
                if (!ruleRoom.isRoomMode()) {
                    player.sendMessage("이 테이블은 방 모드가 아니에요.");
                    return true;
                }
                if (!ruleRoom.isHost(player.getUniqueId())) {
                    player.sendMessage("방장만 룰을 변경할 수 있어요.");
                    return true;
                }
                if (ruleRoom.getState() != GameState.LOBBY) {
                    player.sendMessage("게임 시작 후에는 룰을 바꿀 수 없어요.");
                    return true;
                }
                if (args.length == 2) {
                    ruleRoom.showRoomRules(player);
                    return true;
                }
                String ruleKey = args[2].toLowerCase(Locale.ROOT);
                if ("preset".equals(ruleKey)) {
                    if (args.length < 4) {
                        sendRoomRulesUsage(player);
                        return true;
                    }
                    if (!ruleRoom.applyPreset(args[3])) {
                        sendRoomRulesUsage(player);
                        return true;
                    }
                    player.sendMessage("프리셋 룰을 적용했어요.");
                    return true;
                }
                Boolean value = null;
                if (args.length >= 4) {
                    value = parseBoolean(args[3]);
                    if (value == null) {
                        sendRoomRulesUsage(player);
                        return true;
                    }
                }
                if (!ruleRoom.updateRule(ruleKey, value)) {
                    sendRoomRulesUsage(player);
                    return true;
                }
                player.sendMessage("룸 룰을 업데이트했어요.");
                return true;
            case "ready":
                Optional<GameTable> tableForReady = tableManager.getTableByPlayer(player);
                if (tableForReady.isEmpty()) {
                    player.sendMessage("현재 방에 참여 중이 아니에요.");
                    return true;
                }
                GameTable readyRoom = tableForReady.get();
                if (!readyRoom.isRoomMode()) {
                    player.sendMessage("이 테이블은 방 모드가 아니에요.");
                    return true;
                }
                if (readyRoom.getState() != GameState.LOBBY) {
                    player.sendMessage("레디 상태는 로비에서만 변경할 수 있어요.");
                    return true;
                }
                if (!readyRoom.toggleReady(player)) {
                    player.sendMessage("레디 상태 변경에 실패했어요.");
                } else {
                    readyRoom.showRoomLobby(player);
                }
                return true;
            case "status":
                Optional<GameTable> tableForStatus = tableManager.getTableByPlayer(player);
                if (tableForStatus.isEmpty()) {
                    player.sendMessage("현재 방에 참여 중이 아니에요.");
                    return true;
                }
                GameTable statusRoom = tableForStatus.get();
                if (!statusRoom.isRoomMode()) {
                    player.sendMessage("이 테이블은 방 모드가 아니에요.");
                    return true;
                }
                statusRoom.showRoomLobby(player);
                return true;
            default:
                sendRoomUsage(player);
                return true;
        }
    }

    private boolean handleBotCommand(Player player, String[] args) {
        if (args.length < 2) {
            sendBotUsage(player);
            return true;
        }
        Optional<GameTable> tableOpt = tableManager.getTableByPlayer(player);
        if (tableOpt.isEmpty()) {
            player.sendMessage("현재 방에 참여 중이 아니에요.");
            return true;
        }
        GameTable table = tableOpt.get();
        if (!table.isRoomMode()) {
            player.sendMessage("이 테이블은 방 모드가 아니에요.");
            return true;
        }
        if (!table.isHost(player.getUniqueId())) {
            player.sendMessage("방장만 봇을 관리할 수 있어요.");
            return true;
        }
        if (table.getState() != GameState.LOBBY) {
            player.sendMessage("봇 관리는 로비에서만 가능해요.");
            return true;
        }
        if (!table.areBotsEnabled()) {
            player.sendMessage("이 방에서는 봇이 비활성화되어 있어요.");
            return true;
        }
        if (args.length < 3) {
            sendBotUsage(player);
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        Optional<BotDifficulty> difficulty = BotDifficulty.parse(args[2]);
        if (difficulty.isEmpty()) {
            sendBotUsage(player);
            return true;
        }
        switch (action) {
            case "add":
                if (table.addBot(difficulty.get())) {
                    player.sendMessage("봇을 추가했어요: " + difficulty.get());
                } else {
                    player.sendMessage("봇 추가에 실패했어요.");
                }
                return true;
            case "remove":
                if (table.removeBot(difficulty.get())) {
                    player.sendMessage("봇을 제거했어요: " + difficulty.get());
                } else {
                    player.sendMessage("봇 제거에 실패했어요.");
                }
                return true;
            default:
                sendBotUsage(player);
                return true;
        }
    }

    private boolean handleCoachCommand(Player player, String[] args) {
        if (args.length < 2) {
            sendCoachUsage(player);
            return true;
        }
        Optional<GameTable> tableOpt = tableManager.getTableByPlayer(player);
        if (tableOpt.isEmpty()) {
            player.sendMessage("현재 방에 참여 중이 아니에요.");
            return true;
        }
        GameTable table = tableOpt.get();
        if (!table.isRoomMode()) {
            player.sendMessage("이 테이블은 방 모드가 아니에요.");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "on":
                table.setCoach(player, true);
                return true;
            case "off":
                table.setCoach(player, false);
                return true;
            default:
                sendCoachUsage(player);
                return true;
        }
    }

    private void sendRoomUsage(Player player) {
        player.sendMessage("사용법: /mj room (메뉴) | create | join <code> | rules | ready | status");
    }

    private void sendRoomRulesUsage(Player player) {
        player.sendMessage("사용법: /mj room rules <rule> <on|off>");
        player.sendMessage("프리셋: /mj room rules preset <default|kuitan|classic>");
        player.sendMessage("rule 목록: redDora, openTanyao, ippatsu, uraDora, bots, coach, coachRank");
    }

    private void sendBotUsage(Player player) {
        player.sendMessage("사용법: /mj bot add|remove <BEGINNER|NORMAL|HARD>");
    }

    private void sendCoachUsage(Player player) {
        player.sendMessage("사용법: /mj coach on|off");
    }

    private Boolean parseBoolean(String value) {
        if (value == null) {
            return null;
        }
        switch (value.toLowerCase(Locale.ROOT)) {
            case "on":
            case "true":
            case "yes":
                return Boolean.TRUE;
            case "off":
            case "false":
            case "no":
                return Boolean.FALSE;
            default:
                return null;
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage("기본 명령어: /mj create | join <tableId> | leave | start | nexthand | hand | ron | pon | chi [번호] | kan [번호] | riichi | tsumo");
        player.sendMessage("도구 명령어: /mj log export|replay [ticks] | bot add|remove <난이도> | coach on|off | info | list | disband <tableId>");
        player.sendMessage("방 명령어: /mj room (메뉴) | create | join <code> | rules | ready | status");
    }

    private Optional<UUID> resolveTableId(String input) {
        try {
            return Optional.of(UUID.fromString(input));
        } catch (IllegalArgumentException ignored) {
            String lower = input.toLowerCase(Locale.ROOT);
            for (GameTable t : tableManager.getTables()) {
                String id = t.getId().toString().toLowerCase(Locale.ROOT);
                if (id.startsWith(lower)) {
                    return Optional.of(t.getId());
                }
            }
            return Optional.empty();
        }
    }
}
