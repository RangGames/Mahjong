package wiki.creeper.mahjong.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Wall {

    public static final int TILE_COUNT = 136;
    public static final int DEAD_WALL_SIZE = 14;

    private final List<Tile> tiles;
    private final int deadWallStart;
    private final List<Integer> doraIndicatorIndices;
    private final List<Integer> uraIndicatorIndices;
    private int revealedDoraCount;
    private int drawIndex;
    private int rinshanIndex;

    private Wall(List<Tile> tiles, List<Integer> doraIndicatorIndices, List<Integer> uraIndicatorIndices, int deadWallStart) {
        this.tiles = tiles;
        this.doraIndicatorIndices = doraIndicatorIndices;
        this.uraIndicatorIndices = uraIndicatorIndices;
        this.deadWallStart = deadWallStart;
        this.drawIndex = 0;
        this.rinshanIndex = tiles.size() - 1;
        this.revealedDoraCount = Math.min(1, doraIndicatorIndices.size());
    }

    public static Wall create(long seed, boolean redDora) {
        return create(new Random(seed), redDora);
    }

    public static Wall create(Random random, boolean redDora) {
        List<Tile> tiles = new ArrayList<>(TILE_COUNT);
        int instanceId = 0;
        for (TileSuit suit : TileSuit.values()) {
            int maxRank = suit == TileSuit.HONOR ? 7 : 9;
            for (int rank = 1; rank <= maxRank; rank++) {
                int copies = 4;
                int redCopies = 0;
                if (redDora && TileSuit.isSuited(suit) && rank == 5) {
                    copies = 3;
                    redCopies = 1;
                }
                for (int i = 0; i < copies; i++) {
                    tiles.add(new Tile(TileId.of(suit, rank, false), instanceId++));
                }
                for (int i = 0; i < redCopies; i++) {
                    tiles.add(new Tile(TileId.of(suit, rank, true), instanceId++));
                }
            }
        }

        Collections.shuffle(tiles, random);
        int deadWallStart = tiles.size() - DEAD_WALL_SIZE;
        List<Integer> doraIndicators = new ArrayList<>();
        List<Integer> uraIndicators = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int doraIndex = deadWallStart + 4 + (i * 2);
            int uraIndex = deadWallStart + 5 + (i * 2);
            if (doraIndex < tiles.size()) {
                doraIndicators.add(doraIndex);
            }
            if (uraIndex < tiles.size()) {
                uraIndicators.add(uraIndex);
            }
        }

        return new Wall(tiles, doraIndicators, uraIndicators, deadWallStart);
    }

    public int getRemainingLiveCount() {
        return Math.max(0, deadWallStart - drawIndex);
    }

    public Tile draw() {
        if (drawIndex >= deadWallStart) {
            return null;
        }
        return tiles.get(drawIndex++);
    }

    public Tile drawRinshan() {
        if (rinshanIndex < deadWallStart) {
            return null;
        }
        return tiles.get(rinshanIndex--);
    }

    public List<Tile> getDoraIndicators() {
        List<Tile> indicators = new ArrayList<>();
        for (int i = 0; i < revealedDoraCount && i < doraIndicatorIndices.size(); i++) {
            int index = doraIndicatorIndices.get(i);
            indicators.add(tiles.get(index));
        }
        return Collections.unmodifiableList(indicators);
    }

    public List<Tile> getUraDoraIndicators() {
        List<Tile> indicators = new ArrayList<>();
        for (int i = 0; i < revealedDoraCount && i < uraIndicatorIndices.size(); i++) {
            int index = uraIndicatorIndices.get(i);
            indicators.add(tiles.get(index));
        }
        return Collections.unmodifiableList(indicators);
    }

    public void revealNextDora() {
        if (revealedDoraCount < doraIndicatorIndices.size()) {
            revealedDoraCount++;
        }
    }
}
