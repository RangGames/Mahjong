package wiki.creeper.mahjong.game;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class HandAnalysis {

    private final List<HandMeld> melds;
    private final TileId pair;

    public HandAnalysis(List<HandMeld> melds, TileId pair) {
        this.melds = List.copyOf(Objects.requireNonNull(melds, "melds"));
        this.pair = Objects.requireNonNull(pair, "pair");
    }

    public List<HandMeld> getMelds() {
        return Collections.unmodifiableList(melds);
    }

    public TileId getPair() {
        return pair;
    }
}
