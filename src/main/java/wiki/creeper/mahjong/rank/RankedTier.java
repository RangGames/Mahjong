package wiki.creeper.mahjong.rank;

public final class RankedTier {
    private final String id;
    private final String name;
    private final double minRating;

    public RankedTier(String id, String name, double minRating) {
        this.id = id;
        this.name = name;
        this.minRating = minRating;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getMinRating() {
        return minRating;
    }
}
