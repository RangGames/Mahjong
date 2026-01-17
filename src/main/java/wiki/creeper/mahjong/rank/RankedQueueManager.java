package wiki.creeper.mahjong.rank;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import wiki.creeper.mahjong.table.GameTable;
import wiki.creeper.mahjong.table.TableManager;

public final class RankedQueueManager {
    private final JavaPlugin plugin;
    private final TableManager tableManager;
    private final RankedManager rankedManager;
    private final LinkedHashSet<UUID> queue = new LinkedHashSet<>();

    public RankedQueueManager(JavaPlugin plugin, TableManager tableManager, RankedManager rankedManager) {
        this.plugin = plugin;
        this.tableManager = tableManager;
        this.rankedManager = rankedManager;
    }

    public boolean isEnabled() {
        return rankedManager.isAvailable() && plugin.getConfig().getBoolean("ranked.queue.enabled", true);
    }

    public boolean isQueued(UUID playerId) {
        synchronized (queue) {
            return queue.contains(playerId);
        }
    }

    public int getQueueSize() {
        synchronized (queue) {
            return queue.size();
        }
    }

    public int getPosition(UUID playerId) {
        synchronized (queue) {
            int position = 1;
            for (UUID id : queue) {
                if (id.equals(playerId)) {
                    return position;
                }
                position++;
            }
        }
        return -1;
    }

    public boolean enqueue(Player player) {
        if (player == null) {
            return false;
        }
        if (!isEnabled()) {
            player.sendMessage("랭크전 매칭이 비활성화되어 있어요.");
            return false;
        }
        if (tableManager.getTableByPlayer(player).isPresent() || tableManager.getTableBySpectator(player).isPresent()) {
            player.sendMessage("이미 테이블에 참여 중이라 매칭 대기열에 들어갈 수 없어요.");
            return false;
        }
        UUID playerId = player.getUniqueId();
        synchronized (queue) {
            if (queue.contains(playerId)) {
                int position = getPosition(playerId);
                player.sendMessage("이미 매칭 대기열에 있어요. 현재 순번: " + position + " (" + queue.size() + "/4)");
                return false;
            }
            queue.add(playerId);
        }
        int position = getPosition(playerId);
        player.sendMessage("랭크전 매칭 대기열에 등록됐어요. 순번: " + position + " (" + getQueueSize() + "/4)");
        tryMatch();
        return true;
    }

    public boolean leave(Player player) {
        if (player == null) {
            return false;
        }
        boolean removed;
        synchronized (queue) {
            removed = queue.remove(player.getUniqueId());
        }
        if (removed) {
            player.sendMessage("랭크전 매칭 대기열에서 나왔어요.");
        }
        return removed;
    }

    public void remove(UUID playerId) {
        if (playerId == null) {
            return;
        }
        synchronized (queue) {
            queue.remove(playerId);
        }
    }

    private void tryMatch() {
        if (!isEnabled()) {
            return;
        }
        while (true) {
            List<Player> matched = pollMatchPlayers();
            if (matched.isEmpty()) {
                return;
            }
            if (!startMatch(matched)) {
                requeuePlayers(matched);
                return;
            }
        }
    }

    private List<Player> pollMatchPlayers() {
        synchronized (queue) {
            cleanQueueLocked();
            if (queue.size() < 4) {
                return List.of();
            }
            List<Player> players = new ArrayList<>(4);
            Iterator<UUID> iterator = queue.iterator();
            while (iterator.hasNext() && players.size() < 4) {
                UUID id = iterator.next();
                Player player = plugin.getServer().getPlayer(id);
                if (player == null) {
                    iterator.remove();
                    continue;
                }
                players.add(player);
                iterator.remove();
            }
            if (players.size() < 4) {
                for (Player player : players) {
                    queue.add(player.getUniqueId());
                }
                return List.of();
            }
            return players;
        }
    }

    private void cleanQueueLocked() {
        Iterator<UUID> iterator = queue.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            Player player = plugin.getServer().getPlayer(id);
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (tableManager.getTableByPlayer(player).isPresent() || tableManager.getTableBySpectator(player).isPresent()) {
                iterator.remove();
            }
        }
    }

    private boolean startMatch(List<Player> players) {
        if (players.size() < 4) {
            return false;
        }
        Player owner = players.get(0);
        GameTable table = tableManager.createRankedTable(owner);
        boolean success = true;
        for (int i = 1; i < players.size(); i++) {
            if (!tableManager.joinTable(players.get(i), table.getId())) {
                success = false;
                break;
            }
        }
        if (!success) {
            for (Player player : players) {
                tableManager.leaveTable(player);
            }
            return false;
        }
        if (!table.prepareRankedMatch()) {
            for (Player player : players) {
                tableManager.leaveTable(player);
            }
            return false;
        }
        table.requestStart(owner);
        String code = table.getRoomCode();
        String labelText = code != null ? "코드: " + code : "ID: " + table.getId();
        for (Player player : players) {
            player.sendMessage("랭크전 매칭 완료! " + labelText);
        }
        return true;
    }

    private void requeuePlayers(List<Player> players) {
        synchronized (queue) {
            for (Player player : players) {
                if (player == null) {
                    continue;
                }
                if (tableManager.getTableByPlayer(player).isPresent() || tableManager.getTableBySpectator(player).isPresent()) {
                    continue;
                }
                queue.add(player.getUniqueId());
            }
        }
        for (Player player : players) {
            if (player != null) {
                player.sendMessage("매칭에 실패했어요. 다시 대기열에 넣었어요.");
            }
        }
    }
}
