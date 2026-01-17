package wiki.creeper.mahjong.api.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import wiki.creeper.mahjong.game.ScoreResult;
import wiki.creeper.mahjong.table.GameTable;

public final class MahjongWinEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final GameTable table;
    private final UUID winnerId;
    private final Player winner;
    private final UUID discarderId;
    private final Player discarder;
    private final boolean tsumo;
    private final ScoreResult score;

    public MahjongWinEvent(GameTable table, UUID winnerId, Player winner, UUID discarderId,
                           Player discarder, boolean tsumo, ScoreResult score) {
        this.table = table;
        this.winnerId = winnerId;
        this.winner = winner;
        this.discarderId = discarderId;
        this.discarder = discarder;
        this.tsumo = tsumo;
        this.score = score;
    }

    public GameTable getTable() {
        return table;
    }

    public UUID getWinnerId() {
        return winnerId;
    }

    public Player getWinner() {
        return winner;
    }

    public UUID getDiscarderId() {
        return discarderId;
    }

    public Player getDiscarder() {
        return discarder;
    }

    public boolean isTsumo() {
        return tsumo;
    }

    public ScoreResult getScore() {
        return score;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
