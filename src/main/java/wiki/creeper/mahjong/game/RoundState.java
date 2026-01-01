package wiki.creeper.mahjong.game;

public class RoundState {

    private SeatWind roundWind;
    private SeatWind dealerWind;
    private int roundIndex;
    private int honba;
    private int riichiPot;
    private int remainingTiles;
    private int handsPlayed;

    public RoundState(SeatWind roundWind, SeatWind dealerWind, int remainingTiles) {
        this.roundWind = roundWind;
        this.dealerWind = dealerWind;
        this.roundIndex = roundWind.order();
        this.remainingTiles = remainingTiles;
    }

    public SeatWind getRoundWind() {
        return roundWind;
    }

    public SeatWind getDealerWind() {
        return dealerWind;
    }

    public void setDealerWind(SeatWind dealerWind) {
        this.dealerWind = dealerWind;
    }

    public int getRoundIndex() {
        return roundIndex;
    }

    public int getKyoku() {
        return dealerWind.order() + 1;
    }

    public int getHonba() {
        return honba;
    }

    public void addHonba(int delta) {
        honba += delta;
    }

    public void setHonba(int honba) {
        this.honba = honba;
    }

    public int getRiichiPot() {
        return riichiPot;
    }

    public void addRiichiPot(int delta) {
        riichiPot += delta;
    }

    public int getRemainingTiles() {
        return remainingTiles;
    }

    public void setRemainingTiles(int remainingTiles) {
        this.remainingTiles = remainingTiles;
    }

    public int getHandsPlayed() {
        return handsPlayed;
    }

    public void incrementHandsPlayed() {
        handsPlayed++;
    }

    public void setHandsPlayed(int handsPlayed) {
        this.handsPlayed = handsPlayed;
    }

    public void advanceRoundWind() {
        this.roundIndex++;
        this.roundWind = this.roundWind.next();
    }
}
