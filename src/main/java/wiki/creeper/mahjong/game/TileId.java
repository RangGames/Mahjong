package wiki.creeper.mahjong.game;

import java.util.Objects;

public final class TileId {

    private final TileSuit suit;
    private final int rank;
    private final boolean red;

    private TileId(TileSuit suit, int rank, boolean red) {
        this.suit = Objects.requireNonNull(suit, "suit");
        this.rank = rank;
        this.red = red;
        validate();
    }

    public static TileId of(TileSuit suit, int rank, boolean red) {
        return new TileId(suit, rank, red);
    }

    public static TileId parse(String value) {
        if (value == null || value.length() < 2) {
            throw new IllegalArgumentException("Invalid tile id: " + value);
        }
        char suitCode = value.charAt(0);
        TileSuit suit = TileSuit.fromCode(suitCode);
        String rest = value.substring(1).toLowerCase();

        boolean red = false;
        String digits = rest;
        if (rest.endsWith("r")) {
            red = true;
            digits = rest.substring(0, rest.length() - 1);
        }
        if (digits.length() != 1) {
            throw new IllegalArgumentException("Invalid tile id: " + value);
        }
        char digitChar = digits.charAt(0);
        int rank;
        if (digitChar == '0' && TileSuit.isSuited(suit)) {
            rank = 5;
            red = true;
        } else {
            rank = Character.digit(digitChar, 10);
        }
        if (rank <= 0) {
            throw new IllegalArgumentException("Invalid tile id: " + value);
        }
        return new TileId(suit, rank, red);
    }

    public TileSuit getSuit() {
        return suit;
    }

    public int getRank() {
        return rank;
    }

    public boolean isRed() {
        return red;
    }

    public boolean isHonor() {
        return suit == TileSuit.HONOR;
    }

    public boolean isTerminal() {
        return !isHonor() && (rank == 1 || rank == 9);
    }

    public boolean isSimple() {
        return !isHonor() && rank >= 2 && rank <= 8;
    }

    public String toShortString() {
        if (red && TileSuit.isSuited(suit)) {
            return "" + suit.getCode() + "0";
        }
        return "" + suit.getCode() + rank;
    }

    private void validate() {
        if (suit == TileSuit.HONOR) {
            if (rank < 1 || rank > 7) {
                throw new IllegalArgumentException("Honor rank out of range: " + rank);
            }
            if (red) {
                throw new IllegalArgumentException("Honor tiles cannot be red.");
            }
        } else {
            if (rank < 1 || rank > 9) {
                throw new IllegalArgumentException("Suit rank out of range: " + rank);
            }
            if (red && rank != 5) {
                throw new IllegalArgumentException("Only 5 can be red: " + rank);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TileId)) {
            return false;
        }
        TileId tileId = (TileId) o;
        return rank == tileId.rank && red == tileId.red && suit == tileId.suit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(suit, rank, red);
    }

    @Override
    public String toString() {
        return toShortString();
    }
}
