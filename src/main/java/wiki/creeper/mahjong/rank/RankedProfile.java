package wiki.creeper.mahjong.rank;

import java.util.UUID;

public final class RankedProfile {
    private final UUID playerId;
    private String lastKnownName;
    private double rating;
    private int games;
    private int firsts;
    private int seconds;
    private int thirds;
    private int fourths;

    RankedProfile(UUID playerId, String lastKnownName, double rating, int games, int firsts, int seconds, int thirds, int fourths) {
        this.playerId = playerId;
        this.lastKnownName = lastKnownName;
        this.rating = rating;
        this.games = games;
        this.firsts = firsts;
        this.seconds = seconds;
        this.thirds = thirds;
        this.fourths = fourths;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getLastKnownName() {
        return lastKnownName;
    }

    public double getRating() {
        return rating;
    }

    public int getGames() {
        return games;
    }

    public int getFirsts() {
        return firsts;
    }

    public int getSeconds() {
        return seconds;
    }

    public int getThirds() {
        return thirds;
    }

    public int getFourths() {
        return fourths;
    }

    void setLastKnownName(String lastKnownName) {
        if (lastKnownName == null || lastKnownName.isBlank()) {
            return;
        }
        this.lastKnownName = lastKnownName;
    }

    void applyResult(int placement, double delta) {
        rating = roundOneDecimal(rating + delta);
        games++;
        switch (placement) {
            case 1:
                firsts++;
                break;
            case 2:
                seconds++;
                break;
            case 3:
                thirds++;
                break;
            case 4:
                fourths++;
                break;
            default:
                break;
        }
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
