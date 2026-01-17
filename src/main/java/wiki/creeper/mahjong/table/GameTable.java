package wiki.creeper.mahjong.table;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.EnumMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import wiki.creeper.mahjong.api.event.MahjongCallEvent;
import wiki.creeper.mahjong.api.event.MahjongDiscardEvent;
import wiki.creeper.mahjong.api.event.MahjongGameStartEvent;
import wiki.creeper.mahjong.api.event.MahjongHandEndEvent;
import wiki.creeper.mahjong.api.event.MahjongHandStartEvent;
import wiki.creeper.mahjong.api.event.MahjongRiichiEvent;
import wiki.creeper.mahjong.api.event.MahjongWinEvent;
import wiki.creeper.mahjong.ai.BotAction;
import wiki.creeper.mahjong.ai.BotController;
import wiki.creeper.mahjong.ai.BotDecision;
import wiki.creeper.mahjong.ai.BotDifficulty;
import wiki.creeper.mahjong.ai.BotProfile;
import wiki.creeper.mahjong.ai.CoachAdvice;
import wiki.creeper.mahjong.ai.CoachAdvisor;
import wiki.creeper.mahjong.ai.CoachSuggestion;
import wiki.creeper.mahjong.ai.PrivateState;
import wiki.creeper.mahjong.ai.PublicState;
import wiki.creeper.mahjong.ai.ShantenCalculator;
import wiki.creeper.mahjong.ai.TileCounter;
import wiki.creeper.mahjong.ai.TurnContext;
import wiki.creeper.mahjong.game.GameEngine;
import wiki.creeper.mahjong.game.GameState;
import wiki.creeper.mahjong.game.GameRules;
import wiki.creeper.mahjong.game.PlayerState;
import wiki.creeper.mahjong.game.Tile;
import wiki.creeper.mahjong.game.TileId;
import wiki.creeper.mahjong.game.KanOption;
import wiki.creeper.mahjong.game.CallRequest;
import wiki.creeper.mahjong.game.CallType;
import wiki.creeper.mahjong.game.SeatWind;
import wiki.creeper.mahjong.game.RoundState;
import wiki.creeper.mahjong.game.MeldType;
import wiki.creeper.mahjong.game.ScoreCalculator;
import wiki.creeper.mahjong.game.ScoreResult;
import wiki.creeper.mahjong.ui.HandInventory;
import wiki.creeper.mahjong.ui.RoomInventory;
import wiki.creeper.mahjong.ui.RoomMenuType;
import wiki.creeper.mahjong.ui.UiManager;
import wiki.creeper.mahjong.ui.WorldUiManager;
import wiki.creeper.mahjong.storage.GameEvent;
import wiki.creeper.mahjong.storage.GameEventLogger;
import wiki.creeper.mahjong.storage.GameEventType;

public class GameTable {

    static final int MAX_PLAYERS = 4;
    private static final int COACH_OVERLAY_SECONDS = 6;

    private final UUID id = UUID.randomUUID();
    private final JavaPlugin plugin;
    private final GameQueue queue = new GameQueue();
    private final List<UUID> players = new ArrayList<>(MAX_PLAYERS);
    private final Set<UUID> spectators = new HashSet<>();
    private final Map<UUID, HandInventory> handInventories = new HashMap<>();
    private final UiManager uiManager;
    private final WorldUiManager worldUi;
    private final GameTableDialogs dialogs;
    private final GameTableRoomUi roomUi;
    private final GameEventLogger eventLogger = new GameEventLogger();
    private final Set<UUID> furitenNotified = new HashSet<>();
    private final Set<UUID> readyPlayers = new HashSet<>();
    private final Map<UUID, BotProfile> bots = new HashMap<>();
    private final Map<UUID, BukkitTask> botTurnTasks = new HashMap<>();
    private final Map<UUID, String> displayNameCache = new HashMap<>();
    private final Set<UUID> coachPlayers = new HashSet<>();
    private final Map<UUID, CoachAdvice> coachAdviceCache = new HashMap<>();
    private final Map<UUID, BossBar> coachBars = new HashMap<>();
    private final Map<UUID, BukkitTask> coachBarTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> replayTasks = new HashMap<>();
    private final Map<SeatWind, UUID> seatAssignments = new EnumMap<>(SeatWind.class);
    private final Map<UUID, SeatWind> playerSeats = new HashMap<>();
    private Map<UUID, PlayerState> playerStates;
    private RoundState roundState;
    private GameRules rules;
    private GameState state = GameState.LOBBY;
    private GameEngine engine;
    private String roomCode;
    private UUID hostId;
    private boolean roomMode;
    private BukkitTask callTask;
    private BossBar callBossBar;
    private BukkitTask callBossBarTask;
    private BossBar turnBossBar;
    private BukkitTask turnBossBarTask;
    private BukkitTask nextHandTask;
    private UUID turnTimerPlayer;
    private BukkitTask botCallTask;
    private int lastLoggedDrawSequence;
    private int turnSecondsRemaining = -1;
    private int turnByoyomiRemaining = -1;
    private final Map<UUID, Integer> turnExtraSeconds = new HashMap<>();
    private int callSecondsRemaining = -1;
    private boolean botsEnabled;
    private boolean coachEnabled;
    private boolean coachRankDisabled;
    private int botDelayTicks;
    private int botSequence;
    private String lastAction = "NONE";

    public GameTable(JavaPlugin plugin) {
        this.plugin = plugin;
        this.uiManager = new UiManager(plugin);
        this.worldUi = new WorldUiManager(plugin, id);
        this.dialogs = new GameTableDialogs(this);
        this.roomUi = new GameTableRoomUi(this);
        loadRoomOptions();
    }

    public UUID getId() {
        return id;
    }

    public GameState getState() {
        if (engine != null) {
            return engine.getState();
        }
        return state;
    }

    public GameEngine getEngine() {
        return engine;
    }

    JavaPlugin getPlugin() {
        return plugin;
    }

    WorldUiManager getWorldUi() {
        return worldUi;
    }

    GameTableDialogs getDialogs() {
        return dialogs;
    }

    Map<SeatWind, UUID> getSeatAssignments() {
        return Collections.unmodifiableMap(seatAssignments);
    }

    Map<UUID, SeatWind> getPlayerSeats() {
        return Collections.unmodifiableMap(playerSeats);
    }

    Set<UUID> getReadyPlayers() {
        return Collections.unmodifiableSet(readyPlayers);
    }

    Set<UUID> getCoachPlayers() {
        return Collections.unmodifiableSet(coachPlayers);
    }

    Map<UUID, BotProfile> getBots() {
        return Collections.unmodifiableMap(bots);
    }

    public int getTurnSecondsRemaining() {
        return turnSecondsRemaining;
    }

    public String getDisplayName(UUID playerId) {
        return resolveName(playerId);
    }

    public String getStatusLine() {
        StringBuilder sb = new StringBuilder();
        sb.append("코치: 추천 버림패 ");
        if (engine != null) {
            sb.append(" active=").append(resolveName(engine.getActivePlayer()));
            sb.append(" remaining=").append(engine.getRoundState().getRemainingTiles());
            sb.append(" riichiPot=").append(engine.getRoundState().getRiichiPot());
            sb.append(" points=[");
            boolean first = true;
            for (UUID playerId : players) {
                PlayerState state = engine.getPlayerState(playerId);
                if (state == null) {
                    continue;
                }
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(resolveName(playerId)).append(":").append(state.getPoints());
            }
            sb.append("]");
        }
        return sb.toString();
    }

    public List<UUID> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public Set<UUID> getSpectators() {
        return Collections.unmodifiableSet(spectators);
    }

    public boolean isRoomMode() {
        return roomMode;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public UUID getHostId() {
        return hostId;
    }

    public boolean isHost(UUID playerId) {
        return playerId != null && playerId.equals(hostId);
    }

    public int getReadyCount() {
        return readyPlayers.size() + bots.size();
    }

    public boolean isReady(UUID playerId) {
        return isBot(playerId) || readyPlayers.contains(playerId);
    }

    public boolean areAllReady() {
        if (players.isEmpty() || !areAllSeatsFilled()) {
            return false;
        }
        int readyCount = 0;
        for (UUID playerId : players) {
            if (isBot(playerId) || readyPlayers.contains(playerId)) {
                readyCount++;
            }
        }
        return readyCount == players.size();
    }

    public boolean areAllSeatsFilled() {
        return seatAssignments.size() == MAX_PLAYERS;
    }

    public boolean areBotsEnabled() {
        return botsEnabled;
    }

    public boolean isCoachEnabled() {
        return coachEnabled;
    }

    public boolean isCoachRankDisabled() {
        return coachRankDisabled;
    }

    public boolean isBot(UUID playerId) {
        return playerId != null && bots.containsKey(playerId);
    }

    public GameRules getRulesSnapshot() {
        if (rules != null) {
            return rules;
        }
        return loadRules();
    }

    public List<String> getRoomStatusLines() {
        return roomUi.getRoomStatusLines();
    }

    public void openRoomLobbyGui(Player player) {
        roomUi.openRoomLobbyGui(player);
    }

    public void openRoomRulesGui(Player player) {
        roomUi.openRoomRulesGui(player);
    }

    public void openRoomBotsGui(Player player) {
        roomUi.openRoomBotsGui(player);
    }

    public void showRoomRules(Player player) {
        roomUi.showRoomRules(player);
    }

    public void showRoomBots(Player player) {
        roomUi.showRoomBots(player);
    }

    public void showRoomLobby(Player player) {
        roomUi.showRoomLobby(player);
    }

    public void enableRoom(Player host, String code) {
        roomMode = true;
        roomCode = code;
        hostId = host.getUniqueId();
        if (rules == null) {
            rules = loadRules();
        }
        loadRoomOptions();
        seatAssignments.clear();
        playerSeats.clear();
        assignSeatInternal(host.getUniqueId(), SeatWind.EAST, true);
        updateRoomLobbyUi();
    }

    public boolean toggleReady(Player player) {
        if (!roomMode || getState() != GameState.LOBBY || player == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (!players.contains(playerId)) {
            return false;
        }
        if (!playerSeats.containsKey(playerId)) {
            player.sendMessage("준비하기 전에 좌석을 선택해 주세요.");
            return false;
        }
        boolean ready = readyPlayers.contains(playerId);
        if (ready) {
            readyPlayers.remove(playerId);
        } else {
            readyPlayers.add(playerId);
        }
        String state = ready ? "준비를 해제했어요" : "준비를 완료했어요";
        broadcast(resolveName(playerId) + " 님이 " + state + " (" + readyPlayers.size() + "/" + players.size() + ").");
        updateRoomLobbyUi();
        return true;
    }

    public boolean requestSeat(Player player, SeatWind seat) {
        return assignSeat(player, seat);
    }

    public boolean applyPreset(String presetKey) {
        if (!roomMode || getState() != GameState.LOBBY || presetKey == null) {
            return false;
        }
        String key = presetKey.toLowerCase(Locale.ROOT);
        GameRules base = loadRules();
        GameRules updated;
        switch (key) {
            case "default":
                updated = base;
                break;
            case "kuitan":
                updated = new GameRules(base.isRedDoraEnabled(), true, base.isIppatsuEnabled(), base.isUraDoraEnabled());
                break;
            case "classic":
                updated = new GameRules(false, false, false, false);
                break;
            default:
                return false;
        }
        rules = updated;
        broadcastRoomRules();
        return true;
    }

    public boolean updateRule(String ruleKey, Boolean value) {
        if (!roomMode || getState() != GameState.LOBBY || ruleKey == null) {
            return false;
        }
        GameRules current = getRulesSnapshot();
        boolean red = current.isRedDoraEnabled();
        boolean open = current.isOpenTanyaoEnabled();
        boolean ippatsu = current.isIppatsuEnabled();
        boolean ura = current.isUraDoraEnabled();
        boolean botsFlag = botsEnabled;
        boolean coachFlag = coachEnabled;
        boolean coachRankFlag = coachRankDisabled;
        boolean coachWasAllowed = isCoachAllowed();
        String key = ruleKey.toLowerCase(Locale.ROOT);
        switch (key) {
            case "reddora":
            case "red":
            case "aka":
                red = value != null ? value : !red;
                break;
            case "opentanyao":
            case "open":
            case "kuitan":
                open = value != null ? value : !open;
                break;
            case "ippatsu":
            case "일발":
                ippatsu = value != null ? value : !ippatsu;
                break;
            case "uradora":
            case "ura":
                ura = value != null ? value : !ura;
                break;
            case "bots":
            case "bot":
            case "봇":
                botsFlag = value != null ? value : !botsFlag;
                break;
            case "coach":
                coachFlag = value != null ? value : !coachFlag;
                break;
            case "coachrank":
            case "coachrankdisabled":
            case "coachranklock":
                coachRankFlag = value != null ? value : !coachRankFlag;
                break;
            default:
                return false;
        }
        rules = new GameRules(red, open, ippatsu, ura);
        botsEnabled = botsFlag;
        coachEnabled = coachFlag;
        coachRankDisabled = coachRankFlag;
        if (!botsEnabled) {
            removeAllBots();
        }
        if (coachWasAllowed && !isCoachAllowed()) {
            disableCoachForAll("이 테이블에서는 코치가 비활성화되어 있어요.");
        }
        broadcastRoomRules();
        return true;
    }

    public boolean addPlayer(Player player) {
        if (getState() != GameState.LOBBY || players.size() >= MAX_PLAYERS) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (players.contains(playerId)) {
            return false;
        }
        spectators.remove(playerId);
        boolean added = players.add(playerId);
        if (added) {
            displayNameCache.put(playerId, player.getName());
            turnExtraSeconds.putIfAbsent(playerId, loadTurnExtraSeconds());
            readyPlayers.remove(playerId);
            if (worldUi.isSpawned()) {
                worldUi.hideHandDisplaysFor(player);
            }
            if (roomMode) {
                broadcast(resolveName(playerId) + " 님이 테이블에 입장했어요 (" + players.size() + "/" + MAX_PLAYERS + ").");
                autoAssignSeat(playerId);
                updateRoomLobbyUi();
            }
        }
        return added;
    }

    public boolean addSpectator(Player player) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (players.contains(playerId)) {
            return false;
        }
        if (!spectators.add(playerId)) {
            return false;
        }
        displayNameCache.put(playerId, player.getName());
        if (worldUi.isSpawned()) {
            worldUi.hideHandDisplaysFor(player);
        }
        return true;
    }

    public boolean removePlayer(Player player) {
        UUID playerId = player.getUniqueId();
        if (!players.contains(playerId)) {
            return false;
        }
        if (state != GameState.LOBBY && engine != null && !isBot(playerId)) {
            return replaceWithBot(player);
        }
        boolean removed = players.remove(playerId);
        readyPlayers.remove(playerId);
        turnExtraSeconds.remove(playerId);
        if (worldUi.isSpawned()) {
            worldUi.clearHandDisplay(playerId);
        }
        displayNameCache.remove(playerId);
        coachPlayers.remove(playerId);
        coachAdviceCache.remove(playerId);
        clearCoachOverlay(playerId);
        cancelReplay(playerId);
        if (playerId.equals(turnTimerPlayer)) {
            cancelTurnTimer();
        }
        if (roomMode) {
            broadcast(resolveName(playerId) + " 님이 테이블에서 나갔어요 (" + players.size() + "/" + MAX_PLAYERS + ").");
            clearSeat(playerId, true);
            if (playerId.equals(hostId)) {
                hostId = resolveNextHost();
                if (hostId != null) {
                    broadcast("새 호스트: " + resolveName(hostId));
                    if (!playerSeats.containsKey(hostId)) {
                        autoAssignSeat(hostId);
                    }
                }
            }
            updateRoomLobbyUi();
        }
        return true;
    }

    public boolean removeSpectator(Player player) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        boolean removed = spectators.remove(playerId);
        if (removed) {
            displayNameCache.remove(playerId);
        }
        return removed;
    }

    private boolean replaceWithBot(Player player) {
        UUID playerId = player.getUniqueId();
        String name = resolveName(playerId);
        String botName = name + " (봇)";
        if (!isBot(playerId)) {
            long seed = ThreadLocalRandom.current().nextLong();
            BotProfile profile = new BotProfile(playerId, BotDifficulty.NORMAL, seed, botName);
            bots.put(playerId, profile);
        }
        spectators.remove(playerId);
        displayNameCache.put(playerId, botName);
        readyPlayers.remove(playerId);
        turnExtraSeconds.remove(playerId);
        handInventories.remove(playerId);
        coachPlayers.remove(playerId);
        coachAdviceCache.remove(playerId);
        clearCoachOverlay(playerId);
        cancelReplay(playerId);
        if (callBossBar != null) {
            callBossBar.removePlayer(player);
        }
        if (worldUi.isSpawned()) {
            worldUi.clearHandDisplay(playerId);
        }
        dialogs.closeDialog(player);
        if (player.isOnline()) {
            player.closeInventory();
        }
        if (playerId.equals(turnTimerPlayer)) {
            cancelTurnTimer();
        }
        if (engine != null && playerId.equals(engine.getActivePlayer())) {
            scheduleBotTurn(playerId);
        }
        broadcast(name + " 님이 나가서 봇이 대신 플레이해요.");
        updateWorldUi();
        return true;
    }

    public boolean addBot(BotDifficulty difficulty) {
        if (!roomMode || getState() != GameState.LOBBY || difficulty == null) {
            return false;
        }
        if (!botsEnabled || players.size() >= MAX_PLAYERS) {
            return false;
        }
        UUID botId = UUID.randomUUID();
        long seed = ThreadLocalRandom.current().nextLong();
        BotProfile profile = new BotProfile(botId, difficulty, seed, buildBotName(difficulty));
        bots.put(botId, profile);
        displayNameCache.put(botId, profile.getName());
        players.add(botId);
        if (roomMode) {
            broadcast(resolveName(botId) + " 님이 테이블에 입장했어요 (" + players.size() + "/" + MAX_PLAYERS + ").");
            autoAssignSeat(botId);
            updateRoomLobbyUi();
        }
        return true;
    }

    public boolean removeBot(BotDifficulty difficulty) {
        if (!roomMode || getState() != GameState.LOBBY) {
            return false;
        }
        UUID target = null;
        for (UUID playerId : players) {
            BotProfile profile = bots.get(playerId);
            if (profile == null) {
                continue;
            }
            if (difficulty == null || profile.getDifficulty() == difficulty) {
                target = playerId;
                break;
            }
        }
        if (target == null) {
            return false;
        }
        removeBotInternal(target, true);
        return true;
    }

    private int removeAllBots() {
        if (bots.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (UUID botId : new ArrayList<>(bots.keySet())) {
            removeBotInternal(botId, false);
            removed++;
        }
        if (removed > 0) {
            updateRoomLobbyUi();
        }
        return removed;
    }

    private void removeBotInternal(UUID botId, boolean updateUi) {
        if (botId == null) {
            return;
        }
        String name = resolveName(botId);
        players.remove(botId);
        bots.remove(botId);
        displayNameCache.remove(botId);
        readyPlayers.remove(botId);
        if (worldUi.isSpawned()) {
            worldUi.clearHandDisplay(botId);
        }
        coachPlayers.remove(botId);
        coachAdviceCache.remove(botId);
        clearCoachOverlay(botId);
        cancelBotTurn(botId);
        clearSeat(botId, false);
        if (botId.equals(hostId)) {
            hostId = resolveNextHost();
        }
        if (roomMode) {
            broadcast(name + " 님이 테이블에서 나갔어요 (" + players.size() + "/" + MAX_PLAYERS + ").");
        }
        if (updateUi) {
            updateRoomLobbyUi();
        }
    }

    private String buildBotName(BotDifficulty difficulty) {
        botSequence++;
        return "Bot-" + difficulty.name() + "-" + botSequence;
    }

    boolean hasBotDifficulty(BotDifficulty difficulty) {
        if (difficulty == null) {
            return false;
        }
        for (BotProfile profile : bots.values()) {
            if (profile.getDifficulty() == difficulty) {
                return true;
            }
        }
        return false;
    }

    public boolean setCoach(Player player, boolean enabled) {
        if (player == null) {
            return false;
        }
        if (!roomMode) {
            player.sendMessage("코치는 테이블에서만 사용할 수 있어요.");
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (!players.contains(playerId)) {
            player.sendMessage("이 테이블에 참여 중이 아니에요.");
            return false;
        }
        if (isBot(playerId)) {
            player.sendMessage("봇은 코치를 사용할 수 없어요.");
            return false;
        }
        if (!isCoachAllowed()) {
            if (coachRankDisabled) {
                player.sendMessage("랭크 제한에서는 코치를 사용할 수 없어요.");
            } else {
                player.sendMessage("이 테이블에서는 코치가 비활성화되어 있어요.");
            }
            return false;
        }
        if (enabled) {
            coachPlayers.add(playerId);
        } else {
            coachPlayers.remove(playerId);
            coachAdviceCache.remove(playerId);
            clearCoachOverlay(playerId);
        }
        player.sendMessage(enabled ? "코치를 켰어요." : "코치를 껐어요.");
        if (enabled && engine != null && playerId.equals(engine.getActivePlayer())) {
            sendCoachAdvice(playerId);
        }
        return true;
    }

    public boolean isEmpty() {
        for (UUID playerId : players) {
            if (!isBot(playerId)) {
                return false;
            }
        }
        return true;
    }

    public boolean start() {
        if (getState() != GameState.LOBBY || players.size() < MAX_PLAYERS) {
            return false;
        }
        if (roomMode && !areAllReady()) {
            return false;
        }
        if (roomMode && areAllSeatsFilled()) {
            List<UUID> seatOrder = resolveSeatOrder();
            if (seatOrder.size() == MAX_PLAYERS) {
                players.clear();
                players.addAll(seatOrder);
            }
        }
        int startingPoints = plugin.getConfig().getInt("round.startingPoints", 25000);
        if (rules == null) {
            rules = loadRules();
        }
        eventLogger.clear();
        lastLoggedDrawSequence = 0;
        cancelAllReplays();
        cancelAllBotTurns();
        cancelBotCalls();
        coachAdviceCache.clear();
        long seed = System.currentTimeMillis();
        this.engine = new GameEngine(players, startingPoints, rules, seed);
        this.engine.startRound();
        resetTurnExtraSeconds();
        logGameStart(seed);
        logHandStart();
        callEvent(new MahjongGameStartEvent(this, seed, rules, engine.getRoundState()));
        callEvent(new MahjongHandStartEvent(this, engine.getRoundState()));
        cacheState();
        state = engine.getState();
        clearRoomBossBar();
        readyPlayers.clear();
        ensureWorldUi();
        worldUi.clearHandResult();
        openHands();
        broadcastDoraIndicators();
        broadcastRoundStatus();
        updateWorldUi();
        if (engine.getState() == GameState.HAND_END) {
            endHand();
        } else {
            announceTurn(engine.getActivePlayer());
        }
        return true;
    }

    public boolean startNextHand() {
        cancelAutoNextHand();
        if (engine == null || engine.getState() != GameState.HAND_END) {
            return false;
        }
        if (players.size() < MAX_PLAYERS) {
            return false;
        }
        if (playerStates == null || roundState == null) {
            cacheState();
        }
        if (isGameOver()) {
            return false;
        }
        for (PlayerState state : playerStates.values()) {
            state.resetForNewHand();
        }
        if (rules == null) {
            rules = loadRules();
        }
        long seed = System.currentTimeMillis();
        this.engine = new GameEngine(players, playerStates, roundState, rules, seed);
        this.engine.startRound();
        resetTurnExtraSeconds();
        lastLoggedDrawSequence = 0;
        cancelAllReplays();
        cancelAllBotTurns();
        cancelBotCalls();
        coachAdviceCache.clear();
        logHandStart();
        callEvent(new MahjongHandStartEvent(this, engine.getRoundState()));
        ensureWorldUi();
        worldUi.clearHandResult();
        openHands();
        broadcastDoraIndicators();
        broadcastRoundStatus();
        updateWorldUi();
        if (engine.getState() == GameState.HAND_END) {
            endHand();
        } else {
            announceTurn(engine.getActivePlayer());
        }
        return true;
    }

    private void requestNextHandInternal(Player player) {
        if (engine == null || engine.getState() != GameState.HAND_END) {
            return;
        }
        if (players.size() < MAX_PLAYERS) {
            if (player != null) {
                player.sendMessage("다음 핸드를 시작할 수 없어요. 4명이 필요해요.");
            }
            return;
        }
        if (isGameOver()) {
            if (player != null) {
                player.sendMessage("게임이 종료되었어요. 로비에서 새 게임을 시작해 주세요.");
            }
            return;
        }
        if (startNextHand()) {
            broadcast("다음 핸드를 시작했어요.");
        } else if (player != null) {
            player.sendMessage("다음 핸드 시작에 실패했어요.");
        }
    }

    public GameQueue getQueue() {
        return queue;
    }

    public void openHand(Player player) {
        queue.enqueue(() -> openHandInternal(player));
    }

    public void requestDiscard(Player player, ItemStack item) {
        queue.enqueue(() -> requestDiscardInternal(player, item));
    }

    public void requestDiscardByInstance(Player player, int instanceId) {
        queue.enqueue(() -> requestDiscardInternal(player, instanceId));
    }

    public void handleDiscard(Player player, ItemStack item) {
        queue.enqueue(() -> handleDiscardInternal(player, item));
    }

    public void requestRon(Player player) {
        queue.enqueue(() -> requestRonInternal(player));
    }

    public void requestPon(Player player) {
        queue.enqueue(() -> requestPonInternal(player));
    }

    public void requestChi(Player player) {
        requestChi(player, 1);
    }

    public void requestChi(Player player, int optionIndex) {
        queue.enqueue(() -> requestChiInternal(player, optionIndex));
    }

    public void requestChiSelection(Player player) {
        queue.enqueue(() -> requestChiSelectionInternal(player));
    }

    public void requestKan(Player player) {
        queue.enqueue(() -> requestKanInternal(player, 0));
    }

    public void requestKan(Player player, int optionIndex) {
        queue.enqueue(() -> requestKanInternal(player, optionIndex));
    }

    public void requestRiichi(Player player) {
        queue.enqueue(() -> requestRiichiInternal(player));
    }

    public void requestTsumo(Player player) {
        queue.enqueue(() -> requestTsumoInternal(player));
    }

    public void requestStart(Player player) {
        queue.enqueue(() -> requestStartInternal(player));
    }

    public void requestNextHand(Player player) {
        queue.enqueue(() -> requestNextHandInternal(player));
    }

    public Optional<Path> exportEventLog() {
        Path dir = plugin.getDataFolder().toPath().resolve("replays");
        String name = "mahjong-" + id + "-" + System.currentTimeMillis() + ".log";
        Path file = dir.resolve(name);
        try {
            eventLogger.exportTo(file);
            return Optional.of(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to export replay: " + e.getMessage());
            return Optional.empty();
        }
    }

    public void replayEvents(Player player, int intervalTicks) {
        if (player == null) {
            return;
        }
        if (eventLogger.isEmpty()) {
            player.sendMessage("기록된 이벤트가 없어요.");
            return;
        }
        UUID playerId = player.getUniqueId();
        cancelReplay(playerId);
        int ticks = Math.max(1, intervalTicks);
        List<GameEvent> events = eventLogger.getEvents();
        AtomicInteger index = new AtomicInteger(0);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancelReplay(playerId);
                return;
            }
            int i = index.getAndIncrement();
            if (i >= events.size()) {
                player.sendMessage("리플레이가 종료됐어요.");
                cancelReplay(playerId);
                return;
            }
            player.sendMessage("[리플레이] " + formatEventLine(events.get(i)));
        }, 0L, ticks);
        replayTasks.put(playerId, task);
    }

    private String formatEventLine(GameEvent event) {
        if (event == null) {
            return "";
        }
        String actor = event.getPlayerId() != null ? resolveName(event.getPlayerId()) : "-";
        String payload = event.getPayload();
        if (payload == null || payload.isEmpty()) {
            return event.getType() + " " + actor;
        }
        return event.getType() + " " + actor + " " + payload;
    }

    public void shutdown() {
        queue.clear();
        cancelAutoNextHand();
        if (callTask != null) {
            callTask.cancel();
            callTask = null;
        }
        clearCallBossBar();
        cancelTurnTimer();
        dialogs.clearCallDialogs();
        clearRoomBossBar();
        cancelAllReplays();
        cancelAllBotTurns();
        cancelBotCalls();
        coachAdviceCache.clear();
        clearCoachOverlays();
        worldUi.remove();
        spectators.clear();
    }

    private void resetToLobby() {
        engine = null;
        cancelAutoNextHand();
        state = GameState.LOBBY;
        playerStates = null;
        roundState = null;
        furitenNotified.clear();
        dialogs.clearCallDialogs();
        if (worldUi.isSpawned()) {
            worldUi.clearAllHandDisplays();
        }
        cancelAllBotTurns();
        cancelBotCalls();
        cancelTurnTimer();
        coachAdviceCache.clear();
        clearCoachOverlays();
        if (roomMode) {
            readyPlayers.clear();
        }
        updateRoomLobbyUi();
        worldUi.clearHandResult();
        updateWorldUi();
    }

    private void cancelReplay(UUID playerId) {
        if (playerId == null) {
            return;
        }
        BukkitTask task = replayTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void cancelAllReplays() {
        for (BukkitTask task : replayTasks.values()) {
            task.cancel();
        }
        replayTasks.clear();
    }

    private void cancelBotTurn(UUID playerId) {
        if (playerId == null) {
            return;
        }
        BukkitTask task = botTurnTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void cancelAllBotTurns() {
        for (BukkitTask task : botTurnTasks.values()) {
            task.cancel();
        }
        botTurnTasks.clear();
    }

    private void cancelBotCalls() {
        if (botCallTask != null) {
            botCallTask.cancel();
            botCallTask = null;
        }
    }

    private void openHands() {
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            updateHand(playerId, true);
        }
    }

    private void openHandInternal(Player player) {
        if (engine == null) {
            player.sendMessage("게임이 아직 시작되지 않았어요.");
            return;
        }
        updateHand(player.getUniqueId(), true);
    }

    private void requestDiscardInternal(Player player, ItemStack item) {
        if (engine == null) {
            player.sendMessage("게임이 아직 시작되지 않았어요.");
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!playerId.equals(engine.getActivePlayer())) {
            player.sendMessage("지금은 내 차례가 아니에요.");
            return;
        }
        PlayerState state = engine.getPlayerState(playerId);
        if (state == null) {
            player.sendMessage("플레이어 상태를 찾을 수 없어요.");
            return;
        }
        Optional<Tile> tile = uiManager.readTile(item, state);
        if (tile.isEmpty()) {
            player.sendMessage("선택한 패가 유효하지 않아요.");
            return;
        }
        if (!engine.canDiscard(playerId, tile.get())) {
            player.sendMessage("해당 패는 버릴 수 없어요.");
            return;
        }
        dialogs.showDiscardPreview(player, tile.get());
        if (!dialogs.shouldConfirmDiscard()) {
            handleDiscardInternal(player, item);
            return;
        }
        dialogs.showDiscardConfirmDialog(player, tile.get(), item);
    }

    private void requestDiscardInternal(Player player, int instanceId) {
        if (engine == null) {
            player.sendMessage("게임이 아직 시작되지 않았어요.");
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!playerId.equals(engine.getActivePlayer())) {
            player.sendMessage("지금은 내 차례가 아니에요.");
            return;
        }
        PlayerState state = engine.getPlayerState(playerId);
        if (state == null) {
            player.sendMessage("플레이어 상태를 찾을 수 없어요.");
            return;
        }
        Tile tile = resolveTileByInstance(state, instanceId);
        if (tile == null) {
            player.sendMessage("선택한 패가 유효하지 않아요.");
            return;
        }
        if (!engine.canDiscard(playerId, tile)) {
            player.sendMessage("해당 패는 버릴 수 없어요.");
            return;
        }
        dialogs.showDiscardPreview(player, tile);
        ItemStack payload = uiManager.createTileItem(tile);
        if (!dialogs.shouldConfirmDiscard()) {
            handleDiscardInternal(player, instanceId);
            return;
        }
        dialogs.showDiscardConfirmDialog(player, tile, payload);
    }

    void handleDiscardInternal(Player player, ItemStack item) {
        if (engine == null) {
            player.sendMessage("게임이 아직 시작되지 않았어요.");
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!playerId.equals(engine.getActivePlayer())) {
            player.sendMessage("지금은 내 차례가 아니에요.");
            return;
        }
        PlayerState state = engine.getPlayerState(playerId);
        if (state == null) {
            player.sendMessage("플레이어 상태를 찾을 수 없어요.");
            return;
        }
        Optional<Tile> tile = uiManager.readTile(item, state);
        if (tile.isEmpty()) {
            player.sendMessage("선택한 패가 유효하지 않아요.");
            return;
        }
        completeDiscard(player, tile.get());
    }

    void handleDiscardInternal(Player player, int instanceId) {
        if (engine == null) {
            player.sendMessage("게임이 아직 시작되지 않았어요.");
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!playerId.equals(engine.getActivePlayer())) {
            player.sendMessage("지금은 내 차례가 아니에요.");
            return;
        }
        PlayerState state = engine.getPlayerState(playerId);
        if (state == null) {
            player.sendMessage("플레이어 상태를 찾을 수 없어요.");
            return;
        }
        Tile tile = resolveTileByInstance(state, instanceId);
        if (tile == null) {
            player.sendMessage("선택한 패가 유효하지 않아요.");
            return;
        }
        completeDiscard(player, tile);
    }

    private void completeDiscard(Player player, Tile tile) {
        if (engine == null) {
            player.sendMessage("게임이 아직 시작되지 않았어요.");
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!engine.discard(playerId, tile)) {
            player.sendMessage("해당 패는 버릴 수 없어요.");
            return;
        }
        cancelTurnTimer();
        dialogs.closeDialog(player);
        eventLogger.record(new GameEvent(id, playerId, GameEventType.DISCARD, dialogs.buildTilePayload(tile)));
        callEvent(new MahjongDiscardEvent(this, playerId, player, tile));
        lastAction = "DISCARD";
        updateHand(playerId, false);
        broadcast(player.getName() + " 님이 " + tile.getId().toDisplayString() + "을(를) 버렸어요.");
        notifyCoachAfterDiscard(playerId, tile);
        updateWorldUi();
        int seconds = plugin.getConfig().getInt("timers.callWindowSeconds", 5);
        boolean hasCall = notifyCallOptions(seconds);
        if (hasCall) {
            scheduleCallResolution();
            updateWorldUiActions(seconds);
        } else {
            resolveCallWindow();
        }
    }

    private void requestRonInternal(Player player) {
        if (engine == null) {
            player.sendMessage("게임이 아직 시작되지 않았어요.");
            return;
        }
        if (engine.getState() != GameState.CALL_WINDOW) {
            player.sendMessage("지금은 호출 창이 열려 있지 않아요.");
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!engine.canRon(playerId)) {
            PlayerState state = engine.getPlayerState(playerId);
            if (state != null && state.getHand().isFuriten()) {
                player.sendMessage("후리텐 상태라 론을 할 수 없어요.");
            } else {
                player.sendMessage("론이 가능한 상태가 아니에요.");
            }
            return;
        }
        Tile lastDiscard = engine.getLastDiscard();
        if (lastDiscard == null) {
            player.sendMessage("마지막 버림패가 없어요.");
            return;
        }
        engine.addCallRequest(new CallRequest(playerId, CallType.RON, List.of(lastDiscard)));
        player.sendMessage("론을 선언했어요.");
        resolveCallWindow();
    }

    private void requestPonInternal(Player player) {
        requestCallInternal(player, CallType.PON);
    }

    private void requestChiInternal(Player player, int optionIndex) {
        if (engine == null) {
            player.sendMessage("게임이 아직 시작되지 않았어요.");
            return;
        }
        if (engine.getState() != GameState.CALL_WINDOW) {
            player.sendMessage("지금은 호출 창이 열려 있지 않아요.");
            return;
        }
        Optional<CallRequest> request = engine.createChiRequest(player.getUniqueId(), optionIndex);
        if (request.isEmpty()) {
            player.sendMessage("치가 가능한 상태가 아니에요.");
            return;
        }
        engine.addCallRequest(request.get());
        player.sendMessage("치를 선언했어요.");
        resolveCallWindow();
    }

    private void requestChiSelectionInternal(Player player) {
        if (engine == null) {
            player.sendMessage("게임이 아직 시작되지 않았어요.");
            return;
        }
        if (engine.getState() != GameState.CALL_WINDOW) {
            player.sendMessage("지금은 호출 창이 열려 있지 않아요.");
            return;
        }
        UUID playerId = player.getUniqueId();
        int chiCount = engine.getChiOptionCount(playerId);
        if (chiCount <= 0) {
            player.sendMessage("치가 가능한 상태가 아니에요.");
            return;
        }
        if (chiCount == 1) {
            requestChiInternal(player, 1);
            return;
        }
        if (!dialogs.isEnabled()) {
            player.sendMessage("치 선택지가 여러 개예요. /mj chi <1-" + chiCount + ">로 선택해 주세요.");
            return;
        }
        List<CallOption> options = resolveChiChoices(playerId);
        if (options.isEmpty()) {
            player.sendMessage("치가 가능한 상태가 아니에요.");
            return;
        }
        dialogs.showChiDialog(player, options);
    }

    private void requestKanInternal(Player player, int optionIndex) {
        if (engine == null) {
            player.sendMessage("게임이 아직 시작되지 않았어요.");
            return;
        }
        if (engine.getState() == GameState.CALL_WINDOW) {
            requestCallInternal(player, CallType.KAN);
            return;
        }
        if (engine.getState() != GameState.TURN_DISCARD) {
            player.sendMessage("지금은 깡을 할 수 없어요.");
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!playerId.equals(engine.getActivePlayer())) {
            player.sendMessage("지금은 내 차례가 아니에요.");
            return;
        }
        List<KanOption> options = engine.getSelfKanOptions(playerId);
        if (options.isEmpty()) {
            player.sendMessage("깡이 가능한 상태가 아니에요.");
            return;
        }
        if (optionIndex <= 0) {
            if (options.size() == 1) {
                optionIndex = 1;
            } else if (dialogs.isEnabled()) {
                dialogs.showSelfKanDialog(player, options);
                return;
            } else {
                player.sendMessage("깡 선택지가 여러 개예요. /mj kan <1-" + options.size() + ">로 선택해 주세요.");
                return;
            }
        }
        if (optionIndex < 1 || optionIndex > options.size()) {
            player.sendMessage("사용법: /mj kan <1-" + options.size() + ">");
            return;
        }
        if (!engine.declareKan(playerId, optionIndex)) {
            player.sendMessage("깡이 가능한 상태가 아니에요.");
            return;
        }
        dialogs.closeDialog(player);
        KanOption option = options.get(optionIndex - 1);
        eventLogger.record(new GameEvent(id, playerId, GameEventType.CALL, dialogs.buildKanPayload(option)));
        callEvent(new MahjongCallEvent(this, playerId, player, CallType.KAN, option.getTiles(), null, true));
        broadcast(player.getName() + " 님이 깡을 선언했어요.");
        updateHand(playerId, true);
        broadcastDoraIndicators();
        if (engine.getState() == GameState.HAND_END) {
            endHand();
            return;
        }
        announceTurn(playerId);
    }

    private void requestRiichiInternal(Player player) {
        if (engine == null) {
            player.sendMessage("게임이 아직 시작되지 않았어요.");
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!engine.declareRiichi(playerId)) {
            player.sendMessage("리치가 가능한 상태가 아니에요.");
            return;
        }
        dialogs.closeDialog(player);
        eventLogger.record(new GameEvent(id, playerId, GameEventType.RIICHI, "state=declare"));
        callEvent(new MahjongRiichiEvent(this, playerId, player));
        lastAction = "RIICHI";
        broadcast(player.getName() + " 님이 리치를 선언했어요.");
        updateWorldUi();
    }

    private void requestTsumoInternal(Player player) {
        if (engine == null) {
            player.sendMessage("게임이 아직 시작되지 않았어요.");
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!engine.declareTsumo(playerId)) {
            player.sendMessage("쯔모가 가능한 상태가 아니에요.");
            return;
        }
        dialogs.closeDialog(player);
        endHand();
    }

    private void requestStartInternal(Player player) {
        if (player == null) {
            return;
        }
        if (players.size() < MAX_PLAYERS) {
            player.sendMessage("시작할 수 없어요. 4명이 필요해요 (현재 " + players.size() + "명).");
            return;
        }
        if (getState() != GameState.LOBBY) {
            player.sendMessage("이미 게임이 시작됐어요.");
            return;
        }
        UUID playerId = player.getUniqueId();
        if (roomMode) {
            if (!isHost(playerId)) {
                player.sendMessage("호스트만 게임을 시작할 수 있어요.");
                return;
            }
            if (!areAllReady()) {
                player.sendMessage("시작하려면 모두 준비해야 해요 (" + readyPlayers.size() + "/" + players.size() + ").");
                return;
            }
        }
        if (start()) {
            dialogs.closeDialogsForAll();
            broadcast("게임을 시작했어요.");
        } else {
            player.sendMessage("시작 조건이 맞지 않아요. 4명 + 로비 상태여야 해요.");
        }
    }

    private void requestCallInternal(Player player, CallType type) {
        if (engine == null) {
            player.sendMessage("게임이 아직 시작되지 않았어요.");
            return;
        }
        if (engine.getState() != GameState.CALL_WINDOW) {
            player.sendMessage("지금은 호출 창이 열려 있지 않아요.");
            return;
        }
        Optional<CallRequest> request;
        switch (type) {
            case PON:
                request = engine.createPonRequest(player.getUniqueId());
                break;
            case CHI:
                request = engine.createChiRequest(player.getUniqueId());
                break;
            case KAN:
                request = engine.createKanRequest(player.getUniqueId());
                break;
            default:
                request = Optional.empty();
                break;
        }
        if (request.isEmpty()) {
            player.sendMessage("지금은 " + callName(type) + "이(가) 불가능해요.");
            return;
        }
        engine.addCallRequest(request.get());
        player.sendMessage("치를 선언했어요.");
        resolveCallWindow();
    }

    private void scheduleCallResolution() {
        if (callTask != null) {
            callTask.cancel();
        }
        int seconds = plugin.getConfig().getInt("timers.callWindowSeconds", 5);
        if (seconds <= 0) {
            resolveCallWindow();
            return;
        }
        long delayTicks = seconds * 20L;
        startCallBossBar(seconds);
        callTask = Bukkit.getScheduler().runTaskLater(plugin, () -> queue.enqueue(this::resolveCallWindow), delayTicks);
    }

    private void resolveCallWindow() {
        if (callTask != null) {
            callTask.cancel();
            callTask = null;
        }
        clearCallBossBar();
        dialogs.clearCallPopups();
        dialogs.clearCallDialogs();
        cancelBotCalls();
        if (engine == null) {
            return;
        }
        Tile lastDiscard = engine.getLastDiscard();
        UUID lastDiscarder = engine.getLastDiscarder();
        CallRequest resolved = engine.resolveCalls();
        if (resolved == null) {
            lastAction = "NO_CALL";
        }
        updateFuritenWarnings();
        if (resolved != null) {
            List<Tile> callTiles = new ArrayList<>(resolved.getTiles());
            if (lastDiscard != null && resolved.getType() != CallType.RON) {
                callTiles.add(lastDiscard);
            } else if (lastDiscard != null && resolved.getType() == CallType.RON && callTiles.isEmpty()) {
                callTiles.add(lastDiscard);
            }
            Player caller = plugin.getServer().getPlayer(resolved.getPlayerId());
            callEvent(new MahjongCallEvent(this, resolved.getPlayerId(), caller, resolved.getType(),
                    callTiles, lastDiscarder, false));
        }
        if (engine.getState() == GameState.HAND_END) {
            endHand();
            return;
        }
        UUID active = engine.getActivePlayer();
        if (resolved != null) {
            eventLogger.record(new GameEvent(id, resolved.getPlayerId(), GameEventType.CALL,
                    dialogs.buildCallPayload(resolved, lastDiscard, lastDiscarder)));
            lastAction = "CALL";
            updateHand(resolved.getPlayerId(), true);
            if (resolved.getType() == CallType.KAN) {
                broadcastDoraIndicators();
            }
        }
        updateHand(active, true);
        announceTurn(active);
        updateWorldUi();
        updateWorldUiActions(-1);
    }

    private void endHand() {
        if (callTask != null) {
            callTask.cancel();
            callTask = null;
        }
        clearCallBossBar();
        cancelTurnTimer();
        dialogs.clearCallDialogs();
        coachAdviceCache.clear();
        clearCoachOverlays();
        if (engine == null) {
            return;
        }
        Map<UUID, Integer> beforePoints = snapshotPoints();
        int honbaApplied = engine.getRoundState().getHonba();
        int riichiPotApplied = engine.getRoundState().getRiichiPot();
        UUID winner = engine.getWinner();
        UUID discarder = engine.getLastDiscarder();
        ScoreResult score = null;
        List<UUID> tenpaiPlayers = List.of();
        if (winner != null) {
            updateHand(winner, true);
            String name = resolveName(winner);
            String suffix = engine.isTsumoWin() ? " (쯔모)" : "";
            eventLogger.record(new GameEvent(id, winner, GameEventType.WIN, dialogs.buildWinPayload(engine.isTsumoWin(), discarder)));
            lastAction = "WIN";
            broadcast("화료: " + name + suffix + ".");
            score = settlePoints(winner);
            broadcastScore(score);
            broadcastPoints();
            updateRoundAfterHand(winner, null);
            updateWorldUi();
        } else {
            tenpaiPlayers = resolveTenpaiPlayers();
            eventLogger.record(new GameEvent(id, null, GameEventType.RYUUKYOKU, dialogs.buildRyuukyokuPayload(tenpaiPlayers)));
            lastAction = "RYUUKYOKU";
            broadcast("유국입니다.");
            broadcastTenpai(tenpaiPlayers);
            settleDraw(tenpaiPlayers);
            broadcastPoints();
            updateRoundAfterHand(null, tenpaiPlayers);
            updateWorldUi();
        }
        Map<UUID, Integer> afterPoints = snapshotPoints();
        Map<UUID, Integer> deltas = computePointDeltas(beforePoints, afterPoints);
        HandResult result = new HandResult(winner, discarder, engine.isTsumoWin(), score, tenpaiPlayers,
                deltas, afterPoints, honbaApplied, riichiPotApplied, isGameOver(), engine.getRoundState());
        callEvent(new MahjongHandEndEvent(this, winner, discarder, engine.isTsumoWin(), score, tenpaiPlayers,
                deltas, afterPoints, honbaApplied, riichiPotApplied, result.gameOver, result.nextRound));
        if (winner != null) {
            Player winnerPlayer = plugin.getServer().getPlayer(winner);
            Player discarderPlayer = discarder != null ? plugin.getServer().getPlayer(discarder) : null;
            callEvent(new MahjongWinEvent(this, winner, winnerPlayer, discarder, discarderPlayer,
                    engine.isTsumoWin(), score));
        }
        dialogs.updateWorldHandResult(result);
        if (dialogs.isEnabled()) {
            dialogs.showHandResultDialogs(result);
        }
        if (result.gameOver) {
            logGameEnd();
            broadcast("게임이 종료되었어요.");
            resetToLobby();
        } else {
            scheduleAutoNextHand();
        }

    }
    private void updateHand(UUID playerId, boolean openIfNeeded) {
        if (engine == null) {
            return;
        }
        if (isBot(playerId)) {
            return;
        }
        PlayerState state = engine.getPlayerState(playerId);
        if (state == null) {
            return;
        }
        HandInventory holder = handInventories.computeIfAbsent(playerId, uiManager::createHandInventory);
        uiManager.renderHand(holder.getInventory(), state);
        Player player = plugin.getServer().getPlayer(playerId);
        if (plugin.getConfig().getBoolean("ui.preferWorld", false)) {
            ensureWorldUi();
            if (worldUi.isSpawned()) {
                worldUi.updateHandDisplay(this, playerId, state);
            }
        }
        if (player != null && openIfNeeded && !plugin.getConfig().getBoolean("ui.preferWorld", false)) {
            if (player.getOpenInventory().getTopInventory().getHolder() != holder) {
                player.openInventory(holder.getInventory());
            }
        }
    }

    private Tile resolveTileByInstance(PlayerState state, int instanceId) {
        if (state == null) {
            return null;
        }
        for (Tile tile : state.getHand().getConcealed()) {
            if (tile.getInstanceId() == instanceId) {
                return tile;
            }
        }
        return null;
    }

    private void announceTurn(UUID playerId) {
        if (engine != null) {
            logDrawIfNeeded();
        }
        if (isBot(playerId)) {
            scheduleBotTurn(playerId);
            updateFuritenWarnings();
            updateWorldUi();
            return;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            int byoyomiSeconds = loadTurnByoyomiSeconds();
            int extraRemaining = getTurnExtraSeconds(playerId);
            if (byoyomiSeconds > 0 || extraRemaining > 0) {
                StringBuilder message = new StringBuilder("내 차례예요. ");
                if (byoyomiSeconds > 0) {
                    message.append("기본 ").append(byoyomiSeconds).append("초");
                }
                if (extraRemaining > 0) {
                    if (byoyomiSeconds > 0) {
                        message.append(" + ");
                    }
                    message.append("추가 ").append(extraRemaining).append("초");
                }
                message.append(" 안에 버릴 패를 골라 주세요. 시간이 지나면 자동으로 버려요.");
                player.sendMessage(message.toString());
            } else {
                player.sendMessage("내 차례예요. 버릴 패를 골라 주세요.");
            }
            if (engine != null && engine.canDeclareRiichi(playerId)) {
                player.sendMessage("리치 가능: /mj riichi");
            }
            if (engine != null && engine.canDeclareKan(playerId)) {
                player.sendMessage("깡 가능: /mj kan");
            }
            if (engine != null && engine.canTsumo(playerId)) {
                player.sendMessage("쯔모 가능: /mj tsumo");
            }
            if (engine != null && dialogs.isEnabled()) {
                dialogs.showActionDialog(player,
                        engine.canDeclareRiichi(playerId),
                        engine.canTsumo(playerId),
                        engine.canDeclareKan(playerId));
            }
            sendCoachAdvice(playerId);
        }
        startTurnTimer(playerId);
        updateFuritenWarnings();
        updateWorldUi();
    }

    private void sendCoachAdvice(UUID playerId) {
        if (!shouldSendCoach(playerId) || engine == null) {
            return;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            return;
        }
        PrivateState privateState = buildPrivateState(playerId);
        CoachAdvice advice = CoachAdvisor.buildAdvice(privateState, engine.canDeclareRiichi(playerId));
        coachAdviceCache.put(playerId, advice);
        for (String line : formatCoachAdvice(advice)) {
            player.sendMessage(line);
        }
        showCoachOverlay(playerId, advice);
    }

    private void notifyCoachAfterDiscard(UUID playerId, Tile discarded) {
        if (!shouldSendCoach(playerId)) {
            coachAdviceCache.remove(playerId);
            return;
        }
        CoachAdvice advice = coachAdviceCache.remove(playerId);
        if (advice == null || discarded == null) {
            return;
        }
        TileId discardId = TileCounter.normalize(discarded.getId());
        for (CoachSuggestion suggestion : advice.getSuggestions()) {
            if (sameTile(discardId, suggestion.getDiscard())) {
                return;
            }
        }
        if (advice.getSuggestions().isEmpty()) {
            return;
        }
        CoachSuggestion best = advice.getSuggestions().get(0);
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            player.sendMessage("코치: 추천 1순위는 " + best.getDiscard().toDisplayString()
                    + " (샹텐 " + best.getShanten() + ", 유효패 " + best.getUkeire() + ").");
        }
        showCoachMissOverlay(playerId, best);
    }

    private List<String> formatCoachAdvice(CoachAdvice advice) {
        if (advice == null || advice.getSuggestions().isEmpty()) {
            return List.of("코치: 추천 결과가 없어요.");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("코치: 추천 버림패 ");
        for (int i = 0; i < advice.getSuggestions().size(); i++) {
            CoachSuggestion suggestion = advice.getSuggestions().get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(suggestion.getDiscard().toDisplayString())
                    .append(" (샹텐 ").append(suggestion.getShanten())
                    .append(", 유효패 ").append(suggestion.getUkeire()).append(")");
        }
        List<String> lines = new ArrayList<>();
        lines.add(sb.toString());
        if (advice.isRiichiAvailable()) {
            String recommend = advice.isRiichiRecommended() ? "리치" : "유지";
            lines.add("코치: 리치 기대값~" + advice.getRiichiValue()
                    + " / 유지 기대값~" + advice.getKeepValue() + " -> " + recommend);
        } else {
            lines.add("코치: 샹텐을 줄이는 방향이 좋아요.");
        }
        return lines;
    }

    private void showCoachOverlay(UUID playerId, CoachAdvice advice) {
        if (playerId == null || advice == null || advice.getSuggestions().isEmpty()) {
            return;
        }
        String text = buildCoachOverlayText(advice);
        showCoachOverlay(playerId, text, BarColor.BLUE, NamedTextColor.AQUA);
    }

    private void showCoachMissOverlay(UUID playerId, CoachSuggestion best) {
        if (playerId == null || best == null) {
            return;
        }
        String text = buildCoachMissText(best);
        showCoachOverlay(playerId, text, BarColor.RED, NamedTextColor.RED);
    }

    private String buildCoachOverlayText(CoachAdvice advice) {
        CoachSuggestion best = advice.getSuggestions().get(0);
        StringBuilder sb = new StringBuilder();
        sb.append("코치: 추천 버림패 ")
                .append(best.getDiscard().toDisplayString())
                .append(" (샹텐 ").append(best.getShanten())
                .append(", 유효패 ").append(best.getUkeire()).append(")");
        if (advice.isRiichiAvailable()) {
            sb.append(advice.isRiichiRecommended() ? " 리치" : " 유지");
        }
        return sb.toString();
    }

    private String buildCoachMissText(CoachSuggestion best) {
        return "코치: 추천 1순위 " + best.getDiscard().toDisplayString()
                + " (샹텐 " + best.getShanten() + ", 유효패 " + best.getUkeire() + ")";
    }

    private void showCoachOverlay(UUID playerId, String text, BarColor barColor, NamedTextColor textColor) {
        if (text == null || text.isBlank()) {
            return;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            return;
        }
        player.sendActionBar(Component.text(text, textColor));
        clearCoachBar(playerId);
        BossBar bar = Bukkit.createBossBar(text, barColor, BarStyle.SOLID);
        bar.addPlayer(player);
        bar.setProgress(1.0);
        bar.setVisible(true);
        coachBars.put(playerId, bar);
        int ticks = Math.max(1, COACH_OVERLAY_SECONDS * 20);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> clearCoachOverlay(playerId), ticks);
        coachBarTasks.put(playerId, task);
    }

    private void clearCoachOverlay(UUID playerId) {
        clearCoachBar(playerId);
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            player.sendActionBar("");
        }
    }

    private void clearCoachOverlays() {
        for (UUID playerId : new ArrayList<>(coachBars.keySet())) {
            clearCoachOverlay(playerId);
        }
        coachBars.clear();
        coachBarTasks.clear();
    }

    private void clearCoachBar(UUID playerId) {
        BukkitTask task = coachBarTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        BossBar bar = coachBars.remove(playerId);
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
    }

    private boolean shouldSendCoach(UUID playerId) {
        return playerId != null
                && !isBot(playerId)
                && coachPlayers.contains(playerId)
                && isCoachAllowed();
    }

    private void scheduleBotTurn(UUID playerId) {
        if (engine == null || !isBot(playerId)) {
            return;
        }
        cancelBotTurn(playerId);
        int delay = Math.max(1, botDelayTicks);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () ->
                queue.enqueue(() -> executeBotTurn(playerId)), delay);
        botTurnTasks.put(playerId, task);
    }

    private void executeBotTurn(UUID playerId) {
        cancelBotTurn(playerId);
        if (engine == null || engine.getState() != GameState.TURN_DISCARD) {
            return;
        }
        if (!playerId.equals(engine.getActivePlayer())) {
            return;
        }
        BotProfile profile = bots.get(playerId);
        if (profile == null) {
            return;
        }
        if (engine.canTsumo(playerId)) {
            BotDecision decision = new BotDecision(BotAction.win(true), null, false, "tsumo");
            logBotDecision(profile, decision);
            engine.declareTsumo(playerId);
            endHand();
            return;
        }
        PrivateState privateState = buildPrivateState(playerId);
        PublicState publicState = buildPublicState();
        TurnContext context = buildTurnContext();
        int[] remainingCounts = TileCounter.buildRemainingCounts(publicState, privateState);
        BotDecision decision = BotController.decideDiscard(profile,
                publicState,
                privateState,
                context,
                engine.canDeclareRiichi(playerId),
                engine.getLastDrawnTile(),
                engine.getRules(),
                engine.getRoundState(),
                engine.getDoraIndicators(),
                remainingCounts);
        if (decision == null || decision.getAction() == null) {
            return;
        }
        if (decision.isDeclareRiichi() && engine.declareRiichi(playerId)) {
            eventLogger.record(new GameEvent(id, playerId, GameEventType.RIICHI, "state=declare"));
            callEvent(new MahjongRiichiEvent(this, playerId, null));
            lastAction = "RIICHI";
            broadcast(resolveName(playerId) + " 님이 리치를 선언했어요.");
            updateWorldUi();
        }
        logBotDecision(profile, decision);
        Tile discardTile = decision.getAction().getDiscardTile();
        if (!handleBotDiscard(playerId, discardTile)) {
            Tile fallback = engine.getLastDrawnTile();
            if (fallback != null) {
                handleBotDiscard(playerId, fallback);
            }
        }
    }

    private boolean handleBotDiscard(UUID playerId, Tile tile) {
        if (engine == null || tile == null) {
            return false;
        }
        if (!engine.discard(playerId, tile)) {
            return false;
        }
        cancelTurnTimer();
        eventLogger.record(new GameEvent(id, playerId, GameEventType.DISCARD, dialogs.buildTilePayload(tile)));
        callEvent(new MahjongDiscardEvent(this, playerId, null, tile));
        lastAction = "DISCARD";
        updateHand(playerId, false);
        broadcast(resolveName(playerId) + " 님이 " + tile.getId().toDisplayString() + "을(를) 버렸어요.");
        updateWorldUi();
        int seconds = plugin.getConfig().getInt("timers.callWindowSeconds", 5);
        boolean hasCall = notifyCallOptions(seconds);
        if (hasCall) {
            scheduleCallResolution();
            updateWorldUiActions(seconds);
        } else {
            resolveCallWindow();
        }
        return true;
    }

    private void forceRandomDiscard(UUID playerId) {
        if (engine == null || playerId == null) {
            return;
        }
        if (engine.getState() != GameState.TURN_DISCARD) {
            return;
        }
        if (!playerId.equals(engine.getActivePlayer())) {
            return;
        }
        if (isBot(playerId)) {
            return;
        }
        PlayerState state = engine.getPlayerState(playerId);
        if (state == null) {
            return;
        }
        List<Tile> candidates = new ArrayList<>();
        for (Tile tile : state.getHand().getConcealed()) {
            if (engine.canDiscard(playerId, tile)) {
                candidates.add(tile);
            }
        }
        if (candidates.isEmpty()) {
            Tile fallback = engine.getLastDrawnTile();
            if (fallback != null && engine.canDiscard(playerId, fallback)) {
                candidates.add(fallback);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        Tile tile = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        if (!engine.discard(playerId, tile)) {
            return;
        }
        eventLogger.record(new GameEvent(id, playerId, GameEventType.DISCARD, dialogs.buildTilePayload(tile)));
        lastAction = "DISCARD";
        updateHand(playerId, false);
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            player.sendMessage("시간이 초과되어 자동으로 버렸어요.");
            dialogs.closeDialog(player);
        }
        broadcast(resolveName(playerId) + " 님이 " + tile.getId().toDisplayString() + "을(를) 버렸어요.");
        notifyCoachAfterDiscard(playerId, tile);
        updateWorldUi();
        int seconds = plugin.getConfig().getInt("timers.callWindowSeconds", 5);
        boolean hasCall = notifyCallOptions(seconds);
        if (hasCall) {
            scheduleCallResolution();
            updateWorldUiActions(seconds);
        } else {
            resolveCallWindow();
        }
    }

    private void scheduleBotCalls(int callWindowSeconds) {
        if (bots.isEmpty()) {
            return;
        }
        cancelBotCalls();
        if (engine == null || engine.getState() != GameState.CALL_WINDOW) {
            return;
        }
        if (callWindowSeconds <= 0) {
            return;
        }
        int maxTicks = Math.max(1, callWindowSeconds * 20 - 1);
        int delay = Math.min(botDelayTicks, maxTicks);
        botCallTask = Bukkit.getScheduler().runTaskLater(plugin, () -> queue.enqueue(this::executeBotCalls), delay);
    }

    private void executeBotCalls() {
        cancelBotCalls();
        if (engine == null || engine.getState() != GameState.CALL_WINDOW) {
            return;
        }
        boolean anyCall = false;
        for (UUID playerId : players) {
            if (!isBot(playerId) || playerId.equals(engine.getLastDiscarder())) {
                continue;
            }
            BotProfile profile = bots.get(playerId);
            if (profile == null) {
                continue;
            }
            CallRequest request = chooseBotCall(playerId, profile);
            if (request == null) {
                continue;
            }
            engine.addCallRequest(request);
            BotDecision decision = new BotDecision(BotAction.call(request.getType(), 0), null, false,
                    "call=" + request.getType().name());
            logBotDecision(profile, decision);
            anyCall = true;
        }
        if (anyCall) {
            resolveCallWindow();
        }
    }

    private CallRequest chooseBotCall(UUID playerId, BotProfile profile) {
        if (engine == null) {
            return null;
        }
        Tile lastDiscard = engine.getLastDiscard();
        if (lastDiscard == null) {
            return null;
        }
        if (engine.canRon(playerId)) {
            return new CallRequest(playerId, CallType.RON, List.of(lastDiscard));
        }
        if (profile.getDifficulty() == BotDifficulty.BEGINNER) {
            return null;
        }
        PrivateState privateState = buildPrivateState(playerId);
        if (privateState == null) {
            return null;
        }
        int baseShanten = ShantenCalculator.calculate(privateState.getConcealed(), privateState.getMelds().size());
        CallRequest best = null;
        int bestShanten = baseShanten;
        Optional<CallRequest> kanRequest = engine.createKanRequest(playerId);
        if (kanRequest.isPresent()) {
            int shanten = evaluateCallShanten(privateState, kanRequest.get());
            if (shanten < bestShanten) {
                bestShanten = shanten;
                best = kanRequest.get();
            }
        }
        Optional<CallRequest> ponRequest = engine.createPonRequest(playerId);
        if (ponRequest.isPresent()) {
            int shanten = evaluateCallShanten(privateState, ponRequest.get());
            if (shanten < bestShanten) {
                bestShanten = shanten;
                best = ponRequest.get();
            }
        }
        int chiCount = engine.getChiOptionCount(playerId);
        for (int i = 1; i <= chiCount; i++) {
            Optional<CallRequest> request = engine.createChiRequest(playerId, i);
            if (request.isEmpty()) {
                continue;
            }
            int shanten = evaluateCallShanten(privateState, request.get());
            if (shanten < bestShanten) {
                bestShanten = shanten;
                best = request.get();
            }
        }
        return best;
    }

    private int evaluateCallShanten(PrivateState privateState, CallRequest request) {
        if (privateState == null || request == null) {
            return 8;
        }
        List<Tile> remaining = new ArrayList<>(privateState.getConcealed());
        for (Tile tile : request.getTiles()) {
            remaining.remove(tile);
        }
        int openMelds = privateState.getMelds().size() + 1;
        return ShantenCalculator.minShantenAfterDiscard(remaining, openMelds);
    }

    private void logBotDecision(BotProfile profile, BotDecision decision) {
        if (profile == null || decision == null) {
            return;
        }
        StringBuilder payload = new StringBuilder();
        payload.append("diff=").append(profile.getDifficulty());
        payload.append(";seed=").append(profile.getSeed());
        payload.append(";action=").append(decision.getAction().getType());
        if (decision.getAction().getType() == BotAction.Type.DISCARD && decision.getAction().getDiscardTile() != null) {
            payload.append(";tile=").append(decision.getAction().getDiscardTile().getId().toShortString());
        }
        if (decision.getAction().getType() == BotAction.Type.CALL && decision.getAction().getCallType() != null) {
            payload.append(";call=").append(decision.getAction().getCallType().name());
        }
        if (decision.getAction().getType() == BotAction.Type.WIN) {
            payload.append(";tsumo=").append(decision.getAction().isTsumo() ? 1 : 0);
        }
        if (decision.getEvaluation() != null) {
            payload.append(";shanten=").append(decision.getEvaluation().getShanten());
            payload.append(";ukeire=").append(decision.getEvaluation().getUkeire().getTotal());
            payload.append(";safety=").append(decision.getEvaluation().getSafety());
            payload.append(";value=").append(decision.getEvaluation().getExpectedValue());
        }
        if (decision.isDeclareRiichi()) {
            payload.append(";riichi=1");
        }
        if (decision.getReason() != null && !decision.getReason().isBlank()) {
            payload.append(";reason=").append(decision.getReason());
        }
        eventLogger.record(new GameEvent(id, profile.getId(), GameEventType.BOT_DECISION, payload.toString()));
        if (isAiDebug()) {
            plugin.getLogger().info("[AI] " + resolveName(profile.getId()) + " " + payload);
        }
    }

    private ScoreResult settlePoints(UUID winnerId) {
        if (engine == null) {
            return null;
        }
        PlayerState winner = engine.getPlayerState(winnerId);
        if (winner == null) {
            return null;
        }
        int riichiStick = plugin.getConfig().getInt("scoring.riichiStick", 1000);
        int honbaRonBonus = plugin.getConfig().getInt("scoring.honbaRonBonus", 300);
        int honbaTsumoBonus = plugin.getConfig().getInt("scoring.honbaTsumoBonus", 100);
        int honba = engine.getRoundState().getHonba();
        GameRules rules = engine.getRules();
        boolean openTanyao = rules.isOpenTanyaoEnabled();
        boolean redDoraEnabled = rules.isRedDoraEnabled();
        boolean uraDoraEnabled = rules.isUraDoraEnabled();
        boolean ippatsuEnabled = rules.isIppatsuEnabled();
        ScoreResult score = ScoreCalculator.calculate(
                winner,
                engine.getRoundState(),
                engine.isTsumoWin(),
                engine.getWinningTile(),
                engine.getDoraIndicators(),
                engine.getUraDoraIndicators(),
                redDoraEnabled,
                openTanyao,
                ippatsuEnabled,
                uraDoraEnabled
        );
        if (engine.isTsumoWin()) {
            for (UUID playerId : players) {
                if (playerId.equals(winnerId)) {
                    continue;
                }
                PlayerState state = engine.getPlayerState(playerId);
                if (state == null) {
                    continue;
                }
                int basePayment;
                if (winner.getSeatWind() == engine.getRoundState().getDealerWind()) {
                    basePayment = score.getTsumoFromDealer();
                } else if (state.getSeatWind() == engine.getRoundState().getDealerWind()) {
                    basePayment = score.getTsumoFromDealer();
                } else {
                    basePayment = score.getTsumoFromOthers();
                }
                int payment = basePayment + (honba * honbaTsumoBonus);
                state.addPoints(-payment);
                winner.addPoints(payment);
            }
        } else {
            UUID discarder = engine.getLastDiscarder();
            if (discarder != null) {
                PlayerState state = engine.getPlayerState(discarder);
                if (state != null) {
                    int payment = score.getRonPayment() + (honba * honbaRonBonus);
                    state.addPoints(-payment);
                    winner.addPoints(payment);
                }
            }
        }
        int potCount = engine.getRoundState().getRiichiPot();
        if (potCount > 0) {
            int bonus = potCount * riichiStick;
            winner.addPoints(bonus);
            engine.getRoundState().addRiichiPot(-potCount);
        }
        return score;
    }

    private void broadcastPoints() {
        if (engine == null) {
            return;
        }
        broadcast("점수:");
        for (UUID playerId : players) {
            PlayerState state = engine.getPlayerState(playerId);
            if (state == null) {
                continue;
            }
            broadcast("- " + resolveName(playerId) + ": " + state.getPoints());
        }
    }

    private void broadcastScore(ScoreResult score) {
        if (score == null) {
            return;
        }
        broadcast("점수:");
    }

    private Map<UUID, Integer> snapshotPoints() {
        Map<UUID, Integer> snapshot = new HashMap<>();
        if (engine == null) {
            return snapshot;
        }
        for (UUID playerId : players) {
            PlayerState state = engine.getPlayerState(playerId);
            if (state != null) {
                snapshot.put(playerId, state.getPoints());
            }
        }
        return snapshot;
    }

    private Map<UUID, Integer> computePointDeltas(Map<UUID, Integer> before, Map<UUID, Integer> after) {
        Map<UUID, Integer> deltas = new HashMap<>();
        for (UUID playerId : players) {
            int start = before != null ? before.getOrDefault(playerId, 0) : 0;
            int end = after != null ? after.getOrDefault(playerId, 0) : 0;
            deltas.put(playerId, end - start);
        }
        return deltas;
    }

    private void logGameStart(long seed) {
        String payload = "seed=" + seed
                + ";players=" + formatPlayerIdList(players)
                + ";rules=" + describeRules(getRulesSnapshot());
        if (roomMode && roomCode != null) {
            payload += ";room=" + roomCode;
        }
        eventLogger.record(new GameEvent(id, hostId, GameEventType.GAME_START, payload));
        lastAction = "GAME_START";
    }

    private void logHandStart() {
        if (engine == null) {
            return;
        }
        RoundState round = engine.getRoundState();
        String payload = "round=" + round.getRoundWind()
                + ";kyoku=" + round.getKyoku()
                + ";dealer=" + round.getDealerWind()
                + ";honba=" + round.getHonba()
                + ";riichi=" + round.getRiichiPot()
                + ";hands=" + round.getHandsPlayed();
        eventLogger.record(new GameEvent(id, null, GameEventType.HAND_START, payload));
        lastAction = "HAND_START";
    }

    private void logGameEnd() {
        if (engine == null) {
            return;
        }
        String payload = "points=" + formatPointsPayload();
        eventLogger.record(new GameEvent(id, null, GameEventType.GAME_END, payload));
        lastAction = "GAME_END";
    }

    private void logDrawIfNeeded() {
        if (engine == null) {
            return;
        }
        int sequence = engine.getDrawSequence();
        if (sequence <= lastLoggedDrawSequence) {
            return;
        }
        lastLoggedDrawSequence = sequence;
        UUID playerId = engine.getLastDrawnPlayer();
        Tile tile = engine.getLastDrawnTile();
        if (playerId == null || tile == null) {
            return;
        }
        String payload = dialogs.buildDrawPayload(tile, engine.isLastDrawRinshan());
        eventLogger.record(new GameEvent(id, playerId, GameEventType.DRAW, payload));
        lastAction = "DRAW";
    }

    private String formatPlayerIdList(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (UUID playerId : ids) {
            values.add(playerId.toString());
        }
        return String.join(",", values);
    }

    private String formatPointsPayload() {
        if (engine == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (UUID playerId : players) {
            PlayerState state = engine.getPlayerState(playerId);
            if (state == null) {
                continue;
            }
            parts.add(playerId + ":" + state.getPoints());
        }
        return String.join(",", parts);
    }

    private PublicState buildPublicState() {
        if (engine == null) {
            return null;
        }
        Map<UUID, List<TileId>> discards = new HashMap<>();
        Map<UUID, List<TileId>> melds = new HashMap<>();
        Map<UUID, Integer> points = new HashMap<>();
        Map<UUID, Boolean> riichi = new HashMap<>();
        for (UUID playerId : players) {
            PlayerState state = engine.getPlayerState(playerId);
            if (state == null) {
                continue;
            }
            List<TileId> discardIds = new ArrayList<>();
            for (Tile tile : state.getDiscards()) {
                discardIds.add(TileCounter.normalize(tile.getId()));
            }
            discards.put(playerId, discardIds);
            List<TileId> meldIds = new ArrayList<>();
            for (wiki.creeper.mahjong.game.Meld meld : state.getHand().getMelds()) {
                for (Tile tile : meld.getTiles()) {
                    meldIds.add(TileCounter.normalize(tile.getId()));
                }
            }
            melds.put(playerId, meldIds);
            points.put(playerId, state.getPoints());
            riichi.put(playerId, state.getHand().isRiichiDeclared());
        }
        List<TileId> doraIndicators = new ArrayList<>();
        for (Tile tile : engine.getDoraIndicators()) {
            doraIndicators.add(tile.getId());
        }
        RoundState round = engine.getRoundState();
        return new PublicState(discards, melds, points, riichi, doraIndicators,
                round.getRoundWind(), round.getDealerWind(), round.getRemainingTiles());
    }

    private PrivateState buildPrivateState(UUID playerId) {
        if (engine == null || playerId == null) {
            return null;
        }
        PlayerState state = engine.getPlayerState(playerId);
        if (state == null) {
            return null;
        }
        return new PrivateState(playerId,
                state.getSeatWind(),
                state.getHand().getConcealed(),
                state.getHand().getMelds(),
                state.getHand().isRiichiDeclared(),
                state.getPoints());
    }

    private TurnContext buildTurnContext() {
        if (engine == null) {
            return new TurnContext(lastAction, state, null, null, false, -1);
        }
        Tile lastDiscard = engine.getLastDiscard();
        TileId lastDiscardId = lastDiscard == null ? null : TileCounter.normalize(lastDiscard.getId());
        boolean callWindow = engine.isCallWindowActive();
        int remainingSeconds = -1;
        if (callWindow) {
            remainingSeconds = callSecondsRemaining >= 0
                    ? callSecondsRemaining
                    : plugin.getConfig().getInt("timers.callWindowSeconds", 5);
        } else if (engine.getState() == GameState.TURN_DISCARD) {
            remainingSeconds = turnSecondsRemaining;
        }
        return new TurnContext(lastAction, engine.getState(), lastDiscardId, engine.getLastDiscarder(),
                callWindow, remainingSeconds);
    }

    private List<UUID> resolveTenpaiPlayers() {
        if (engine == null) {
            return List.of();
        }
        // SIMPLIFIED: tenpai check uses only implemented hand forms (standard/chiitoi/kokushi).
        List<UUID> result = new ArrayList<>();
        for (UUID playerId : players) {
            PlayerState state = engine.getPlayerState(playerId);
            if (state == null) {
                continue;
            }
            if (wiki.creeper.mahjong.game.HandValidator.isTenpai(state.getHand())) {
                result.add(playerId);
            }
        }
        return result;
    }

    private void broadcastTenpai(List<UUID> tenpaiPlayers) {
        if (tenpaiPlayers == null || tenpaiPlayers.isEmpty()) {
            broadcast("텐파이 없음");
            return;
        }
        List<String> names = new ArrayList<>();
        for (UUID playerId : tenpaiPlayers) {
            names.add(resolveName(playerId));
        }
        broadcast("텐파이: " + String.join(", ", names));
    }

    private void settleDraw(List<UUID> tenpaiPlayers) {
        if (engine == null) {
            return;
        }
        if (tenpaiPlayers == null || tenpaiPlayers.isEmpty() || tenpaiPlayers.size() == players.size()) {
            return;
        }
        int total = plugin.getConfig().getInt("scoring.notenPenaltyTotal", 3000);
        int notenCount = players.size() - tenpaiPlayers.size();
        if (notenCount <= 0) {
            return;
        }
        int gainEach = total / tenpaiPlayers.size();
        int payEach = total / notenCount;
        for (UUID playerId : players) {
            PlayerState state = engine.getPlayerState(playerId);
            if (state == null) {
                continue;
            }
            if (tenpaiPlayers.contains(playerId)) {
                state.addPoints(gainEach);
            } else {
                state.addPoints(-payEach);
            }
        }
    }

    private void updateRoundAfterHand(UUID winnerId, List<UUID> tenpaiPlayers) {
        if (engine == null) {
            return;
        }
        if (winnerId == null) {
            // SIMPLIFIED: tenpai check uses only implemented hand forms (standard/chiitoi/kokushi).
            boolean dealerTenpai = false;
            if (tenpaiPlayers != null) {
                for (UUID playerId : tenpaiPlayers) {
                    PlayerState state = engine.getPlayerState(playerId);
                    if (state != null && state.getSeatWind() == engine.getRoundState().getDealerWind()) {
                        dealerTenpai = true;
                        break;
                    }
                }
            }
            engine.getRoundState().addHonba(1);
            if (!dealerTenpai) {
                SeatWind nextDealer = engine.getRoundState().getDealerWind().next();
                engine.getRoundState().setDealerWind(nextDealer);
                if (nextDealer == SeatWind.EAST) {
                    engine.getRoundState().advanceRoundWind();
                }
            }
            engine.getRoundState().incrementHandsPlayed();
            return;
        }
        PlayerState winner = engine.getPlayerState(winnerId);
        if (winner != null && winner.getSeatWind() == engine.getRoundState().getDealerWind()) {
            engine.getRoundState().addHonba(1);
        } else {
            engine.getRoundState().setHonba(0);
            SeatWind nextDealer = engine.getRoundState().getDealerWind().next();
            engine.getRoundState().setDealerWind(nextDealer);
            if (nextDealer == SeatWind.EAST) {
                engine.getRoundState().advanceRoundWind();
            }
        }
        engine.getRoundState().incrementHandsPlayed();
    }

    private boolean isGameOver() {
        if (engine == null) {
            return false;
        }
        SeatWind maxWind = loadMaxRoundWind();
        if (maxWind == null) {
            return false;
        }
        return engine.getRoundState().getRoundIndex() > maxWind.order();
    }

    private boolean notifyCallOptions(int callWindowSeconds) {
        if (engine == null || engine.getState() != GameState.CALL_WINDOW) {
            return false;
        }
        boolean anyCall = false;
        for (UUID playerId : players) {
            if (playerId.equals(engine.getLastDiscarder())) {
                continue;
            }
            List<CallOption> choices = resolveCallChoices(playerId);
            if (!choices.isEmpty()) {
                anyCall = true;
            }
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            if (choices.isEmpty()) {
                continue;
            }
            List<String> options = resolveCallOptions(playerId, player);
            if (options.isEmpty()) {
                continue;
            }
            String message = "가능한 행동: " + String.join("/", options);
            if (callWindowSeconds >= 0) {
                message += " (" + callWindowSeconds + "초)";
            }
            player.sendMessage(message);
            dialogs.showCallPopup(player, options, callWindowSeconds);
        }
        if (anyCall) {
            scheduleBotCalls(callWindowSeconds);
        }
        return anyCall;
    }

    private String callName(CallType type) {
        return dialogs.callName(type);
    }

    List<String> resolveCallOptions(UUID playerId, Player player) {
        List<String> options = new ArrayList<>();
        if (engine.canRon(playerId)) {
            options.add(callName(CallType.RON));
        }
        if (engine.createKanRequest(playerId).isPresent()) {
            options.add(callName(CallType.KAN));
        }
        if (engine.createPonRequest(playerId).isPresent()) {
            options.add(callName(CallType.PON));
        }
        int chiCount = engine.getChiOptionCount(playerId);
        if (chiCount > 0) {
            options.add(callName(CallType.CHI));
            if (chiCount > 1 && !dialogs.isEnabled()) {
                player.sendMessage("치 선택지가 여러 개예요. /mj chi <1-" + chiCount + ">로 선택해 주세요.");
            }
        }
        return options;
    }

    private List<CallOption> resolveCallChoices(UUID playerId) {
        List<CallOption> options = new ArrayList<>();
        if (engine.canRon(playerId)) {
            Tile last = engine.getLastDiscard();
            options.add(new CallOption(CallType.RON, 0, last == null ? List.of() : List.of(last)));
        }
        engine.createKanRequest(playerId).ifPresent(request ->
                options.add(new CallOption(CallType.KAN, 0, request.getTiles())));
        engine.createPonRequest(playerId).ifPresent(request ->
                options.add(new CallOption(CallType.PON, 0, request.getTiles())));
        int chiCount = engine.getChiOptionCount(playerId);
        for (int i = 1; i <= chiCount; i++) {
            int chiIndex = i;
            Optional<CallRequest> request = engine.createChiRequest(playerId, chiIndex);
            request.ifPresent(callRequest ->
                    options.add(new CallOption(CallType.CHI, chiIndex, callRequest.getTiles())));
        }
        return options;
    }

    private List<CallOption> resolveChiChoices(UUID playerId) {
        List<CallOption> options = new ArrayList<>();
        int chiCount = engine.getChiOptionCount(playerId);
        for (int i = 1; i <= chiCount; i++) {
            int chiIndex = i;
            Optional<CallRequest> request = engine.createChiRequest(playerId, chiIndex);
            request.ifPresent(callRequest ->
                    options.add(new CallOption(CallType.CHI, chiIndex, callRequest.getTiles())));
        }
        return options;
    }


    private void updateFuritenWarnings() {
        if (engine == null) {
            return;
        }
        for (UUID playerId : players) {
            PlayerState state = engine.getPlayerState(playerId);
            if (state == null) {
                continue;
            }
            boolean furiten = state.getHand().isFuriten();
            if (furiten) {
                if (furitenNotified.add(playerId)) {
                    Player player = plugin.getServer().getPlayer(playerId);
                    if (player != null) {
                        player.sendMessage("론이 가능한 상태가 아니에요.");
                    }
                }
            } else {
                furitenNotified.remove(playerId);
            }
        }
    }

    private void broadcastRoundStatus() {
        if (engine == null) {
            return;
        }
        int honba = engine.getRoundState().getHonba();
        int riichiPot = engine.getRoundState().getRiichiPot();
        int hands = engine.getRoundState().getHandsPlayed();
        RoundState round = engine.getRoundState();
        broadcast("국 " + seatLabel(round.getRoundWind()) + " " + round.getKyoku()
                + " / 딜러: " + seatLabel(round.getDealerWind()));
        broadcast("본장: " + honba + " / 공탁: " + riichiPot + " / 핸드: " + hands);
    }

    private void broadcastRoomRules() {
        roomUi.broadcastRoomRules();
    }

    private void updateRoomLobbyUi() {
        roomUi.updateRoomLobbyUi();
    }

    private void clearRoomBossBar() {
        roomUi.clearRoomBossBar();
    }

    boolean assignSeat(Player player, SeatWind seat) {
        if (player == null || seat == null) {
            return false;
        }
        if (!roomMode || getState() != GameState.LOBBY) {
            player.sendMessage("좌석 선택은 로비에서만 가능해요.");
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (!players.contains(playerId)) {
            return false;
        }
        UUID occupant = seatAssignments.get(seat);
        SeatWind currentSeat = playerSeats.get(playerId);
        if (occupant != null && !occupant.equals(playerId)) {
            player.sendMessage("좌석 " + seatLabel(seat) + "은(는) 이미 사용 중이에요.");
            return false;
        }
        if (occupant != null && occupant.equals(playerId)) {
            clearSeat(playerId, true);
            updateRoomLobbyUi();
            return true;
        }
        if (currentSeat != null) {
            seatAssignments.remove(currentSeat);
        }
        seatAssignments.put(seat, playerId);
        playerSeats.put(playerId, seat);
        readyPlayers.remove(playerId);
        updateRoomLobbyUi();
        return true;
    }

    private void autoAssignSeat(UUID playerId) {
        if (playerId == null || !roomMode || getState() != GameState.LOBBY) {
            return;
        }
        if (playerSeats.containsKey(playerId)) {
            return;
        }
        for (SeatWind seat : SeatWind.values()) {
            if (!seatAssignments.containsKey(seat)) {
                assignSeatInternal(playerId, seat, true);
                updateRoomLobbyUi();
                return;
            }
        }
    }

    void clearSeat(UUID playerId, boolean updateUi) {
        if (playerId == null) {
            return;
        }
        SeatWind seat = playerSeats.remove(playerId);
        if (seat != null) {
            seatAssignments.remove(seat);
            readyPlayers.remove(playerId);
        }
        if (updateUi) {
            updateRoomLobbyUi();
        }
    }

    private void assignSeatInternal(UUID playerId, SeatWind seat, boolean override) {
        if (playerId == null || seat == null) {
            return;
        }
        if (!override && seatAssignments.containsKey(seat)) {
            return;
        }
        SeatWind current = playerSeats.get(playerId);
        if (current != null) {
            seatAssignments.remove(current);
        }
        seatAssignments.put(seat, playerId);
        playerSeats.put(playerId, seat);
        readyPlayers.remove(playerId);
    }

    private UUID resolveNextHost() {
        for (UUID playerId : players) {
            if (!isBot(playerId)) {
                return playerId;
            }
        }
        return null;
    }

    private List<UUID> resolveSeatOrder() {
        List<UUID> order = new ArrayList<>(MAX_PLAYERS);
        for (SeatWind seat : SeatWind.values()) {
            UUID occupant = seatAssignments.get(seat);
            if (occupant == null) {
                return List.of();
            }
            order.add(occupant);
        }
        return order;
    }

    String seatLabel(SeatWind seat) {
        if (seat == null) {
            return "알수없음";
        }
        switch (seat) {
            case EAST:
                return "동";
            case SOUTH:
                return "남";
            case WEST:
                return "서";
            case NORTH:
            default:
                return "북";
        }
    }

    String describeRules(GameRules rules) {
        return "빨간 도라: " + onOff(rules.isRedDoraEnabled())
                + " | 오픈 탄야오: " + onOff(rules.isOpenTanyaoEnabled())       
                + " | 일발: " + onOff(rules.isIppatsuEnabled())
                + " | 우라 도라: " + onOff(rules.isUraDoraEnabled())
                + " | 봇: " + onOff(botsEnabled)
                + " | 코치: " + onOff(coachEnabled)
                + " | 코치 랭크 잠금: " + onOff(coachRankDisabled);
    }

    String onOff(boolean enabled) {
        return enabled ? "켜짐" : "꺼짐";
    }

    String formatRuleLine(String label, boolean enabled) {
        return label + ": " + onOff(enabled);
    }

    private boolean sameTile(TileId a, TileId b) {
        if (a == null || b == null) {
            return false;
        }
        TileId na = TileCounter.normalize(a);
        TileId nb = TileCounter.normalize(b);
        return na.getSuit() == nb.getSuit() && na.getRank() == nb.getRank();
    }

    void sendRoomRulesSummary(Player player) {
        player.sendMessage("테이블 규칙: " + describeRules(getRulesSnapshot()));
        player.sendMessage("규칙 변경: /mj table rules <rule> <on|off> 또는 /mj table rules preset <default|kuitan|classic>");
        player.sendMessage("rule 목록: redDora, openTanyao, ippatsu(일발), uraDora, bots(봇), coach, coachRank");
    }

    private GameRules loadRules() {
        boolean redDora = plugin.getConfig().getBoolean("rules.redDora", true);
        boolean openTanyao = plugin.getConfig().getBoolean("rules.openTanyao", false);
        boolean ippatsu = plugin.getConfig().getBoolean("rules.ippatsu", true);
        boolean uraDora = plugin.getConfig().getBoolean("rules.uraDora", true);
        return new GameRules(redDora, openTanyao, ippatsu, uraDora);
    }

    private void loadRoomOptions() {
        botsEnabled = plugin.getConfig().getBoolean("bots.enabled", true);
        coachEnabled = plugin.getConfig().getBoolean("coach.enabled", true);
        coachRankDisabled = plugin.getConfig().getBoolean("coach.rankDisabled", false);
        botDelayTicks = Math.max(1, plugin.getConfig().getInt("bots.delayTicks", 10));
    }

    boolean isCoachAllowed() {
        return roomMode && coachEnabled && !coachRankDisabled;
    }

    private void disableCoachForAll(String reason) {
        if (coachPlayers.isEmpty()) {
            return;
        }
        for (UUID playerId : new ArrayList<>(coachPlayers)) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && reason != null && !reason.isBlank()) {
                player.sendMessage(reason);
            }
        }
        coachPlayers.clear();
        coachAdviceCache.clear();
        clearCoachOverlays();
    }

    private boolean isAiDebug() {
        return plugin.getConfig().getBoolean("ai.debug", false);
    }

    private SeatWind loadMaxRoundWind() {
        String configured = plugin.getConfig().getString("round.length", null);
        SeatWind parsed = parseRoundLength(configured);
        if (parsed != null) {
            return parsed;
        }
        int maxHands = plugin.getConfig().getInt("round.maxHands", 8);
        if (maxHands <= 4) {
            return SeatWind.EAST;
        }
        if (maxHands <= 8) {
            return SeatWind.SOUTH;
        }
        if (maxHands <= 12) {
            return SeatWind.WEST;
        }
        return SeatWind.NORTH;
    }

    private SeatWind parseRoundLength(String value) {
        if (value == null) {
            return null;
        }
        String key = value.trim().toUpperCase(Locale.ROOT);
        switch (key) {
            case "EAST":
            case "TONPUU":
                return SeatWind.EAST;
            case "SOUTH":
            case "HANCHAN":
                return SeatWind.SOUTH;
            case "WEST":
                return SeatWind.WEST;
            case "NORTH":
                return SeatWind.NORTH;
            default:
                return null;
        }
    }

    private void cacheState() {
        playerStates = new HashMap<>();
        for (UUID playerId : players) {
            PlayerState state = engine.getPlayerState(playerId);
            if (state != null) {
                playerStates.put(playerId, state);
            }
        }
        roundState = engine.getRoundState();
    }

    DialogAction dialogAction(UUID targetId, Consumer<Player> action) {
        return dialogs.dialogAction(targetId, action);
    }

    String resolveName(UUID playerId) {
        if (playerId == null) {
            return "-";
        }
        String cached = displayNameCache.get(playerId);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        BotProfile bot = bots.get(playerId);
        if (bot != null) {
            String name = bot.getName();
            if (name != null && !name.isBlank()) {
                displayNameCache.put(playerId, name);
                return name;
            }
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            String name = player.getName();
            displayNameCache.put(playerId, name);
            return name;
        }
        return playerId.toString();
    }

    void broadcast(String message) {
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
            }
        }
        for (UUID playerId : spectators) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private void callEvent(Event event) {
        Bukkit.getPluginManager().callEvent(event);
    }

    private void broadcastDoraIndicators() {
        if (engine == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("코치: 추천 버림패 ");
        List<Tile> indicators = engine.getDoraIndicators();
        for (int i = 0; i < indicators.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(indicators.get(i).getId().toDisplayString());
        }
        broadcast(sb.toString());
        worldUi.updateDora(this);
    }

    private int loadTurnByoyomiSeconds() {
        int seconds = plugin.getConfig().getInt("timers.turnByoyomiSeconds", 5);
        return Math.max(0, seconds);
    }

    private int loadTurnExtraSeconds() {
        int seconds = plugin.getConfig().getInt("timers.turnExtraSeconds", 20);
        return Math.max(0, seconds);
    }

    private void resetTurnExtraSeconds() {
        int extra = loadTurnExtraSeconds();
        turnExtraSeconds.clear();
        for (UUID playerId : players) {
            turnExtraSeconds.put(playerId, extra);
        }
    }

    private int getTurnExtraSeconds(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        Integer value = turnExtraSeconds.get(playerId);
        if (value == null) {
            value = loadTurnExtraSeconds();
            turnExtraSeconds.put(playerId, value);
        }
        return value;
    }

    private void startTurnTimer(UUID playerId) {
        cancelTurnTimer();
        if (engine == null || playerId == null || isBot(playerId)) {
            return;
        }
        if (engine.getState() != GameState.TURN_DISCARD) {
            return;
        }
        int byoyomiSeconds = loadTurnByoyomiSeconds();
        int extraSeconds = getTurnExtraSeconds(playerId);
        int totalSeconds = byoyomiSeconds + extraSeconds;
        if (totalSeconds <= 0) {
            return;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            return;
        }
        turnByoyomiRemaining = byoyomiSeconds;
        turnSecondsRemaining = totalSeconds;
        turnTimerPlayer = playerId;
        turnBossBar = Bukkit.createBossBar(buildTurnBossBarTitle(byoyomiSeconds, extraSeconds),
                BarColor.GREEN, BarStyle.SOLID);
        turnBossBar.addPlayer(player);
        turnBossBar.setProgress(1.0);
        turnBossBar.setVisible(true);
        turnBossBarTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int byoyomiRemaining = byoyomiSeconds;
            private int extraRemaining = extraSeconds;

            @Override
            public void run() {
                if (byoyomiRemaining > 0) {
                    byoyomiRemaining--;
                } else if (extraRemaining > 0) {
                    extraRemaining--;
                    turnExtraSeconds.put(playerId, extraRemaining);
                } else {
                    cancelTurnTimer();
                    queue.enqueue(() -> forceRandomDiscard(playerId));
                    return;
                }
                turnByoyomiRemaining = byoyomiRemaining;
                turnSecondsRemaining = byoyomiRemaining + extraRemaining;
                updateTurnBossBar(byoyomiRemaining, extraRemaining, totalSeconds);
                updateWorldUiActions(-1);
            }
        }, 20L, 20L);
    }

    private void updateTurnBossBar(int byoyomiRemaining, int extraRemaining, int total) {
        if (turnBossBar == null) {
            return;
        }
        turnBossBar.setTitle(buildTurnBossBarTitle(byoyomiRemaining, extraRemaining));
        int remaining = Math.max(0, byoyomiRemaining + extraRemaining);
        if (total > 0) {
            turnBossBar.setProgress(Math.max(0.0, Math.min(1.0, remaining / (double) total)));
        } else {
            turnBossBar.setProgress(0.0);
        }
    }

    private String buildTurnBossBarTitle(int byoyomiRemaining, int extraRemaining) {
        if (extraRemaining > 0) {
            return "내 차례: 기본 " + Math.max(0, byoyomiRemaining) + "초 + 추가 " + extraRemaining + "초";
        }
        return "내 차례: " + Math.max(0, byoyomiRemaining) + "초";
    }

    private void cancelTurnTimer() {
        turnSecondsRemaining = -1;
        turnByoyomiRemaining = -1;
        turnTimerPlayer = null;
        if (turnBossBarTask != null) {
            turnBossBarTask.cancel();
            turnBossBarTask = null;
        }
        if (turnBossBar != null) {
            turnBossBar.removeAll();
            turnBossBar.setVisible(false);
            turnBossBar = null;
        }
    }

    private void scheduleAutoNextHand() {
        cancelAutoNextHand();
        int seconds = plugin.getConfig().getInt("timers.nextHandDelaySeconds", 5);
        if (seconds <= 0) {
            startNextHand();
            return;
        }
        broadcast("다음 국을 준비합니다 (" + seconds + "초)");
        nextHandTask = Bukkit.getScheduler().runTaskLater(plugin,
                () -> queue.enqueue(() -> {
                    nextHandTask = null;
                    startNextHand();
                }),
                seconds * 20L);
    }

    private void cancelAutoNextHand() {
        if (nextHandTask != null) {
            nextHandTask.cancel();
            nextHandTask = null;
        }
    }

    private void startCallBossBar(int seconds) {
        clearCallBossBar();
        callSecondsRemaining = seconds;
        if (engine == null || engine.getState() != GameState.CALL_WINDOW) {
            return;
        }
        List<Player> targets = new ArrayList<>();
        for (UUID playerId : players) {
            if (playerId.equals(engine.getLastDiscarder())) {
                continue;
            }
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            List<String> options = resolveCallOptions(playerId, player);
            if (options.isEmpty()) {
                continue;
            }
            targets.add(player);
        }
        if (targets.isEmpty()) {
            return;
        }
        callBossBar = Bukkit.createBossBar("행동 선택: " + seconds + "초", BarColor.YELLOW, BarStyle.SOLID);
        for (Player player : targets) {
            callBossBar.addPlayer(player);
        }
        callBossBar.setProgress(1.0);
        callBossBar.setVisible(true);
        callBossBarTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int remaining = seconds;

            @Override
            public void run() {
                remaining--;
                if (remaining <= 0) {
                    clearCallBossBar();
                    return;
                }
                callSecondsRemaining = remaining;
                callBossBar.setTitle("행동 선택: " + remaining + "초");
                callBossBar.setProgress(Math.max(0.0, Math.min(1.0, remaining / (double) seconds)));
                updateWorldUiActions(remaining);
                dialogs.updateCallPopups(remaining);
            }
        }, 20L, 20L);
    }

    private void clearCallBossBar() {
        callSecondsRemaining = -1;
        if (callBossBarTask != null) {
            callBossBarTask.cancel();
            callBossBarTask = null;
        }
        if (callBossBar != null) {
            callBossBar.removeAll();
            callBossBar.setVisible(false);
            callBossBar = null;
        }
    }

    private void ensureWorldUi() {
        if (!plugin.getConfig().getBoolean("ui.preferWorld", false)) {
            return;
        }
        if (worldUi.isSpawned()) {
            return;
        }
        if (players.isEmpty()) {
            return;
        }
        Player anchorPlayer = null;
        for (UUID playerId : players) {
            Player candidate = plugin.getServer().getPlayer(playerId);
            if (candidate != null) {
                anchorPlayer = candidate;
                break;
            }
        }
        if (anchorPlayer == null) {
            return;
        }
        worldUi.spawn(anchorPlayer.getLocation());
    }

    private void updateWorldUi() {
        if (!worldUi.isSpawned()) {
            return;
        }
        worldUi.updateBoard(this);
        worldUi.updateDora(this);
        worldUi.updateDiscards(this);
        worldUi.updateActionButtons(this, -1);
    }

    private void updateWorldUiActions(int callSecondsRemaining) {
        if (!worldUi.isSpawned()) {
            return;
        }
        worldUi.updateActionButtons(this, callSecondsRemaining);
    }

    static final class HandResult {
        final UUID winnerId;
        final UUID discarderId;
        final boolean tsumo;
        final ScoreResult score;
        final List<UUID> tenpaiPlayers;
        final Map<UUID, Integer> pointDeltas;
        final Map<UUID, Integer> pointsAfter;
        final int honbaApplied;
        final int riichiPotApplied;
        final boolean gameOver;
        final RoundState nextRound;

        private HandResult(UUID winnerId, UUID discarderId, boolean tsumo, ScoreResult score, List<UUID> tenpaiPlayers,
                           Map<UUID, Integer> pointDeltas, Map<UUID, Integer> pointsAfter,
                           int honbaApplied, int riichiPotApplied, boolean gameOver, RoundState nextRound) {
            this.winnerId = winnerId;
            this.discarderId = discarderId;
            this.tsumo = tsumo;
            this.score = score;
            this.tenpaiPlayers = tenpaiPlayers == null ? List.of() : List.copyOf(tenpaiPlayers);
            this.pointDeltas = pointDeltas == null ? Map.of() : Map.copyOf(pointDeltas);
            this.pointsAfter = pointsAfter == null ? Map.of() : Map.copyOf(pointsAfter);
            this.honbaApplied = honbaApplied;
            this.riichiPotApplied = riichiPotApplied;
            this.gameOver = gameOver;
            this.nextRound = nextRound;
        }
    }

    static final class CallOption {
        private final CallType type;
        private final int chiIndex;
        private final List<Tile> tiles;

        private CallOption(CallType type, int chiIndex, List<Tile> tiles) {
            this.type = type;
            this.chiIndex = chiIndex;
            this.tiles = tiles == null ? List.of() : List.copyOf(tiles);
        }

           CallType getType() {
            return type;
        }

           int getChiIndex() {
            return chiIndex;
        }

           List<Tile> getTiles() {
            return tiles;
        }
    }
}















