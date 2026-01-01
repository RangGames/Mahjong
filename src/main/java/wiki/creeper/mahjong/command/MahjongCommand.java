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
            sender.sendMessage("Only players can use this command.");
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
                    player.sendMessage("You are already in a table.");
                    return true;
                }
                GameTable table = tableManager.createTable(player);
                player.sendMessage("Table created: " + table.getId());
                return true;
            case "room":
                return handleRoomCommand(player, args);
            case "join":
                if (tableManager.getTableByPlayer(player).isPresent()) {
                    player.sendMessage("You are already in a table.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("Usage: /mj join <tableId>");
                    return true;
                }
                Optional<UUID> tableId = resolveTableId(args[1]);
                if (tableId.isEmpty()) {
                    player.sendMessage("Table not found: " + args[1]);
                    return true;
                }
                GameTable target = tableManager.getTable(tableId.get()).orElse(null);
                if (target == null) {
                    player.sendMessage("Table not found: " + args[1]);
                    return true;
                }
                if (target.getState() != GameState.LOBBY) {
                    player.sendMessage("Unable to join: game already started.");
                    return true;
                }
                if (target.getPlayers().size() >= 4) {
                    player.sendMessage("Unable to join: table is full.");
                    return true;
                }
                if (tableManager.joinTable(player, tableId.get())) {
                    player.sendMessage("Joined table: " + tableId.get());       
                } else {
                    player.sendMessage("Unable to join table.");
                }
                return true;
            case "leave":
                if (tableManager.leaveTable(player)) {
                    player.sendMessage("Left table.");
                } else {
                    player.sendMessage("You are not in a table.");
                }
                return true;
            case "start":
                Optional<GameTable> current = tableManager.getTableByPlayer(player);
                if (current.isEmpty()) {
                    player.sendMessage("You are not in a table.");
                    return true;
                }
                GameTable tableToStart = current.get();
                tableToStart.requestStart(player);
                return true;
            case "hand":
                Optional<GameTable> tableForHand = tableManager.getTableByPlayer(player);
                if (tableForHand.isEmpty()) {
                    player.sendMessage("You are not in a table.");
                    return true;
                }
                tableForHand.get().openHand(player);
                return true;
            case "ron":
                Optional<GameTable> tableForRon = tableManager.getTableByPlayer(player);
                if (tableForRon.isEmpty()) {
                    player.sendMessage("You are not in a table.");
                    return true;
                }
                tableForRon.get().requestRon(player);
                return true;
            case "pon":
                Optional<GameTable> tableForPon = tableManager.getTableByPlayer(player);
                if (tableForPon.isEmpty()) {
                    player.sendMessage("You are not in a table.");
                    return true;
                }
                tableForPon.get().requestPon(player);
                return true;
            case "chi":
                Optional<GameTable> tableForChi = tableManager.getTableByPlayer(player);
                if (tableForChi.isEmpty()) {
                    player.sendMessage("You are not in a table.");
                    return true;
                }
                int optionIndex = 1;
                if (args.length >= 2) {
                    try {
                        optionIndex = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {
                        player.sendMessage("Usage: /mj chi [index]");
                        return true;
                    }
                }
                tableForChi.get().requestChi(player, optionIndex);
                return true;
            case "kan":
                Optional<GameTable> tableForKan = tableManager.getTableByPlayer(player);
                if (tableForKan.isEmpty()) {
                    player.sendMessage("You are not in a table.");
                    return true;
                }
                int kanIndex = 0;
                if (args.length >= 2) {
                    try {
                        kanIndex = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {
                        player.sendMessage("Usage: /mj kan [index]");
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
                    player.sendMessage("You are not in a table.");
                    return true;
                }
                tableForRiichi.get().requestRiichi(player);
                return true;
            case "tsumo":
                Optional<GameTable> tableForTsumo = tableManager.getTableByPlayer(player);
                if (tableForTsumo.isEmpty()) {
                    player.sendMessage("You are not in a table.");
                    return true;
                }
                tableForTsumo.get().requestTsumo(player);
                return true;
            case "nexthand":
                Optional<GameTable> tableForNext = tableManager.getTableByPlayer(player);
                if (tableForNext.isEmpty()) {
                    player.sendMessage("You are not in a table.");
                    return true;
                }
                tableForNext.get().requestNextHand(player);
                return true;
            case "log":
                Optional<GameTable> tableForLog = tableManager.getTableByPlayer(player);
                if (tableForLog.isEmpty()) {
                    player.sendMessage("You are not in a table.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("Usage: /mj log export | replay [ticks]");
                    return true;
                }
                String logAction = args[1].toLowerCase(Locale.ROOT);
                switch (logAction) {
                    case "export":
                        Optional<Path> file = tableForLog.get().exportEventLog();
                        if (file.isPresent()) {
                            player.sendMessage("Replay exported: " + file.get());
                        } else {
                            player.sendMessage("Unable to export replay.");
                        }
                        return true;
                    case "replay":
                        int ticks = 10;
                        if (args.length >= 3) {
                            try {
                                ticks = Integer.parseInt(args[2]);
                            } catch (NumberFormatException ignored) {
                                player.sendMessage("Usage: /mj log replay [ticks]");
                                return true;
                            }
                        }
                        tableForLog.get().replayEvents(player, ticks);
                        return true;
                    default:
                        player.sendMessage("Usage: /mj log export | replay [ticks]");
                        return true;
                }
            case "list":
                if (tableManager.getTables().isEmpty()) {
                    player.sendMessage("No tables available.");
                    return true;
                }
                player.sendMessage("Tables:");
                for (GameTable t : tableManager.getTables()) {
                    player.sendMessage("- " + t.getId() + " players=" + t.getPlayers().size() + " state=" + t.getState());
                }
                return true;
            case "info":
                Optional<GameTable> tableForInfo = tableManager.getTableByPlayer(player);
                if (tableForInfo.isEmpty()) {
                    player.sendMessage("You are not in a table.");
                    return true;
                }
                player.sendMessage(tableForInfo.get().getStatusLine());
                return true;
            case "disband":
                if (!player.hasPermission("mahjong.admin")) {
                    player.sendMessage("You do not have permission to disband tables.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("Usage: /mj disband <tableId>");
                    return true;
                }
                Optional<UUID> disbandId = resolveTableId(args[1]);
                if (disbandId.isEmpty()) {
                    player.sendMessage("Table not found: " + args[1]);
                    return true;
                }
                if (tableManager.disbandTable(disbandId.get())) {
                    player.sendMessage("Table disbanded: " + disbandId.get());
                } else {
                    player.sendMessage("Unable to disband table.");
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
            return List.of("redDora", "openTanyao", "ippatsu", "uraDora", "preset");
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
        if (args.length < 2) {
            sendRoomUsage(player);
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create":
                if (tableManager.getTableByPlayer(player).isPresent()) {
                    player.sendMessage("You are already in a table.");
                    return true;
                }
                GameTable room = tableManager.createRoom(player);
                player.sendMessage("Room created. Code: " + room.getRoomCode());
                player.sendMessage("You are the host. Use /mj room rules to configure.");
                room.showRoomLobby(player);
                return true;
            case "join":
                if (tableManager.getTableByPlayer(player).isPresent()) {
                    player.sendMessage("You are already in a table.");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage("Usage: /mj room join <code>");
                    return true;
                }
                Optional<GameTable> targetRoom = tableManager.getRoomByCode(args[2]);
                if (targetRoom.isEmpty()) {
                    player.sendMessage("Room not found: " + args[2]);
                    return true;
                }
                GameTable roomToJoin = targetRoom.get();
                if (roomToJoin.getState() != GameState.LOBBY) {
                    player.sendMessage("Unable to join: game already started.");
                    return true;
                }
                if (roomToJoin.getPlayers().size() >= 4) {
                    player.sendMessage("Unable to join: room is full.");
                    return true;
                }
                if (tableManager.joinRoom(player, args[2])) {
                    player.sendMessage("Joined room: " + roomToJoin.getRoomCode());
                    roomToJoin.showRoomLobby(player);
                } else {
                    player.sendMessage("Unable to join room.");
                }
                return true;
            case "rules":
                Optional<GameTable> tableForRules = tableManager.getTableByPlayer(player);
                if (tableForRules.isEmpty()) {
                    player.sendMessage("You are not in a room.");
                    return true;
                }
                GameTable ruleRoom = tableForRules.get();
                if (!ruleRoom.isRoomMode()) {
                    player.sendMessage("This table is not a room.");
                    return true;
                }
                if (!ruleRoom.isHost(player.getUniqueId())) {
                    player.sendMessage("Only the host can change room rules.");
                    return true;
                }
                if (ruleRoom.getState() != GameState.LOBBY) {
                    player.sendMessage("Room rules are locked after the game starts.");
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
                    player.sendMessage("Room preset applied.");
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
                player.sendMessage("Room rules updated.");
                return true;
            case "ready":
                Optional<GameTable> tableForReady = tableManager.getTableByPlayer(player);
                if (tableForReady.isEmpty()) {
                    player.sendMessage("You are not in a room.");
                    return true;
                }
                GameTable readyRoom = tableForReady.get();
                if (!readyRoom.isRoomMode()) {
                    player.sendMessage("This table is not a room.");
                    return true;
                }
                if (readyRoom.getState() != GameState.LOBBY) {
                    player.sendMessage("Ready status is only available in the lobby.");
                    return true;
                }
                if (!readyRoom.toggleReady(player)) {
                    player.sendMessage("Unable to update ready status.");
                } else {
                    readyRoom.showRoomLobby(player);
                }
                return true;
            case "status":
                Optional<GameTable> tableForStatus = tableManager.getTableByPlayer(player);
                if (tableForStatus.isEmpty()) {
                    player.sendMessage("You are not in a room.");
                    return true;
                }
                GameTable statusRoom = tableForStatus.get();
                if (!statusRoom.isRoomMode()) {
                    player.sendMessage("This table is not a room.");
                    return true;
                }
                statusRoom.showRoomLobby(player);
                return true;
            default:
                sendRoomUsage(player);
                return true;
        }
    }

    private void sendRoomUsage(Player player) {
        player.sendMessage("/mj room create | join <code> | rules | ready | status");
    }

    private void sendRoomRulesUsage(Player player) {
        player.sendMessage("/mj room rules <rule> <on|off>");
        player.sendMessage("/mj room rules preset <default|kuitan|classic>");
        player.sendMessage("Rules: redDora, openTanyao, ippatsu, uraDora.");
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
        player.sendMessage("/mj create | join <tableId> | leave | start | nexthand | hand | ron | pon | chi [index] | kan [index] | riichi | tsumo | log export|replay [ticks] | info | list | disband <tableId>");
        player.sendMessage("/mj room create | join <code> | rules | ready | status");
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
