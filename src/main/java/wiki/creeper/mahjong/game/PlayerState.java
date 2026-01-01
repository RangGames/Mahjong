package wiki.creeper.mahjong.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class PlayerState {

    private final UUID playerId;
    private final SeatWind seatWind;
    private final Hand hand;
    private final List<Tile> discards = new ArrayList<>();
    private int points;

    public PlayerState(UUID playerId, SeatWind seatWind, int startingPoints) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.seatWind = Objects.requireNonNull(seatWind, "seatWind");
        this.hand = new Hand();
        this.points = startingPoints;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public SeatWind getSeatWind() {
        return seatWind;
    }

    public Hand getHand() {
        return hand;
    }

    public int getPoints() {
        return points;
    }

    public void addPoints(int delta) {
        points += delta;
    }

    public List<Tile> getDiscards() {
        return Collections.unmodifiableList(discards);
    }

    public void addDiscard(Tile tile) {
        discards.add(Objects.requireNonNull(tile, "tile"));
    }

    public void resetForNewHand() {
        discards.clear();
        hand.reset();
    }
}
