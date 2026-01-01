package wiki.creeper.mahjong.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import wiki.creeper.mahjong.game.SeatWind;
import wiki.creeper.mahjong.game.TileId;

public final class PublicState {
    private final Map<UUID, List<TileId>> discards;
    private final Map<UUID, List<TileId>> meldTiles;
    private final Map<UUID, Integer> points;
    private final Map<UUID, Boolean> riichiDeclared;
    private final List<TileId> doraIndicators;
    private final SeatWind roundWind;
    private final SeatWind dealerWind;
    private final int remainingTiles;

    public PublicState(Map<UUID, List<TileId>> discards,
                       Map<UUID, List<TileId>> meldTiles,
                       Map<UUID, Integer> points,
                       Map<UUID, Boolean> riichiDeclared,
                       List<TileId> doraIndicators,
                       SeatWind roundWind,
                       SeatWind dealerWind,
                       int remainingTiles) {
        this.discards = discards == null ? Map.of() : Map.copyOf(discards);
        this.meldTiles = meldTiles == null ? Map.of() : Map.copyOf(meldTiles);
        this.points = points == null ? Map.of() : Map.copyOf(points);
        this.riichiDeclared = riichiDeclared == null ? Map.of() : Map.copyOf(riichiDeclared);
        this.doraIndicators = doraIndicators == null ? List.of() : List.copyOf(doraIndicators);
        this.roundWind = roundWind;
        this.dealerWind = dealerWind;
        this.remainingTiles = remainingTiles;
    }

    public Map<UUID, List<TileId>> getDiscards() {
        return discards;
    }

    public Map<UUID, List<TileId>> getMeldTiles() {
        return meldTiles;
    }

    public Map<UUID, Integer> getPoints() {
        return points;
    }

    public Map<UUID, Boolean> getRiichiDeclared() {
        return riichiDeclared;
    }

    public List<TileId> getDoraIndicators() {
        return doraIndicators;
    }

    public SeatWind getRoundWind() {
        return roundWind;
    }

    public SeatWind getDealerWind() {
        return dealerWind;
    }

    public int getRemainingTiles() {
        return remainingTiles;
    }
}
