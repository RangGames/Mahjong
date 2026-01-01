package wiki.creeper.mahjong.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Hand {

    private final List<Tile> concealed = new ArrayList<>();
    private final List<Meld> melds = new ArrayList<>();
    private boolean riichiDeclared;
    private boolean furiten;
    private boolean ippatsuEligible;
    private boolean riichiPendingDiscard;

    public List<Tile> getConcealed() {
        return Collections.unmodifiableList(concealed);
    }

    public List<Meld> getMelds() {
        return Collections.unmodifiableList(melds);
    }

    public void addTile(Tile tile) {
        concealed.add(Objects.requireNonNull(tile, "tile"));
    }

    public boolean removeTile(Tile tile) {
        return concealed.remove(tile);
    }

    public void addMeld(Meld meld) {
        melds.add(Objects.requireNonNull(meld, "meld"));
    }

    public boolean removeMeld(Meld meld) {
        return melds.remove(meld);
    }

    public boolean replaceMeld(Meld oldMeld, Meld newMeld) {
        int index = melds.indexOf(oldMeld);
        if (index < 0) {
            return false;
        }
        melds.set(index, Objects.requireNonNull(newMeld, "newMeld"));
        return true;
    }

    public boolean isRiichiDeclared() {
        return riichiDeclared;
    }

    public void setRiichiDeclared(boolean riichiDeclared) {
        this.riichiDeclared = riichiDeclared;
    }

    public boolean isFuriten() {
        return furiten;
    }

    public void setFuriten(boolean furiten) {
        this.furiten = furiten;
    }

    public boolean isIppatsuEligible() {
        return ippatsuEligible;
    }

    public void setIppatsuEligible(boolean ippatsuEligible) {
        this.ippatsuEligible = ippatsuEligible;
    }

    public boolean isRiichiPendingDiscard() {
        return riichiPendingDiscard;
    }

    public void setRiichiPendingDiscard(boolean riichiPendingDiscard) {
        this.riichiPendingDiscard = riichiPendingDiscard;
    }

    public boolean isClosed() {
        for (Meld meld : melds) {
            if (meld.getType() != MeldType.KAN_CLOSED) {
                return false;
            }
        }
        return true;
    }

    public List<Tile> getAllTiles() {
        List<Tile> tiles = new ArrayList<>(concealed);
        for (Meld meld : melds) {
            tiles.addAll(meld.getTiles());
        }
        return tiles;
    }

    public void reset() {
        concealed.clear();
        melds.clear();
        riichiDeclared = false;
        furiten = false;
        ippatsuEligible = false;
        riichiPendingDiscard = false;
    }
}
