package wiki.creeper.mahjong.ui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import wiki.creeper.mahjong.ai.BotDifficulty;
import wiki.creeper.mahjong.game.SeatWind;
import wiki.creeper.mahjong.table.GameTable;
import wiki.creeper.mahjong.table.TableManager;
import wiki.creeper.mahjong.ui.RoomInventory;
import wiki.creeper.mahjong.ui.RoomMenuType;

public class MahjongListener implements Listener {

    private final TableManager tableManager;

    public MahjongListener(TableManager tableManager) {
        this.tableManager = tableManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (holder instanceof HandInventory) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) {
                return;
            }
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() == Material.AIR) {
                return;
            }
            Player player = (Player) event.getWhoClicked();
            HandInventory hand = (HandInventory) holder;
            if (!hand.getPlayerId().equals(player.getUniqueId())) {
                return;
            }
            tableManager.getTableByPlayer(player).ifPresent(table -> table.requestDiscard(player, item));
            return;
        }
        if (holder instanceof RoomInventory) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) {
                return;
            }
            Player player = (Player) event.getWhoClicked();
            RoomInventory menu = (RoomInventory) holder;
            GameTable table = tableManager.getTable(menu.getTableId()).orElse(null);
            if (table == null) {
                return;
            }
            if (!table.getPlayers().contains(player.getUniqueId())) {
                return;
            }
            handleRoomMenuClick(table, player, menu.getType(), event.getSlot());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        tableManager.leaveTable(event.getPlayer());
    }

    private void handleRoomMenuClick(GameTable table, Player player, RoomMenuType type, int slot) {
        if (type == RoomMenuType.RULES) {
            handleRulesMenuClick(table, player, slot);
            return;
        }
        if (type == RoomMenuType.BOTS) {
            handleBotsMenuClick(table, player, slot);
            return;
        }
        switch (slot) {
            case RoomInventory.SLOT_LOBBY_EAST:
                table.requestSeat(player, SeatWind.EAST);
                break;
            case RoomInventory.SLOT_LOBBY_SOUTH:
                table.requestSeat(player, SeatWind.SOUTH);
                break;
            case RoomInventory.SLOT_LOBBY_WEST:
                table.requestSeat(player, SeatWind.WEST);
                break;
            case RoomInventory.SLOT_LOBBY_NORTH:
                table.requestSeat(player, SeatWind.NORTH);
                break;
            case RoomInventory.SLOT_LOBBY_READY:
                table.toggleReady(player);
                break;
            case RoomInventory.SLOT_LOBBY_START:
                table.requestStart(player);
                break;
            case RoomInventory.SLOT_LOBBY_RULES:
                table.showRoomRules(player);
                break;
            case RoomInventory.SLOT_LOBBY_BOTS:
                table.showRoomBots(player);
                break;
            case RoomInventory.SLOT_LOBBY_LEAVE:
                tableManager.leaveTable(player);
                break;
            default:
                break;
        }
    }

    private void handleRulesMenuClick(GameTable table, Player player, int slot) {
        if (slot == RoomInventory.SLOT_RULES_BACK) {
            table.openRoomLobbyGui(player);
            return;
        }
        boolean actionable = slot == RoomInventory.SLOT_RULES_RED_DORA
                || slot == RoomInventory.SLOT_RULES_OPEN_TANYAO
                || slot == RoomInventory.SLOT_RULES_IPPATSU
                || slot == RoomInventory.SLOT_RULES_URA_DORA
                || slot == RoomInventory.SLOT_RULES_BOTS
                || slot == RoomInventory.SLOT_RULES_COACH
                || slot == RoomInventory.SLOT_RULES_COACH_RANK
                || slot == RoomInventory.SLOT_RULES_PRESET_DEFAULT
                || slot == RoomInventory.SLOT_RULES_PRESET_KUITAN
                || slot == RoomInventory.SLOT_RULES_PRESET_CLASSIC;
        if (!actionable) {
            return;
        }
        if (!table.isHost(player.getUniqueId())) {
            player.sendMessage("호스트만 규칙을 변경할 수 있어요.");
            return;
        }
        switch (slot) {
            case RoomInventory.SLOT_RULES_RED_DORA:
                table.updateRule("redDora", null);
                break;
            case RoomInventory.SLOT_RULES_OPEN_TANYAO:
                table.updateRule("openTanyao", null);
                break;
            case RoomInventory.SLOT_RULES_IPPATSU:
                table.updateRule("ippatsu", null);
                break;
            case RoomInventory.SLOT_RULES_URA_DORA:
                table.updateRule("uraDora", null);
                break;
            case RoomInventory.SLOT_RULES_BOTS:
                table.updateRule("bots", null);
                break;
            case RoomInventory.SLOT_RULES_COACH:
                table.updateRule("coach", null);
                break;
            case RoomInventory.SLOT_RULES_COACH_RANK:
                table.updateRule("coachRank", null);
                break;
            case RoomInventory.SLOT_RULES_PRESET_DEFAULT:
                table.applyPreset("default");
                break;
            case RoomInventory.SLOT_RULES_PRESET_KUITAN:
                table.applyPreset("kuitan");
                break;
            case RoomInventory.SLOT_RULES_PRESET_CLASSIC:
                table.applyPreset("classic");
                break;
            default:
                break;
        }
    }

    private void handleBotsMenuClick(GameTable table, Player player, int slot) {
        if (slot == RoomInventory.SLOT_BOTS_BACK) {
            table.openRoomLobbyGui(player);
            return;
        }
        boolean actionable = slot == RoomInventory.SLOT_BOTS_ADD_BEGINNER
                || slot == RoomInventory.SLOT_BOTS_ADD_NORMAL
                || slot == RoomInventory.SLOT_BOTS_ADD_HARD
                || slot == RoomInventory.SLOT_BOTS_REMOVE_BEGINNER
                || slot == RoomInventory.SLOT_BOTS_REMOVE_NORMAL
                || slot == RoomInventory.SLOT_BOTS_REMOVE_HARD;
        if (!actionable) {
            return;
        }
        if (!table.isHost(player.getUniqueId())) {
            player.sendMessage("호스트만 봇을 초대할 수 있어요.");
            return;
        }
        switch (slot) {
            case RoomInventory.SLOT_BOTS_ADD_BEGINNER:
                table.addBot(BotDifficulty.BEGINNER);
                break;
            case RoomInventory.SLOT_BOTS_ADD_NORMAL:
                table.addBot(BotDifficulty.NORMAL);
                break;
            case RoomInventory.SLOT_BOTS_ADD_HARD:
                table.addBot(BotDifficulty.HARD);
                break;
            case RoomInventory.SLOT_BOTS_REMOVE_BEGINNER:
                table.removeBot(BotDifficulty.BEGINNER);
                break;
            case RoomInventory.SLOT_BOTS_REMOVE_NORMAL:
                table.removeBot(BotDifficulty.NORMAL);
                break;
            case RoomInventory.SLOT_BOTS_REMOVE_HARD:
                table.removeBot(BotDifficulty.HARD);
                break;
            default:
                break;
        }
        table.openRoomBotsGui(player);
    }
}
