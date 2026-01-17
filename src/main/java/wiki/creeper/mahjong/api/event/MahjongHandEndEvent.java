package wiki.creeper.mahjong.api.event;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import wiki.creeper.mahjong.game.RoundState;
import wiki.creeper.mahjong.game.ScoreResult;
import wiki.creeper.mahjong.table.GameTable;

public final class MahjongHandEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final GameTable table;
    private final UUID winnerId;
    private final UUID discarderId;
    private final boolean tsumo;
    private final ScoreResult score;
    private final List<UUID> tenpaiPlayers;
    private final Map<UUID, Integer> pointDeltas;
    private final Map<UUID, Integer> pointsAfter;
    private final int honbaApplied;
    private final int riichiPotApplied;
    private final boolean gameOver;
    private final RoundState nextRound;

    public MahjongHandEndEvent(GameTable table, UUID winnerId, UUID discarderId, boolean tsumo, ScoreResult score,
                               List<UUID> tenpaiPlayers, Map<UUID, Integer> pointDeltas,
                               Map<UUID, Integer> pointsAfter, int honbaApplied, int riichiPotApplied,
                               boolean gameOver, RoundState nextRound) {
        this.table = table;
        this.winnerId = winnerId;
        this.discarderId = discarderId;
        this.tsumo = tsumo;
        this.score = score;
        this.tenpaiPlayers = tenpaiPlayers == null ? List.of() : List.copyOf(tenpaiPlayers);
        this.pointDeltas = pointDeltas == null ? Map.of() : Map.copyOf(pointDeltas);
        this.pointsAfter = pointsAfter == null ? Map.of() : Map.copyOf(pointsAfter);
        this.honbaApplied = honbaApplied;
        this.riichiPotApplied = riichiPotApplied;
        this.gameOver = gameOver;
        this.nextRound = nextRound;
    }

    public GameTable getTable() {
        return table;
    }

    public UUID getWinnerId() {
        return winnerId;
    }

    public UUID getDiscarderId() {
        return discarderId;
    }

    public boolean isTsumo() {
        return tsumo;
    }

    public ScoreResult getScore() {
        return score;
    }

    public List<UUID> getTenpaiPlayers() {
        return tenpaiPlayers;
    }

    public Map<UUID, Integer> getPointDeltas() {
        return pointDeltas;
    }

    public Map<UUID, Integer> getPointsAfter() {
        return pointsAfter;
    }

    public int getHonbaApplied() {
        return honbaApplied;
    }

    public int getRiichiPotApplied() {
        return riichiPotApplied;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public RoundState getNextRound() {
        return nextRound;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
