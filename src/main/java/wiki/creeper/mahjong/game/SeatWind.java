package wiki.creeper.mahjong.game;

public enum SeatWind {
    EAST,
    SOUTH,
    WEST,
    NORTH;

    public SeatWind next() {
        switch (this) {
            case EAST:
                return SOUTH;
            case SOUTH:
                return WEST;
            case WEST:
                return NORTH;
            case NORTH:
            default:
                return EAST;
        }
    }

    public int order() {
        switch (this) {
            case EAST:
                return 0;
            case SOUTH:
                return 1;
            case WEST:
                return 2;
            case NORTH:
            default:
                return 3;
        }
    }
}
