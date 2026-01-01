package wiki.creeper.mahjong.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class GameEngine {

    private final List<UUID> turnOrder;
    private final Map<UUID, PlayerState> players;
    private final RoundState roundState;
    private final Wall wall;
    private final GameRules rules;
    private final List<CallRequest> pendingCalls = new ArrayList<>();
    private GameState state = GameState.LOBBY;
    private int activeIndex;
    private Tile lastDiscard;
    private UUID lastDiscarder;
    private Tile lastDrawnTile;
    private UUID lastDrawnPlayer;
    private boolean lastDrawRinshan;
    private int drawSequence;
    private Tile winningTile;
    private UUID winner;
    private boolean tsumoWin;

    public GameEngine(List<UUID> playerIds, int startingPoints, GameRules rules, long seed) {
        if (playerIds.size() != 4) {
            throw new IllegalArgumentException("Exactly 4 players are required.");
        }
        this.turnOrder = new ArrayList<>(playerIds);
        this.players = new HashMap<>();
        this.rules = Objects.requireNonNull(rules, "rules");
        this.wall = Wall.create(seed, rules.isRedDoraEnabled());
        this.roundState = new RoundState(SeatWind.EAST, SeatWind.EAST, wall.getRemainingLiveCount());

        SeatWind wind = SeatWind.EAST;
        for (UUID id : turnOrder) {
            players.put(id, new PlayerState(id, wind, startingPoints));
            wind = wind.next();
        }
    }

    public GameEngine(List<UUID> playerIds, Map<UUID, PlayerState> existingPlayers, RoundState roundState, GameRules rules, long seed) {
        if (playerIds.size() != 4) {
            throw new IllegalArgumentException("Exactly 4 players are required.");
        }
        this.turnOrder = new ArrayList<>(playerIds);
        this.players = new HashMap<>();
        for (UUID id : turnOrder) {
            PlayerState state = existingPlayers.get(id);
            if (state == null) {
                throw new IllegalArgumentException("Missing player state for " + id);
            }
            this.players.put(id, state);
        }
        this.rules = Objects.requireNonNull(rules, "rules");
        this.wall = Wall.create(seed, rules.isRedDoraEnabled());
        this.roundState = Objects.requireNonNull(roundState, "roundState");
        this.roundState.setRemainingTiles(wall.getRemainingLiveCount());
    }

    public GameState getState() {
        return state;
    }

    public UUID getActivePlayer() {
        return turnOrder.get(activeIndex);
    }

    public UUID getNextPlayerForCall() {
        return getNextPlayer();
    }

    public PlayerState getPlayerState(UUID playerId) {
        return players.get(playerId);
    }

    public RoundState getRoundState() {
        return roundState;
    }

    public GameRules getRules() {
        return rules;
    }

    public List<Tile> getDoraIndicators() {
        return wall.getDoraIndicators();
    }

    public List<Tile> getUraDoraIndicators() {
        return wall.getUraDoraIndicators();
    }

    public Tile getLastDiscard() {
        return lastDiscard;
    }

    public Tile getLastDrawnTile() {
        return lastDrawnTile;
    }

    public UUID getLastDrawnPlayer() {
        return lastDrawnPlayer;
    }

    public boolean isLastDrawRinshan() {
        return lastDrawRinshan;
    }

    public int getDrawSequence() {
        return drawSequence;
    }

    public UUID getLastDiscarder() {
        return lastDiscarder;
    }

    public boolean isCallWindowActive() {
        return state == GameState.CALL_WINDOW;
    }

    public UUID getWinner() {
        return winner;
    }

    public Tile getWinningTile() {
        return winningTile;
    }

    public boolean isTsumoWin() {
        return tsumoWin;
    }

    public void startRound() {
        if (state != GameState.LOBBY) {
            return;
        }
        winner = null;
        tsumoWin = false;
        lastDiscard = null;
        lastDiscarder = null;
        lastDrawnTile = null;
        lastDrawnPlayer = null;
        lastDrawRinshan = false;
        drawSequence = 0;
        winningTile = null;
        pendingCalls.clear();
        dealInitialHands();
        activeIndex = 0;
        drawForActive();
    }

    public Tile drawForActive() {
        if (state == GameState.HAND_END) {
            return null;
        }
        state = GameState.TURN_DRAW;
        Tile tile = wall.draw();
        if (tile == null) {
            state = GameState.HAND_END;
            return null;
        }
        PlayerState active = getActivePlayerState();
        active.getHand().setFuriten(false);
        active.getHand().addTile(tile);
        lastDrawnTile = tile;
        lastDrawnPlayer = getActivePlayer();
        lastDrawRinshan = false;
        drawSequence++;
        roundState.setRemainingTiles(wall.getRemainingLiveCount());
        state = GameState.TURN_DISCARD;
        return tile;
    }

    public boolean canTsumo(UUID playerId) {
        if (state != GameState.TURN_DISCARD) {
            return false;
        }
        if (!Objects.equals(playerId, getActivePlayer())) {
            return false;
        }
        PlayerState state = getActivePlayerState();
        if (state == null || lastDrawnTile == null) {
            return false;
        }
        if (!HandValidator.isComplete(state.getHand())) {
            return false;
        }
        return hasYakuForWin(state, true, lastDrawnTile);
    }

    public boolean canDiscard(UUID playerId, Tile tile) {
        if (state != GameState.TURN_DISCARD) {
            return false;
        }
        if (!Objects.equals(playerId, getActivePlayer())) {
            return false;
        }
        PlayerState state = getActivePlayerState();
        if (state == null || tile == null) {
            return false;
        }
        if (state.getHand().isRiichiDeclared() && lastDrawnTile != null && !lastDrawnTile.equals(tile)) {
            return false;
        }
        return state.getHand().getConcealed().contains(tile);
    }

    public boolean declareTsumo(UUID playerId) {
        if (!canTsumo(playerId)) {
            return false;
        }
        winner = playerId;
        tsumoWin = true;
        winningTile = lastDrawnTile;
        state = GameState.HAND_END;
        return true;
    }

    public boolean discard(UUID playerId, Tile tile) {
        if (state != GameState.TURN_DISCARD) {
            return false;
        }
        if (!Objects.equals(playerId, getActivePlayer())) {
            return false;
        }
        PlayerState state = getActivePlayerState();
        if (state.getHand().isRiichiDeclared() && lastDrawnTile != null && !lastDrawnTile.equals(tile)) {
            return false;
        }
        if (state.getHand().isRiichiDeclared()) {
            if (state.getHand().isRiichiPendingDiscard()) {
                state.getHand().setRiichiPendingDiscard(false);
            } else if (state.getHand().isIppatsuEligible()) {
                state.getHand().setIppatsuEligible(false);
            }
        }
        if (!state.getHand().removeTile(tile)) {
            return false;
        }
        state.addDiscard(tile);
        lastDiscard = tile;
        lastDiscarder = playerId;
        lastDrawnTile = null;
        this.state = GameState.CALL_WINDOW;
        return true;
    }

    public void addCallRequest(CallRequest request) {
        if (state != GameState.CALL_WINDOW) {
            return;
        }
        pendingCalls.add(request);
    }

    public CallRequest resolveCalls() {
        if (state != GameState.CALL_WINDOW) {
            return null;
        }
        CallRequest best = null;
        int bestPriority = -1;
        for (CallRequest request : pendingCalls) {
            if (request.getType() == CallType.RON && !canRon(request.getPlayerId())) {
                continue;
            }
            int priority = callPriority(request.getType());
            if (priority > bestPriority) {
                bestPriority = priority;
                best = request;
            }
        }
        if (best == null) {
            markFuritenForMissedRon();
            pendingCalls.clear();
            resolveNoCall();
            return null;
        }
        if (best.getType() == CallType.RON) {
            PlayerState winnerState = players.get(best.getPlayerId());
            if (winnerState != null && lastDiscard != null) {
                winnerState.getHand().addTile(lastDiscard);
            }
            winner = best.getPlayerId();
            tsumoWin = false;
            winningTile = lastDiscard;
            state = GameState.HAND_END;
            pendingCalls.clear();
            return best;
        }
        markFuritenForMissedRon();
        pendingCalls.clear();
        if (!applyCall(best)) {
            resolveNoCall();
            return null;
        }
        return best;
    }

    public boolean canRon(UUID playerId) {
        if (state != GameState.CALL_WINDOW) {
            return false;
        }
        if (lastDiscard == null || Objects.equals(playerId, lastDiscarder)) {
            return false;
        }
        PlayerState state = players.get(playerId);
        if (state == null) {
            return false;
        }
        if (state.getHand().isFuriten()) {
            return false;
        }
        if (!HandValidator.isCompleteWith(state.getHand(), lastDiscard)) {
            return false;
        }
        return hasYakuForWin(state, false, lastDiscard);
    }

    public boolean canDeclareRiichi(UUID playerId) {
        if (state != GameState.TURN_DISCARD) {
            return false;
        }
        if (!Objects.equals(playerId, getActivePlayer())) {
            return false;
        }
        PlayerState state = players.get(playerId);
        if (state == null) {
            return false;
        }
        Hand hand = state.getHand();
        if (hand.isRiichiDeclared() || !hand.isClosed()) {
            return false;
        }
        if (state.getPoints() < 1000) {
            return false;
        }
        return isRiichiTenpai(hand);
    }

    public boolean canDeclareKan(UUID playerId) {
        if (state != GameState.TURN_DISCARD) {
            return false;
        }
        if (!Objects.equals(playerId, getActivePlayer())) {
            return false;
        }
        return !getSelfKanOptions(playerId).isEmpty();
    }

    public boolean declareRiichi(UUID playerId) {
        if (!canDeclareRiichi(playerId)) {
            return false;
        }
        PlayerState state = players.get(playerId);
        Hand hand = state.getHand();
        hand.setRiichiDeclared(true);
        hand.setRiichiPendingDiscard(true);
        hand.setIppatsuEligible(true);
        roundState.addRiichiPot(1);
        state.addPoints(-1000);
        return true;
    }

    public boolean declareKan(UUID playerId, int optionIndex) {
        if (state != GameState.TURN_DISCARD) {
            return false;
        }
        if (!Objects.equals(playerId, getActivePlayer())) {
            return false;
        }
        PlayerState state = players.get(playerId);
        if (state == null) {
            return false;
        }
        Hand hand = state.getHand();
        if (hand.isRiichiPendingDiscard()) {
            return false;
        }
        List<KanOption> options = getSelfKanOptions(playerId);
        if (optionIndex < 1 || optionIndex > options.size()) {
            return false;
        }
        KanOption option = options.get(optionIndex - 1);
        if (!applySelfKan(state, option)) {
            return false;
        }
        if (hand.isRiichiDeclared()) {
            hand.setIppatsuEligible(false);
        }
        wall.revealNextDora();
        drawRinshanForActive();
        return true;
    }

    public List<KanOption> getSelfKanOptions(UUID playerId) {
        if (state != GameState.TURN_DISCARD) {
            return List.of();
        }
        if (!Objects.equals(playerId, getActivePlayer())) {
            return List.of();
        }
        PlayerState state = players.get(playerId);
        if (state == null) {
            return List.of();
        }
        Hand hand = state.getHand();
        if (hand.isRiichiPendingDiscard()) {
            return List.of();
        }
        List<KanOption> options = new ArrayList<>();
        Map<TileId, List<Tile>> concealedGroups = new HashMap<>();
        for (Tile tile : hand.getConcealed()) {
            TileId key = normalize(tile.getId());
            concealedGroups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(tile);
        }
        for (Map.Entry<TileId, List<Tile>> entry : concealedGroups.entrySet()) {
            if (entry.getValue().size() == 4) {
                options.add(new KanOption(MeldType.KAN_CLOSED, entry.getKey(), entry.getValue()));
            }
        }
        for (Meld meld : hand.getMelds()) {
            if (meld.getType() != MeldType.PON) {
                continue;
            }
            TileId base = normalize(meld.getTiles().get(0).getId());
            List<Tile> tiles = concealedGroups.get(base);
            if (tiles == null || tiles.isEmpty()) {
                continue;
            }
            options.add(new KanOption(MeldType.KAN_ADDED, base, List.of(tiles.get(0))));
        }
        options.sort((a, b) -> {
            int cmp = Integer.compare(tileSortKey(a.getTileId()), tileSortKey(b.getTileId()));
            if (cmp != 0) {
                return cmp;
            }
            return a.getType().name().compareTo(b.getType().name());
        });
        return options;
    }

    public Optional<CallRequest> createPonRequest(UUID playerId) {
        if (!isCallWindowActive() || lastDiscard == null) {
            return Optional.empty();
        }
        if (Objects.equals(playerId, lastDiscarder)) {
            return Optional.empty();
        }
        PlayerState state = players.get(playerId);
        if (state == null) {
            return Optional.empty();
        }
        if (state.getHand().isRiichiDeclared()) {
            return Optional.empty();
        }
        List<Tile> tiles = pickTiles(state, lastDiscard.getId(), 2);
        if (tiles.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CallRequest(playerId, CallType.PON, tiles));
    }

    public Optional<CallRequest> createKanRequest(UUID playerId) {
        if (!isCallWindowActive() || lastDiscard == null) {
            return Optional.empty();
        }
        if (Objects.equals(playerId, lastDiscarder)) {
            return Optional.empty();
        }
        PlayerState state = players.get(playerId);
        if (state == null) {
            return Optional.empty();
        }
        if (state.getHand().isRiichiDeclared()) {
            return Optional.empty();
        }
        List<Tile> tiles = pickTiles(state, lastDiscard.getId(), 3);
        if (tiles.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CallRequest(playerId, CallType.KAN, tiles));
    }

    public Optional<CallRequest> createChiRequest(UUID playerId) {
        return createChiRequest(playerId, 1);
    }

    public Optional<CallRequest> createChiRequest(UUID playerId, int optionIndex) {
        if (!isCallWindowActive() || lastDiscard == null) {
            return Optional.empty();
        }
        if (!Objects.equals(playerId, getNextPlayer())) {
            return Optional.empty();
        }
        TileId id = lastDiscard.getId();
        if (!TileSuit.isSuited(id.getSuit())) {
            return Optional.empty();
        }
        PlayerState state = players.get(playerId);
        if (state == null) {
            return Optional.empty();
        }
        if (state.getHand().isRiichiDeclared()) {
            return Optional.empty();
        }
        List<List<Tile>> options = chiOptions(state, id);
        if (options.isEmpty()) {
            return Optional.empty();
        }
        if (optionIndex < 1 || optionIndex > options.size()) {
            return Optional.empty();
        }
        List<Tile> tiles = options.get(optionIndex - 1);
        return Optional.of(new CallRequest(playerId, CallType.CHI, tiles));
    }

    public int getChiOptionCount(UUID playerId) {
        if (!isCallWindowActive() || lastDiscard == null) {
            return 0;
        }
        if (!Objects.equals(playerId, getNextPlayer())) {
            return 0;
        }
        TileId id = lastDiscard.getId();
        if (!TileSuit.isSuited(id.getSuit())) {
            return 0;
        }
        PlayerState state = players.get(playerId);
        if (state == null) {
            return 0;
        }
        return chiOptions(state, id).size();
    }

    public void resolveNoCall() {
        if (state != GameState.CALL_WINDOW) {
            return;
        }
        lastDiscard = null;
        lastDiscarder = null;
        advanceTurn();
    }

    private void dealInitialHands() {
        for (int i = 0; i < 13; i++) {
            for (UUID playerId : turnOrder) {
                Tile tile = wall.draw();
                if (tile == null) {
                    state = GameState.HAND_END;
                    return;
                }
                players.get(playerId).getHand().addTile(tile);
            }
        }
    }

    private void advanceTurn() {
        activeIndex = (activeIndex + 1) % turnOrder.size();
        drawForActive();
    }

    private int callPriority(CallType type) {
        switch (type) {
            case RON:
                return 3;
            case KAN:
                return 2;
            case PON:
                return 1;
            case CHI:
            default:
                return 0;
        }
    }

    private PlayerState getActivePlayerState() {
        return players.get(getActivePlayer());
    }

    private boolean applyCall(CallRequest request) {
        PlayerState caller = players.get(request.getPlayerId());
        if (caller == null || lastDiscard == null) {
            return false;
        }
        // SIMPLIFIED: any call cancels ippatsu for all riichi players.
        cancelIppatsuForAll();
        List<Tile> tiles = request.getTiles();
        if (tiles.isEmpty()) {
            return false;
        }
        for (Tile tile : tiles) {
            if (!caller.getHand().removeTile(tile)) {
                return false;
            }
        }
        List<Tile> meldTiles = new ArrayList<>(tiles);
        meldTiles.add(lastDiscard);
        MeldType meldType;
        switch (request.getType()) {
            case CHI:
                meldType = MeldType.CHI;
                break;
            case PON:
                meldType = MeldType.PON;
                break;
            case KAN:
                meldType = MeldType.KAN_OPEN;
                break;
            default:
                return false;
        }
        caller.getHand().addMeld(new Meld(meldType, meldTiles, lastDiscarder));
        int idx = turnOrder.indexOf(request.getPlayerId());
        if (idx >= 0) {
            activeIndex = idx;
        }
        lastDiscard = null;
        lastDiscarder = null;
        lastDrawnTile = null;
        if (request.getType() == CallType.KAN) {
            wall.revealNextDora();
            drawRinshanForActive();
            return true;
        }
        state = GameState.TURN_DISCARD;
        return true;
    }

    private boolean isRiichiTenpai(Hand hand) {
        List<Tile> tiles = hand.getConcealed();
        if (tiles.size() % 3 != 2) {
            return false;
        }
        for (int i = 0; i < tiles.size(); i++) {
            List<Tile> remaining = new ArrayList<>(tiles);
            remaining.remove(i);
            if (HandValidator.isTenpai(remaining)) {
                return true;
            }
        }
        return false;
    }

    private UUID getNextPlayer() {
        return turnOrder.get((activeIndex + 1) % turnOrder.size());
    }

    private boolean hasYakuForWin(PlayerState player, boolean tsumo, Tile winningTile) {
        boolean openTanyao = rules.isOpenTanyaoEnabled();
        boolean ippatsuEnabled = rules.isIppatsuEnabled();
        Hand hand = player.getHand();
        boolean added = false;
        if (winningTile != null && !hand.getConcealed().contains(winningTile)) {
            hand.addTile(winningTile);
            added = true;
        }
        // SIMPLIFIED: yaku check uses only the implemented yaku set.
        boolean result = ScoreCalculator.hasYaku(player, roundState, tsumo, winningTile, openTanyao, ippatsuEnabled);
        if (added) {
            hand.removeTile(winningTile);
        }
        return result;
    }

    private List<Tile> pickTiles(PlayerState state, TileId target, int count) {
        List<Tile> matches = new ArrayList<>();
        for (Tile tile : state.getHand().getConcealed()) {
            if (sameRank(tile.getId(), target)) {
                matches.add(tile);
                if (matches.size() == count) {
                    return matches;
                }
            }
        }
        return List.of();
    }

    private List<List<Tile>> chiOptions(PlayerState state, TileId discard) {
        List<List<Tile>> options = new ArrayList<>();
        int rank = discard.getRank();
        TileSuit suit = discard.getSuit();
        addChiOption(options, state, suit, rank - 2, rank - 1);
        addChiOption(options, state, suit, rank - 1, rank + 1);
        addChiOption(options, state, suit, rank + 1, rank + 2);
        return options;
    }

    private boolean applySelfKan(PlayerState state, KanOption option) {
        if (state == null || option == null) {
            return false;
        }
        Hand hand = state.getHand();
        if (option.getType() == MeldType.KAN_CLOSED) {
            for (Tile tile : option.getTiles()) {
                if (!hand.removeTile(tile)) {
                    return false;
                }
            }
            hand.addMeld(new Meld(MeldType.KAN_CLOSED, option.getTiles(), null));
            return true;
        }
        if (option.getType() == MeldType.KAN_ADDED) {
            List<Tile> tiles = option.getTiles();
            if (tiles.isEmpty()) {
                return false;
            }
            Tile addedTile = tiles.get(0);
            if (!hand.removeTile(addedTile)) {
                return false;
            }
            Meld target = findPonMeld(hand, option.getTileId());
            if (target == null) {
                hand.addTile(addedTile);
                return false;
            }
            List<Tile> meldTiles = new ArrayList<>(target.getTiles());
            meldTiles.add(addedTile);
            Meld upgraded = new Meld(MeldType.KAN_ADDED, meldTiles, target.getCalledFrom());
            if (!hand.replaceMeld(target, upgraded)) {
                hand.addTile(addedTile);
                return false;
            }
            return true;
        }
        return false;
    }

    private Meld findPonMeld(Hand hand, TileId target) {
        for (Meld meld : hand.getMelds()) {
            if (meld.getType() != MeldType.PON) {
                continue;
            }
            TileId base = normalize(meld.getTiles().get(0).getId());
            if (sameRank(base, target)) {
                return meld;
            }
        }
        return null;
    }

    private TileId normalize(TileId id) {
        if (id.isRed()) {
            return TileId.of(id.getSuit(), id.getRank(), false);
        }
        return id;
    }

    private int tileSortKey(TileId id) {
        int suitBase;
        switch (id.getSuit()) {
            case MAN:
                suitBase = 0;
                break;
            case PIN:
                suitBase = 9;
                break;
            case SOU:
                suitBase = 18;
                break;
            case HONOR:
            default:
                suitBase = 27;
                break;
        }
        return suitBase + id.getRank();
    }

    private void addChiOption(List<List<Tile>> options, PlayerState state, TileSuit suit, int r1, int r2) {
        if (r1 < 1 || r2 > 9) {
            return;
        }
        Tile t1 = pickSingle(state, suit, r1);
        Tile t2 = pickSingle(state, suit, r2);
        if (t1 == null || t2 == null) {
            return;
        }
        List<Tile> tiles = new ArrayList<>();
        tiles.add(t1);
        tiles.add(t2);
        options.add(tiles);
    }

    private Tile pickSingle(PlayerState state, TileSuit suit, int rank) {
        for (Tile tile : state.getHand().getConcealed()) {
            TileId id = tile.getId();
            if (id.getSuit() == suit && id.getRank() == rank) {
                return tile;
            }
        }
        return null;
    }

    private boolean sameRank(TileId a, TileId b) {
        return a.getSuit() == b.getSuit() && a.getRank() == b.getRank();
    }

    private void cancelIppatsuForAll() {
        for (PlayerState state : players.values()) {
            if (state.getHand().isRiichiDeclared()) {
                state.getHand().setIppatsuEligible(false);
            }
        }
    }

    private void drawRinshanForActive() {
        if (state == GameState.HAND_END) {
            return;
        }
        state = GameState.TURN_DRAW;
        Tile tile = wall.drawRinshan();
        if (tile == null) {
            state = GameState.HAND_END;
            return;
        }
        PlayerState active = getActivePlayerState();
        active.getHand().setFuriten(false);
        active.getHand().addTile(tile);
        lastDrawnTile = tile;
        lastDrawnPlayer = getActivePlayer();
        lastDrawRinshan = true;
        drawSequence++;
        state = GameState.TURN_DISCARD;
    }

    private void markFuritenForMissedRon() {
        for (UUID playerId : turnOrder) {
            if (Objects.equals(playerId, lastDiscarder)) {
                continue;
            }
            if (hasRonDeclared(playerId)) {
                continue;
            }
            if (canRon(playerId)) {
                PlayerState state = players.get(playerId);
                if (state != null) {
                    state.getHand().setFuriten(true);
                }
            }
        }
    }

    private boolean hasRonDeclared(UUID playerId) {
        for (CallRequest request : pendingCalls) {
            if (request.getType() == CallType.RON && request.getPlayerId().equals(playerId)) {
                return true;
            }
        }
        return false;
    }
}
