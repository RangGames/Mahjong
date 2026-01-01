package wiki.creeper.mahjong.ai;

import java.util.List;
import java.util.UUID;
import wiki.creeper.mahjong.game.Meld;
import wiki.creeper.mahjong.game.SeatWind;
import wiki.creeper.mahjong.game.Tile;

public final class PrivateState {
    private final UUID playerId;
    private final SeatWind seatWind;
    private final List<Tile> concealed;
    private final List<Meld> melds;
    private final boolean riichiDeclared;
    private final int points;

    public PrivateState(UUID playerId, SeatWind seatWind, List<Tile> concealed,
                        List<Meld> melds, boolean riichiDeclared, int points) {
        this.playerId = playerId;
        this.seatWind = seatWind;
        this.concealed = concealed == null ? List.of() : List.copyOf(concealed);
        this.melds = melds == null ? List.of() : List.copyOf(melds);
        this.riichiDeclared = riichiDeclared;
        this.points = points;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public SeatWind getSeatWind() {
        return seatWind;
    }

    public List<Tile> getConcealed() {
        return concealed;
    }

    public List<Meld> getMelds() {
        return melds;
    }

    public boolean isRiichiDeclared() {
        return riichiDeclared;
    }

    public int getPoints() {
        return points;
    }
}
