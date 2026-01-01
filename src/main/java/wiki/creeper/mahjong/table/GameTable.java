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
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
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
import wiki.creeper.mahjong.ui.UiManager;
import wiki.creeper.mahjong.ui.WorldUiManager;
import wiki.creeper.mahjong.storage.GameEvent;
import wiki.creeper.mahjong.storage.GameEventLogger;
import wiki.creeper.mahjong.storage.GameEventType;

public class GameTable {

    private static final int MAX_PLAYERS = 4;
    private static final int COACH_OVERLAY_SECONDS = 6;

    private final UUID id = UUID.randomUUID();
    private final JavaPlugin plugin;
    private final GameQueue queue = new GameQueue();
    private final List<UUID> players = new ArrayList<>(MAX_PLAYERS);
    private final Map<UUID, HandInventory> handInventories = new HashMap<>();
    private final UiManager uiManager;
    private final WorldUiManager worldUi;
    private final GameEventLogger eventLogger = new GameEventLogger();
    private final Set<UUID> furitenNotified = new HashSet<>();
    private final Set<UUID> callDialogPlayers = new HashSet<>();
    private final Set<UUID> readyPlayers = new HashSet<>();
    private final Map<UUID, BotProfile> bots = new HashMap<>();
    private final Map<UUID, BukkitTask> botTurnTasks = new HashMap<>();
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
    private BossBar roomBossBar;
    private BukkitTask botCallTask;
    private int lastLoggedDrawSequence;
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

    public String getStatusLine() {
        StringBuilder sb = new StringBuilder();
        sb.append("Table ").append(id).append(" state=").append(getState());
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

    private boolean isBot(UUID playerId) {
        return playerId != null && bots.containsKey(playerId);
    }

    public GameRules getRulesSnapshot() {
        if (rules != null) {
            return rules;
        }
        return loadRules();
    }

    public List<String> getRoomStatusLines() {
        List<String> lines = new ArrayList<>();
        String code = roomCode == null ? "-" : roomCode;
        String hostName = hostId == null ? "-" : resolveName(hostId);
        lines.add("Room " + code + " host=" + hostName + " players=" + players.size() + "/" + MAX_PLAYERS);
        lines.add("Seats: " + seatAssignments.size() + "/" + MAX_PLAYERS);
        lines.add("Ready: " + getReadyCount() + "/" + players.size());
        lines.add("Rules: " + describeRules(getRulesSnapshot()));
        for (UUID playerId : players) {
            String state = isBot(playerId) ? "BOT" : (readyPlayers.contains(playerId) ? "READY" : "WAIT");
            String seat = seatLabel(playerSeats.get(playerId));
            lines.add("- " + resolveName(playerId) + " [" + seat + "/" + state + "]");
        }
        return lines;
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
            player.sendMessage("레디하기 전에 좌석을 선택해 주세요.");
            return false;
        }
        boolean ready = readyPlayers.contains(playerId);
        if (ready) {
            readyPlayers.remove(playerId);
        } else {
            readyPlayers.add(playerId);
        }
        String state = ready ? "준비 해제" : "준비 완료";
        broadcast(resolveName(playerId) + " " + state + " (" + readyPlayers.size() + "/" + players.size() + ").");
        updateRoomLobbyUi();
        return true;
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
                ippatsu = value != null ? value : !ippatsu;
                break;
            case "uradora":
            case "ura":
                ura = value != null ? value : !ura;
                break;
            case "bots":
            case "bot":
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
            disableCoachForAll("이 방에서는 코치가 비활성화되어 있어요.");
        }
        broadcastRoomRules();
        return true;
    }

    public void showRoomRules(Player player) {
        if (player == null) {
            return;
        }
        if (!roomMode) {
            player.sendMessage("이 테이블은 방 모드가 아니에요.");
            return;
        }
        if (!isHost(player.getUniqueId())) {
            player.sendMessage("방장만 룰을 변경할 수 있어요.");
            return;
        }
        if (getState() != GameState.LOBBY) {
            player.sendMessage("게임 시작 후에는 룰을 바꿀 수 없어요.");
            return;
        }
        if (dialogsEnabled()) {
            showRoomRulesDialog(player);
            return;
        }
        sendRoomRulesSummary(player);
    }

    public void showRoomLobby(Player player) {
        if (player == null) {
            return;
        }
        if (!roomMode) {
            player.sendMessage("이 테이블은 방 모드가 아니에요.");
            return;
        }
        if (getState() != GameState.LOBBY) {
            player.sendMessage("게임 시작 후에는 로비 화면을 열 수 없어요.");
            return;
        }
        if (dialogsEnabled()) {
            showRoomLobbyDialog(player);
            return;
        }
        for (String line : getRoomStatusLines()) {
            player.sendMessage(line);
        }
    }

    public boolean addPlayer(Player player) {
        if (getState() != GameState.LOBBY || players.size() >= MAX_PLAYERS) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (players.contains(playerId)) {
            return false;
        }
        boolean added = players.add(playerId);
        if (added) {
            readyPlayers.remove(playerId);
            if (roomMode) {
                broadcast(resolveName(playerId) + " 님이 방에 입장했어요 (" + players.size() + "/" + MAX_PLAYERS + ").");
                autoAssignSeat(playerId);
                updateRoomLobbyUi();
            }
        }
        return added;
    }

    public boolean removePlayer(Player player) {
        UUID playerId = player.getUniqueId();
        boolean removed = players.remove(playerId);
        if (!removed) {
            return false;
        }
        readyPlayers.remove(playerId);
        coachPlayers.remove(playerId);
        coachAdviceCache.remove(playerId);
        clearCoachOverlay(playerId);
        cancelReplay(playerId);
        if (roomMode) {
            broadcast(resolveName(playerId) + " 님이 방에서 나갔어요 (" + players.size() + "/" + MAX_PLAYERS + ").");
            clearSeat(playerId, true);
            if (playerId.equals(hostId)) {
                hostId = resolveNextHost();
                if (hostId != null) {
                    broadcast("새 방장: " + resolveName(hostId));
                    if (!playerSeats.containsKey(hostId)) {
                        autoAssignSeat(hostId);
                    }
                }
            }
            updateRoomLobbyUi();
        }
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
        players.add(botId);
        if (roomMode) {
            broadcast(resolveName(botId) + " 님이 방에 입장했어요 (" + players.size() + "/" + MAX_PLAYERS + ").");
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
        readyPlayers.remove(botId);
        coachPlayers.remove(botId);
        coachAdviceCache.remove(botId);
        clearCoachOverlay(botId);
        cancelBotTurn(botId);
        clearSeat(botId, false);
        if (botId.equals(hostId)) {
            hostId = resolveNextHost();
        }
        if (roomMode) {
            broadcast(name + " 님이 방에서 나갔어요 (" + players.size() + "/" + MAX_PLAYERS + ").");
        }
        if (updateUi) {
            updateRoomLobbyUi();
        }
    }

    private String buildBotName(BotDifficulty difficulty) {
        botSequence++;
        return "Bot-" + difficulty.name() + "-" + botSequence;
    }

    private boolean hasBotDifficulty(BotDifficulty difficulty) {
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
            player.sendMessage("코치는 방에서만 사용할 수 있어요.");
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (!players.contains(playerId)) {
            player.sendMessage("이 방에 참여 중이 아니에요.");
            return false;
        }
        if (isBot(playerId)) {
            player.sendMessage("봇은 코치를 사용할 수 없어요.");
            return false;
        }
        if (!isCoachAllowed()) {
            if (coachRankDisabled) {
                player.sendMessage("랭크 룸에서는 코치를 사용할 수 없어요.");
            } else {
                player.sendMessage("이 방에서는 코치가 비활성화되어 있어요.");
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
        logGameStart(seed);
        logHandStart();
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
        lastLoggedDrawSequence = 0;
        cancelAllReplays();
        cancelAllBotTurns();
        cancelBotCalls();
        coachAdviceCache.clear();
        logHandStart();
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
            if (player != null) {
                player.sendMessage("다음 핸드를 시작할 수 없어요. 현재 핸드가 아직 끝나지 않았어요.");
            }
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
                player.sendMessage("리플레이가 끝났어요.");
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
        if (callTask != null) {
            callTask.cancel();
            callTask = null;
        }
        clearCallBossBar();
        clearCallDialogs();
        clearRoomBossBar();
        cancelAllReplays();
        cancelAllBotTurns();
        cancelBotCalls();
        coachAdviceCache.clear();
        clearCoachOverlays();
        worldUi.remove();
    }

    private void resetToLobby() {
        engine = null;
        state = GameState.LOBBY;
        playerStates = null;
        roundState = null;
        furitenNotified.clear();
        callDialogPlayers.clear();
        cancelAllBotTurns();
        cancelBotCalls();
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
        if (!shouldConfirmDiscard()) {
            handleDiscardInternal(player, item);
            return;
        }
        if (engine == null) {
            player.sendMessage("게임이 아직 시작되지 않았어요.");
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!playerId.equals(engine.getActivePlayer())) {
            player.sendMessage("지금은 당신 차례가 아니에요.");
            return;
        }
        PlayerState state = engine.getPlayerState(playerId);
        if (state == null) {
            player.sendMessage("플레이어 상태를 찾을 수 없어요.");
            return;
        }
        Optional<Tile> tile = uiManager.readTile(item, state);
        if (tile.isEmpty()) {
            player.sendMessage("선택한 타일이 유효하지 않아요.");
            return;
        }
        if (!engine.canDiscard(playerId, tile.get())) {
            player.sendMessage("이 타일은 버릴 수 없어요.");
            return;
        }
        showDiscardConfirmDialog(player, tile.get(), item);
    }

    private void handleDiscardInternal(Player player, ItemStack item) {
        if (engine == null) {
            player.sendMessage("게임이 아직 시작되지 않았어요.");
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!playerId.equals(engine.getActivePlayer())) {
            player.sendMessage("지금은 당신 차례가 아니에요.");
            return;
        }
        PlayerState state = engine.getPlayerState(playerId);
        if (state == null) {
            player.sendMessage("플레이어 상태를 찾을 수 없어요.");
            return;
        }
        Optional<Tile> tile = uiManager.readTile(item, state);
        if (tile.isEmpty()) {
            player.sendMessage("선택한 타일이 유효하지 않아요.");
            return;
        }
        if (!engine.discard(playerId, tile.get())) {
            player.sendMessage("이 타일은 버릴 수 없어요.");
            return;
        }
        closeDialog(player);
        eventLogger.record(new GameEvent(id, playerId, GameEventType.DISCARD, buildTilePayload(tile.get())));
        lastAction = "DISCARD";
        updateHand(playerId, false);
        broadcast(player.getName() + " 님이 " + tile.get().getId().toShortString() + "을(를) 버렸어요.");
        notifyCoachAfterDiscard(playerId, tile.get());
        updateWorldUi();
        int seconds = plugin.getConfig().getInt("timers.callWindowSeconds", 5);
        broadcast("울기 창 열림 (" + seconds + "s). /mj ron|pon|chi|kan 사용");
        notifyCallOptions(seconds);
        scheduleCallResolution();
        updateWorldUiActions(seconds);
    }

    private void requestRonInternal(Player player) {
        if (engine == null) {
            player.sendMessage("게임이 아직 시작되지 않았어요.");
            return;
        }
        if (engine.getState() != GameState.CALL_WINDOW) {
            player.sendMessage("현재 울기(콜) 창이 열려 있지 않아요.");
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!engine.canRon(playerId)) {
            PlayerState state = engine.getPlayerState(playerId);
            if (state != null && state.getHand().isFuriten()) {
                player.sendMessage("후리텐 상태라 론할 수 없어요.");
            } else {
                player.sendMessage("론이 가능한 상태가 아니에요.");
            }
            return;
        }
        Tile lastDiscard = engine.getLastDiscard();
        if (lastDiscard == null) {
            player.sendMessage("버림패가 없어요.");
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
            player.sendMessage("현재 울기(콜) 창이 열려 있지 않아요.");
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
            player.sendMessage("현재 울기(콜) 창이 열려 있지 않아요.");
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
        if (!dialogsEnabled()) {
            player.sendMessage("여러 치 선택지가 있어요. /mj chi <1-" + chiCount + ">으로 선택해 주세요.");
            return;
        }
        List<CallOption> options = resolveChiChoices(playerId);
        if (options.isEmpty()) {
            player.sendMessage("치가 가능한 상태가 아니에요.");
            return;
        }
        showChiDialog(player, options);
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
            player.sendMessage("지금은 당신 차례가 아니에요.");
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
            } else if (dialogsEnabled()) {
                showSelfKanDialog(player, options);
                return;
            } else {
                player.sendMessage("여러 깡 선택지가 있어요. /mj kan <1-" + options.size() + ">으로 선택해 주세요.");
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
        closeDialog(player);
        KanOption option = options.get(optionIndex - 1);
        eventLogger.record(new GameEvent(id, playerId, GameEventType.CALL, buildKanPayload(option)));
        broadcast(player.getName() + " declared " + describeSelfKan(option) + ".");
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
        closeDialog(player);
        eventLogger.record(new GameEvent(id, playerId, GameEventType.RIICHI, "state=declare"));
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
        closeDialog(player);
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
            player.sendMessage("이미 게임이 시작되었어요.");
            return;
        }
        UUID playerId = player.getUniqueId();
        if (roomMode) {
            if (!isHost(playerId)) {
                player.sendMessage("방장만 게임을 시작할 수 있어요.");
                return;
            }
            if (!areAllReady()) {
                player.sendMessage("시작할 수 없어요. 모두 레디해야 해요 (" + readyPlayers.size() + "/" + players.size() + ").");
                return;
            }
        }
        if (start()) {
            closeDialogsForAll();
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
            player.sendMessage("현재 울기(콜) 창이 열려 있지 않아요.");
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
            player.sendMessage(callName(type) + "을(를) 할 수 없는 상태예요.");
            return;
        }
        engine.addCallRequest(request.get());
        player.sendMessage(callName(type) + "을(를) 선언했어요.");
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
        clearCallPopups();
        clearCallDialogs();
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
        if (engine.getState() == GameState.HAND_END) {
            endHand();
            return;
        }
        UUID active = engine.getActivePlayer();
        if (resolved != null) {
            eventLogger.record(new GameEvent(id, resolved.getPlayerId(), GameEventType.CALL,
                    buildCallPayload(resolved, lastDiscard, lastDiscarder)));
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
        clearCallDialogs();
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
            String suffix = engine.isTsumoWin() ? " (tsumo)" : "";
            eventLogger.record(new GameEvent(id, winner, GameEventType.WIN, buildWinPayload(engine.isTsumoWin(), discarder)));
            lastAction = "WIN";
            broadcast("승자: " + name + suffix + ".");
            score = settlePoints(winner);
            broadcastScore(score);
            broadcastPoints();
            updateRoundAfterHand(winner, null);
            updateWorldUi();
        } else {
            tenpaiPlayers = resolveTenpaiPlayers();
            eventLogger.record(new GameEvent(id, null, GameEventType.RYUUKYOKU, buildRyuukyokuPayload(tenpaiPlayers)));
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
        updateWorldHandResult(result);
        if (dialogsEnabled()) {
            showHandResultDialogs(result);
        }
        if (result.gameOver) {
            logGameEnd();
            broadcast("게임이 종료됐어요.");
            resetToLobby();
        } else {
            broadcast("계속하려면 /mj nexthand 를 사용해 주세요.");
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
        if (player != null && openIfNeeded) {
            if (player.getOpenInventory().getTopInventory().getHolder() != holder) {
                player.openInventory(holder.getInventory());
            }
        }
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
            player.sendMessage("당신 차례예요. 버릴 타일을 선택해 주세요.");
            if (engine != null && engine.canDeclareRiichi(playerId)) {
                player.sendMessage("리치 가능: /mj riichi");
            }
            if (engine != null && engine.canDeclareKan(playerId)) {
                player.sendMessage("깡 가능: /mj kan");
            }
            if (engine != null && engine.canTsumo(playerId)) {
                player.sendMessage("쯔모 가능: /mj tsumo");
            }
            if (engine != null && dialogsEnabled()) {
                showActionDialog(player,
                        engine.canDeclareRiichi(playerId),
                        engine.canTsumo(playerId),
                        engine.canDeclareKan(playerId));
            }
            sendCoachAdvice(playerId);
        }
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
            player.sendMessage("코치: 추천 1순위는 " + best.getDiscard().toShortString()
                    + " (샹텐 " + best.getShanten() + ", 유효패 " + best.getUkeire() + ").");
        }
        showCoachMissOverlay(playerId, best);
    }

    private List<String> formatCoachAdvice(CoachAdvice advice) {
        if (advice == null || advice.getSuggestions().isEmpty()) {
            return List.of("코치: 추천을 만들 수 없어요.");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("코치: 추천 버림패 ");
        for (int i = 0; i < advice.getSuggestions().size(); i++) {
            CoachSuggestion suggestion = advice.getSuggestions().get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(suggestion.getDiscard().toShortString())
                    .append(" (샹텐 ").append(suggestion.getShanten())
                    .append(", 유효패 ").append(suggestion.getUkeire()).append(")");
        }
        List<String> lines = new ArrayList<>();
        lines.add(sb.toString());
        if (advice.isRiichiAvailable()) {
            String recommend = advice.isRiichiRecommended() ? "리치" : "유지";
            lines.add("코치: 리치 기대값 ~" + advice.getRiichiValue()
                    + " / 유지 기대값 ~" + advice.getKeepValue() + " -> " + recommend);
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
        sb.append("코치: ")
                .append(best.getDiscard().toShortString())
                .append(" (샹텐 ").append(best.getShanten())
                .append(", 유효패 ").append(best.getUkeire()).append(")");
        if (advice.isRiichiAvailable()) {
            sb.append(advice.isRiichiRecommended() ? " 리치" : " 유지");
        }
        return sb.toString();
    }

    private String buildCoachMissText(CoachSuggestion best) {
        return "코치: 추천 1순위 " + best.getDiscard().toShortString()
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
        eventLogger.record(new GameEvent(id, playerId, GameEventType.DISCARD, buildTilePayload(tile)));
        lastAction = "DISCARD";
        updateHand(playerId, false);
        broadcast(resolveName(playerId) + " 님이 " + tile.getId().toShortString() + "을(를) 버렸어요.");
        updateWorldUi();
        int seconds = plugin.getConfig().getInt("timers.callWindowSeconds", 5);
        broadcast("울기 창 열림 (" + seconds + "s). /mj ron|pon|chi|kan 사용");
        notifyCallOptions(seconds);
        scheduleCallResolution();
        updateWorldUiActions(seconds);
        return true;
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
        if (!score.getYaku().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (wiki.creeper.mahjong.game.Yaku item : score.getYaku()) {
                names.add(item.getDisplayName());
            }
            broadcast("역: " + String.join(", ", names));
        }
        broadcast("점수: " + score.summary());
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

    private void showHandResultDialogs(HandResult result) {
        if (result == null) {
            return;
        }
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                showHandResultDialog(player, result);
            }
        }
    }

    private void showHandResultDialog(Player player, HandResult result) {
        if (player == null || result == null) {
            return;
        }
        List<DialogBody> body = new ArrayList<>();
        for (Component line : buildHandResultLines(result)) {
            body.add(DialogBody.plainMessage(line));
        }
        List<ActionButton> actions = new ArrayList<>();
        UUID targetId = player.getUniqueId();
        boolean canNext = !result.gameOver && engine != null && engine.getState() == GameState.HAND_END
                && players.size() == MAX_PLAYERS;
        if (canNext) {
            actions.add(ActionButton.create(
                    Component.text("다음 핸드", NamedTextColor.GREEN),
                    Component.text("다음 핸드를 시작해요", NamedTextColor.DARK_GRAY),
                    100,
                    dialogAction(targetId, this::requestNextHand)
            ));
        }
        if (result.gameOver && roomMode) {
            actions.add(ActionButton.create(
                    Component.text("로비", NamedTextColor.AQUA),
                    Component.text("로비로 돌아가기", NamedTextColor.DARK_GRAY),
                    80,
                    dialogAction(targetId, this::showRoomLobby)
            ));
        }
        actions.add(ActionButton.create(
                Component.text("닫기", NamedTextColor.DARK_GRAY),
                Component.text("창 닫기", NamedTextColor.DARK_GRAY),
                10,
                null
        ));
        String title = result.gameOver ? "게임 결과" : "핸드 결과";
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title, NamedTextColor.GOLD))
                        .body(body)
                        .build())
                .type(DialogType.multiAction(actions).build())
        );
        player.showDialog(dialog);
    }

    private List<Component> buildHandResultLines(HandResult result) {
        List<Component> lines = new ArrayList<>();
        if (result.winnerId != null) {
            String winnerName = resolveName(result.winnerId);
            String winType = result.tsumo ? "쯔모" : "론";
            lines.add(Component.text("승자: " + winnerName + " (" + winType + ")", NamedTextColor.GOLD));
            if (!result.tsumo && result.discarderId != null) {
                lines.add(Component.text("방총자: " + resolveName(result.discarderId), NamedTextColor.GRAY));
            }
            if (result.score != null) {
                lines.add(Component.text("점수: " + result.score.summary(), NamedTextColor.WHITE));
                if (!result.score.getYaku().isEmpty()) {
                    List<String> names = new ArrayList<>();
                    for (wiki.creeper.mahjong.game.Yaku item : result.score.getYaku()) {
                        names.add(item.getDisplayName());
                    }
                    lines.add(Component.text("역: " + String.join(", ", names), NamedTextColor.AQUA));
                }
                String paymentLine = buildPaymentLine(result);
                if (!paymentLine.isEmpty()) {
                    lines.add(Component.text(paymentLine, NamedTextColor.GRAY));
                }
            }
            if (result.riichiPotApplied > 0) {
                int riichiStick = plugin.getConfig().getInt("scoring.riichiStick", 1000);
                int bonus = result.riichiPotApplied * riichiStick;
                lines.add(Component.text("공탁: " + result.riichiPotApplied + " (" + bonus + " 점)", NamedTextColor.GRAY));
            }
        } else {
            lines.add(Component.text("유국: 류국", NamedTextColor.GOLD));
            if (result.tenpaiPlayers == null || result.tenpaiPlayers.isEmpty()) {
                lines.add(Component.text("텐파이: 없음", NamedTextColor.GRAY));
            } else {
                List<String> names = new ArrayList<>();
                for (UUID playerId : result.tenpaiPlayers) {
                    names.add(resolveName(playerId));
                }
                lines.add(Component.text("텐파이: " + String.join(", ", names), NamedTextColor.GRAY));
            }
        }
        lines.add(Component.text("점수:", NamedTextColor.YELLOW));
        for (UUID playerId : players) {
            int delta = result.pointDeltas.getOrDefault(playerId, 0);
            int points = result.pointsAfter.getOrDefault(playerId, 0);
            NamedTextColor color;
            if (delta > 0) {
                color = NamedTextColor.GREEN;
            } else if (delta < 0) {
                color = NamedTextColor.RED;
            } else {
                color = NamedTextColor.GRAY;
            }
            lines.add(Component.text("- " + resolveName(playerId) + ": " + formatDelta(delta) + " => " + points, color));
        }
        if (result.gameOver) {
            lines.add(Component.text("게임이 종료됐어요.", NamedTextColor.RED));
        } else if (result.nextRound != null) {
            String nextLine = formatNextRoundLine(result.nextRound);
            if (!nextLine.isEmpty()) {
                lines.add(Component.text(nextLine, NamedTextColor.GRAY));
            }
        }
        return lines;
    }

    private void updateWorldHandResult(HandResult result) {
        if (!worldUi.isSpawned()) {
            return;
        }
        if (result == null) {
            worldUi.clearHandResult();
            return;
        }
        worldUi.updateHandResult(buildHandResultTextLines(result));
    }

    private List<String> buildHandResultTextLines(HandResult result) {
        List<String> lines = new ArrayList<>();
        if (result.winnerId != null) {
            String winnerName = resolveName(result.winnerId);
            String winType = result.tsumo ? "쯔모" : "론";
            lines.add("승자: " + winnerName + " (" + winType + ")");
            if (!result.tsumo && result.discarderId != null) {
                lines.add("방총자: " + resolveName(result.discarderId));
            }
            if (result.score != null) {
                lines.add("점수: " + result.score.summary());
                if (!result.score.getYaku().isEmpty()) {
                    List<String> names = new ArrayList<>();
                    for (wiki.creeper.mahjong.game.Yaku item : result.score.getYaku()) {
                        names.add(item.getDisplayName());
                    }
                    lines.add("역: " + String.join(", ", names));
                }
                String paymentLine = buildPaymentLine(result);
                if (!paymentLine.isEmpty()) {
                    lines.add(paymentLine);
                }
            }
            if (result.riichiPotApplied > 0) {
                int riichiStick = plugin.getConfig().getInt("scoring.riichiStick", 1000);
                int bonus = result.riichiPotApplied * riichiStick;
                lines.add("공탁: " + result.riichiPotApplied + " (" + bonus + " 점)");
            }
        } else {
            lines.add("유국: 류국");
            if (result.tenpaiPlayers == null || result.tenpaiPlayers.isEmpty()) {
                lines.add("텐파이: 없음");
            } else {
                List<String> names = new ArrayList<>();
                for (UUID playerId : result.tenpaiPlayers) {
                    names.add(resolveName(playerId));
                }
                lines.add("텐파이: " + String.join(", ", names));
            }
        }
        lines.add("점수:");
        for (UUID playerId : players) {
            int delta = result.pointDeltas.getOrDefault(playerId, 0);
            int points = result.pointsAfter.getOrDefault(playerId, 0);
            lines.add("- " + resolveName(playerId) + ": " + formatDelta(delta) + " => " + points);
        }
        if (result.gameOver) {
            lines.add("게임이 종료됐어요.");
        } else if (result.nextRound != null) {
            String nextLine = formatNextRoundLine(result.nextRound);
            if (!nextLine.isEmpty()) {
                lines.add(nextLine);
            }
        }
        return lines;
    }

    private String buildPaymentLine(HandResult result) {
        if (result == null || result.score == null) {
            return "";
        }
        int honba = result.honbaApplied;
        int honbaRonBonus = plugin.getConfig().getInt("scoring.honbaRonBonus", 300);
        int honbaTsumoBonus = plugin.getConfig().getInt("scoring.honbaTsumoBonus", 100);
        String suffix = honba > 0 ? " (honba " + honba + ")" : "";
        if (result.tsumo) {
            int dealerPay = result.score.getTsumoFromDealer() + (honba * honbaTsumoBonus);
            int otherPay = result.score.getTsumoFromOthers() + (honba * honbaTsumoBonus);
            if (result.score.isDealer()) {
                return "Payments: each " + dealerPay + suffix;
            }
            return "Payments: dealer " + dealerPay + ", others " + otherPay + suffix;
        }
        int ronPay = result.score.getRonPayment() + (honba * honbaRonBonus);
        return "Payment: " + ronPay + suffix;
    }

    private String formatNextRoundLine(RoundState round) {
        if (round == null) {
            return "";
        }
        return "Next: " + round.getRoundWind() + " " + round.getKyoku()
                + " (Dealer " + round.getDealerWind() + ", Honba " + round.getHonba() + ")";
    }

    private String formatDelta(int delta) {
        if (delta > 0) {
            return "+" + delta;
        }
        return Integer.toString(delta);
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
        String payload = buildDrawPayload(tile, engine.isLastDrawRinshan());
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
        int remainingSeconds = callWindow
                ? plugin.getConfig().getInt("timers.callWindowSeconds", 5)
                : -1;
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
            broadcast("텐파이: 없음");
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

    private void notifyCallOptions(int callWindowSeconds) {
        if (engine == null || engine.getState() != GameState.CALL_WINDOW) {     
            return;
        }
        callDialogPlayers.clear();
        for (UUID playerId : players) {
            if (playerId.equals(engine.getLastDiscarder())) {
                continue;
            }
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            List<CallOption> choices = resolveCallChoices(playerId);
            if (choices.isEmpty()) {
                continue;
            }
            List<String> options = resolveCallOptions(playerId, player);
            if (!options.isEmpty()) {
                player.sendMessage("가능한 울기: " + String.join(", ", options));
            }
            if (dialogsEnabled()) {
                showCallDialog(player, choices, callWindowSeconds);
                callDialogPlayers.add(playerId);
            } else {
                showCallPopup(player, options, callWindowSeconds);
            }
        }
        scheduleBotCalls(callWindowSeconds);
    }

    private List<String> resolveCallOptions(UUID playerId, Player player) {     
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
            if (chiCount > 1 && !dialogsEnabled()) {
                player.sendMessage("여러 치 선택지가 있어요. /mj chi <1-" + chiCount + ">으로 선택해 주세요.");
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

    private void showCallDialog(Player player, List<CallOption> options, int callWindowSeconds) {
        if (player == null || options == null || options.isEmpty()) {
            return;
        }
        Tile lastDiscard = engine == null ? null : engine.getLastDiscard();
        List<DialogBody> body = new ArrayList<>();
        String lastText = lastDiscard == null ? "-" : lastDiscard.getId().toShortString();
        body.add(DialogBody.plainMessage(Component.text("Last discard: " + lastText, NamedTextColor.GRAY)));
        if (callWindowSeconds >= 0) {
            body.add(DialogBody.plainMessage(Component.text("Remaining: " + callWindowSeconds + "s", NamedTextColor.DARK_GRAY)));
        }
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Call Window", NamedTextColor.GOLD))
                        .body(body)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(buildCallButtons(player, options, lastDiscard, true)).build())
        );
        player.showDialog(dialog);
    }

    private void showChiDialog(Player player, List<CallOption> options) {       
        if (player == null || options == null || options.isEmpty()) {
            return;
        }
        Tile lastDiscard = engine == null ? null : engine.getLastDiscard();     
        String lastText = lastDiscard == null ? "-" : lastDiscard.getId().toShortString();
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("Select a chi for " + lastText + ".", NamedTextColor.GRAY)));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Chi Options", NamedTextColor.GREEN))
                        .body(body)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(buildCallButtons(player, options, lastDiscard, true)).build())
        );
        player.showDialog(dialog);
    }

    private void showSelfKanDialog(Player player, List<KanOption> options) {
        if (player == null || options == null || options.isEmpty()) {
            return;
        }
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text(
                "Select a kan option.", NamedTextColor.GRAY)));
        UUID targetId = player.getUniqueId();
        List<ActionButton> actions = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            KanOption option = options.get(i);
            int index = i + 1;
            String label = formatKanOptionLabel(option, index);
            actions.add(ActionButton.create(
                    Component.text(label, NamedTextColor.GOLD),
                    Component.text("Declare kan", NamedTextColor.GRAY),
                    180,
                    dialogAction(targetId, clicker -> requestKan(clicker, index))
            ));
        }
        actions.add(ActionButton.create(
                Component.text("Cancel", NamedTextColor.DARK_GRAY),
                Component.text("Close dialog", NamedTextColor.GRAY),
                120,
                null
        ));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Kan Options", NamedTextColor.GOLD))
                        .body(body)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(actions).build())
        );
        player.showDialog(dialog);
    }

    private void showRoomLobbyDialog(Player player) {
        if (player == null) {
            return;
        }
        List<DialogBody> body = new ArrayList<>();
        String code = roomCode == null ? "-" : roomCode;
        String hostName = hostId == null ? "-" : resolveName(hostId);
        body.add(DialogBody.plainMessage(Component.text("Room Code: " + code, NamedTextColor.GRAY)));
        body.add(DialogBody.plainMessage(Component.text("Host: " + hostName, NamedTextColor.GRAY)));
        body.add(DialogBody.plainMessage(Component.text("Players: " + players.size() + "/" + MAX_PLAYERS, NamedTextColor.GRAY)));
        body.add(DialogBody.plainMessage(Component.text("Seats: " + seatAssignments.size() + "/" + MAX_PLAYERS, NamedTextColor.GRAY)));
        body.add(DialogBody.plainMessage(Component.text("Ready: " + getReadyCount() + "/" + players.size(), NamedTextColor.GRAY)));
        body.add(DialogBody.plainMessage(Component.text("Rules: " + describeRules(getRulesSnapshot()), NamedTextColor.GOLD)));
        for (SeatWind seat : SeatWind.values()) {
            UUID occupant = seatAssignments.get(seat);
            String seatName = seatLabel(seat);
            String occupantName = occupant == null ? "Empty" : resolveName(occupant);
            NamedTextColor color = occupant == null ? NamedTextColor.DARK_GRAY : NamedTextColor.WHITE;
            body.add(DialogBody.plainMessage(Component.text(seatName + ": " + occupantName, color)));
        }
        for (UUID playerId : players) {
            boolean ready = readyPlayers.contains(playerId);
            boolean bot = isBot(playerId);
            NamedTextColor color = bot ? NamedTextColor.AQUA : (ready ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY);
            String state = bot ? "BOT" : (ready ? "READY" : "WAIT");
            String seatName = seatLabel(playerSeats.get(playerId));
            String coachTag = coachPlayers.contains(playerId) ? "/COACH" : "";
            body.add(DialogBody.plainMessage(Component.text("- " + resolveName(playerId) + " [" + seatName + "/" + state + coachTag + "]", color)));
        }
        List<ActionButton> actions = new ArrayList<>();
        UUID targetId = player.getUniqueId();
        for (SeatWind seat : SeatWind.values()) {
            UUID occupant = seatAssignments.get(seat);
            boolean mine = occupant != null && occupant.equals(targetId);
            boolean available = occupant == null || mine;
            NamedTextColor color = mine ? NamedTextColor.GREEN : (occupant == null ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY);
            String label = "Seat " + seatLabel(seat) + (mine ? " (You)" : "");
            DialogAction action = available
                    ? dialogAction(targetId, clicker -> {
                        assignSeat(clicker, seat);
                        showRoomLobbyDialog(clicker);
                    })
                    : null;
            actions.add(ActionButton.create(
                    Component.text(label, color),
                    Component.text(occupant == null ? "Choose seat" : (mine ? "Leave seat" : "Seat taken"), NamedTextColor.GRAY),
                    170,
                    action
            ));
        }
        SeatWind mySeat = playerSeats.get(targetId);
        if (mySeat != null) {
            actions.add(ActionButton.create(
                    Component.text("LEAVE SEAT", NamedTextColor.RED),
                    Component.text("Clear seat", NamedTextColor.GRAY),
                    160,
                    dialogAction(targetId, clicker -> {
                        clearSeat(targetId, true);
                        showRoomLobbyDialog(clicker);
                    })
            ));
        }
        boolean isReady = readyPlayers.contains(targetId);
        boolean canReady = playerSeats.containsKey(targetId);
        actions.add(ActionButton.create(
                Component.text(isReady ? "UNREADY" : "READY", isReady ? NamedTextColor.RED : NamedTextColor.GREEN),
                Component.text(canReady ? "Toggle ready" : "Pick a seat first", NamedTextColor.GRAY),
                160,
                canReady ? dialogAction(targetId, clicker -> {
                    toggleReady(clicker);
                    showRoomLobbyDialog(clicker);
                }) : null
        ));
        boolean coachAllowed = isCoachAllowed();
        boolean coachOn = coachPlayers.contains(targetId);
        String coachLabel = coachAllowed ? (coachOn ? "COACH ON" : "COACH OFF") : "COACH LOCKED";
        NamedTextColor coachColor = coachAllowed ? (coachOn ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY) : NamedTextColor.RED;
        String coachHint = coachAllowed ? "Toggle coach" : "Coach disabled in this room";
        DialogAction coachAction = coachAllowed
                ? dialogAction(targetId, clicker -> {
                    setCoach(clicker, !coachOn);
                    showRoomLobbyDialog(clicker);
                })
                : null;
        actions.add(ActionButton.create(
                Component.text(coachLabel, coachColor),
                Component.text(coachHint, NamedTextColor.GRAY),
                160,
                coachAction
        ));
        if (isHost(targetId)) {
            actions.add(ActionButton.create(
                    Component.text("RULES", NamedTextColor.AQUA),
                    Component.text("Edit room rules", NamedTextColor.GRAY),
                    160,
                    dialogAction(targetId, clicker -> showRoomRules(clicker))
            ));
            actions.add(ActionButton.create(
                    Component.text("BOTS", NamedTextColor.LIGHT_PURPLE),
                    Component.text("Manage bots", NamedTextColor.GRAY),
                    160,
                    dialogAction(targetId, clicker -> showBotDialog(clicker))
            ));
            boolean canStart = getState() == GameState.LOBBY && players.size() == MAX_PLAYERS && areAllReady();
            actions.add(ActionButton.create(
                    Component.text(canStart ? "START" : "START (need all ready)", NamedTextColor.GOLD),
                    Component.text("Start the game", NamedTextColor.GRAY),
                    200,
                    canStart ? dialogAction(targetId, clicker -> requestStart(clicker)) : null
            ));
        }
        actions.add(ActionButton.create(
                Component.text("REFRESH", NamedTextColor.GRAY),
                Component.text("Refresh lobby", NamedTextColor.GRAY),
                140,
                dialogAction(targetId, clicker -> showRoomLobbyDialog(clicker))
        ));
        actions.add(ActionButton.create(
                Component.text("Close", NamedTextColor.DARK_GRAY),
                Component.text("Close dialog", NamedTextColor.GRAY),
                120,
                null
        ));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Room Lobby", NamedTextColor.AQUA))
                        .body(body)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(actions).build())
        );
        player.showDialog(dialog);
    }

    private void showRoomRulesDialog(Player player) {
        if (player == null) {
            return;
        }
        GameRules current = getRulesSnapshot();
        List<DialogBody> body = new ArrayList<>();
        String code = roomCode == null ? "-" : roomCode;
        body.add(DialogBody.plainMessage(Component.text("Room Code: " + code, NamedTextColor.GRAY)));
        body.add(DialogBody.plainMessage(Component.text("Rules", NamedTextColor.GOLD)));
        body.add(DialogBody.plainMessage(Component.text(formatRuleLine("Red Dora", current.isRedDoraEnabled()), NamedTextColor.WHITE)));
        body.add(DialogBody.plainMessage(Component.text(formatRuleLine("Open Tanyao", current.isOpenTanyaoEnabled()), NamedTextColor.WHITE)));
        body.add(DialogBody.plainMessage(Component.text(formatRuleLine("Ippatsu", current.isIppatsuEnabled()), NamedTextColor.WHITE)));
        body.add(DialogBody.plainMessage(Component.text(formatRuleLine("Ura Dora", current.isUraDoraEnabled()), NamedTextColor.WHITE)));
        body.add(DialogBody.plainMessage(Component.text("Options", NamedTextColor.GOLD)));
        body.add(DialogBody.plainMessage(Component.text(formatRuleLine("Bots", botsEnabled), NamedTextColor.WHITE)));
        body.add(DialogBody.plainMessage(Component.text(formatRuleLine("Coach", coachEnabled), NamedTextColor.WHITE)));
        body.add(DialogBody.plainMessage(Component.text(formatRuleLine("Coach Rank Lock", coachRankDisabled), NamedTextColor.WHITE)));

        List<ActionButton> actions = new ArrayList<>();
        UUID targetId = player.getUniqueId();
        actions.add(buildRuleToggleButton(targetId, "Red Dora", current.isRedDoraEnabled(), "redDora"));
        actions.add(buildRuleToggleButton(targetId, "Open Tanyao", current.isOpenTanyaoEnabled(), "openTanyao"));
        actions.add(buildRuleToggleButton(targetId, "Ippatsu", current.isIppatsuEnabled(), "ippatsu"));
        actions.add(buildRuleToggleButton(targetId, "Ura Dora", current.isUraDoraEnabled(), "uraDora"));
        actions.add(buildRuleToggleButton(targetId, "Bots", botsEnabled, "bots"));
        actions.add(buildRuleToggleButton(targetId, "Coach", coachEnabled, "coach"));
        actions.add(buildRuleToggleButton(targetId, "Coach Rank", coachRankDisabled, "coachRank"));
        actions.add(buildPresetButton(targetId, "Preset: Default", "default"));
        actions.add(buildPresetButton(targetId, "Preset: Kuitan", "kuitan"));
        actions.add(buildPresetButton(targetId, "Preset: Classic", "classic"));
        actions.add(ActionButton.create(
                Component.text("Lobby", NamedTextColor.GRAY),
                Component.text("Back to lobby", NamedTextColor.GRAY),
                120,
                dialogAction(targetId, clicker -> showRoomLobbyDialog(clicker))
        ));
        actions.add(ActionButton.create(
                Component.text("Close", NamedTextColor.DARK_GRAY),
                Component.text("Close dialog", NamedTextColor.GRAY),
                120,
                null
        ));

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Room Rules", NamedTextColor.AQUA))
                        .body(body)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(actions).build())
        );
        player.showDialog(dialog);
    }

    private void showBotDialog(Player player) {
        if (player == null) {
            return;
        }
        if (!roomMode) {
            player.sendMessage("이 테이블은 방 모드가 아니에요.");
            return;
        }
        if (!isHost(player.getUniqueId())) {
            player.sendMessage("방장만 봇을 관리할 수 있어요.");
            return;
        }
        if (getState() != GameState.LOBBY) {
            player.sendMessage("봇 관리는 로비에서만 가능해요.");
            return;
        }
        if (!dialogsEnabled()) {
            player.sendMessage("봇 관리는 /mj bot add|remove <난이도>를 사용해 주세요.");
            return;
        }
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("봇: " + onOff(botsEnabled), NamedTextColor.GRAY)));
        body.add(DialogBody.plainMessage(Component.text("인원: " + players.size() + "/" + MAX_PLAYERS, NamedTextColor.GRAY)));
        if (bots.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text("봇: 없음", NamedTextColor.DARK_GRAY)));
        } else {
            body.add(DialogBody.plainMessage(Component.text("봇 목록", NamedTextColor.GOLD)));
            for (BotProfile profile : bots.values()) {
                body.add(DialogBody.plainMessage(Component.text("- " + profile.getName() + " (" + profile.getDifficulty() + ")", NamedTextColor.WHITE)));
            }
        }
        List<ActionButton> actions = new ArrayList<>();
        UUID targetId = player.getUniqueId();
        boolean canAdd = botsEnabled && players.size() < MAX_PLAYERS;
        actions.add(buildBotActionButton(targetId, "ADD BEGINNER", BotDifficulty.BEGINNER, canAdd));
        actions.add(buildBotActionButton(targetId, "ADD NORMAL", BotDifficulty.NORMAL, canAdd));
        actions.add(buildBotActionButton(targetId, "ADD HARD", BotDifficulty.HARD, canAdd));
        actions.add(buildBotRemoveButton(targetId, "REMOVE BEGINNER", BotDifficulty.BEGINNER));
        actions.add(buildBotRemoveButton(targetId, "REMOVE NORMAL", BotDifficulty.NORMAL));
        actions.add(buildBotRemoveButton(targetId, "REMOVE HARD", BotDifficulty.HARD));
        actions.add(ActionButton.create(
                Component.text("Lobby", NamedTextColor.GRAY),
                Component.text("Back to lobby", NamedTextColor.GRAY),
                120,
                dialogAction(targetId, clicker -> showRoomLobbyDialog(clicker))
        ));
        actions.add(ActionButton.create(
                Component.text("Close", NamedTextColor.DARK_GRAY),
                Component.text("Close dialog", NamedTextColor.GRAY),
                120,
                null
        ));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Bot Manager", NamedTextColor.LIGHT_PURPLE))
                        .body(body)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(actions).build())
        );
        player.showDialog(dialog);
    }

    private ActionButton buildBotActionButton(UUID targetId, String label, BotDifficulty difficulty, boolean enabled) {
        DialogAction action = enabled ? dialogAction(targetId, clicker -> {
            addBot(difficulty);
            showBotDialog(clicker);
        }) : null;
        NamedTextColor color = enabled ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY;
        String hint = enabled ? "Add bot" : "Bots disabled or full";
        return ActionButton.create(Component.text(label, color), Component.text(hint, NamedTextColor.GRAY), 160, action);
    }

    private ActionButton buildBotRemoveButton(UUID targetId, String label, BotDifficulty difficulty) {
        boolean enabled = hasBotDifficulty(difficulty);
        DialogAction action = enabled ? dialogAction(targetId, clicker -> {
            removeBot(difficulty);
            showBotDialog(clicker);
        }) : null;
        NamedTextColor color = enabled ? NamedTextColor.RED : NamedTextColor.DARK_GRAY;
        String hint = enabled ? "Remove bot" : "No bot";
        return ActionButton.create(Component.text(label, color), Component.text(hint, NamedTextColor.GRAY), 160, action);
    }

    private ActionButton buildRuleToggleButton(UUID targetId, String label, boolean enabled, String ruleKey) {
        NamedTextColor color = enabled ? NamedTextColor.GREEN : NamedTextColor.RED;
        String state = enabled ? "ON" : "OFF";
        DialogAction action = dialogAction(targetId, clicker -> {
            updateRule(ruleKey, null);
            showRoomRulesDialog(clicker);
        });
        return ActionButton.create(
                Component.text(label + " " + state, color),
                Component.text("Toggle " + label, NamedTextColor.GRAY),
                180,
                action
        );
    }

    private ActionButton buildPresetButton(UUID targetId, String label, String presetKey) {
        DialogAction action = dialogAction(targetId, clicker -> {
            applyPreset(presetKey);
            showRoomRulesDialog(clicker);
        });
        return ActionButton.create(
                Component.text(label, NamedTextColor.GOLD),
                Component.text("Apply preset", NamedTextColor.GRAY),
                180,
                action
        );
    }

    private void showActionDialog(Player player, boolean canRiichi, boolean canTsumo, boolean canKan) {
        if (player == null || (!canRiichi && !canTsumo && !canKan)) {
            return;
        }
        List<ActionButton> actions = new ArrayList<>();
        UUID targetId = player.getUniqueId();
        if (canRiichi) {
            actions.add(ActionButton.create(
                    Component.text("RIICHI", NamedTextColor.AQUA),
                    Component.text("Declare riichi", NamedTextColor.GRAY),
                    140,
                    dialogAction(targetId, clicker -> requestRiichi(clicker))
            ));
        }
        if (canKan) {
            actions.add(ActionButton.create(
                    Component.text("KAN", NamedTextColor.GOLD),
                    Component.text("Declare kan", NamedTextColor.GRAY),
                    140,
                    dialogAction(targetId, clicker -> requestKan(clicker))
            ));
        }
        if (canTsumo) {
            actions.add(ActionButton.create(
                    Component.text("TSUMO", NamedTextColor.LIGHT_PURPLE),
                    Component.text("Win by tsumo", NamedTextColor.GRAY),
                    140,
                    dialogAction(targetId, clicker -> requestTsumo(clicker))
            ));
        }
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Actions", NamedTextColor.AQUA))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "Choose an action or close to discard.", NamedTextColor.GRAY))))
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(actions).build())
        );
        player.showDialog(dialog);
    }

    private void showDiscardConfirmDialog(Player player, Tile tile, ItemStack item) {
        if (player == null || tile == null) {
            return;
        }
        ItemStack displayItem = item == null ? null : item.clone();
        ItemStack payload = displayItem == null ? item : displayItem;
        if (payload == null) {
            return;
        }
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text(
                "Discard " + tile.getId().toShortString() + "?", NamedTextColor.RED)));
        if (displayItem != null) {
            body.add(DialogBody.item(displayItem).build());
        }
        UUID targetId = player.getUniqueId();
        ActionButton yesButton = ActionButton.create(
                Component.text("Discard", NamedTextColor.RED),
                Component.text("Confirm discard", NamedTextColor.GRAY),
                140,
                dialogAction(targetId, clicker -> handleDiscard(clicker, payload))
        );
        ActionButton noButton = ActionButton.create(
                Component.text("Cancel", NamedTextColor.GRAY),
                Component.text("Keep tile", NamedTextColor.DARK_GRAY),
                140,
                null
        );
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Confirm Discard", NamedTextColor.RED))
                        .body(body)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.confirmation(yesButton, noButton))
        );
        player.showDialog(dialog);
    }

    private List<ActionButton> buildCallButtons(Player player, List<CallOption> options, Tile lastDiscard, boolean includePass) {
        List<ActionButton> actions = new ArrayList<>();
        UUID targetId = player.getUniqueId();
        for (CallOption option : options) {
            String label = describeCallOption(option, lastDiscard);
            DialogAction action = dialogAction(targetId, clicker -> handleCallOption(option, clicker));
            actions.add(ActionButton.create(
                    Component.text(label, callColor(option.getType())),
                    Component.text("울기 " + callName(option.getType()), NamedTextColor.GRAY),
                    150,
                    action
            ));
        }
        if (includePass) {
            actions.add(ActionButton.create(
                    Component.text("PASS", NamedTextColor.DARK_GRAY),
                    Component.text("Close dialog", NamedTextColor.DARK_GRAY),
                    110,
                    null
            ));
        }
        return actions;
    }

    private void handleCallOption(CallOption option, Player player) {
        switch (option.getType()) {
            case RON:
                requestRon(player);
                break;
            case PON:
                requestPon(player);
                break;
            case KAN:
                requestKan(player);
                break;
            case CHI:
                requestChi(player, option.getChiIndex());
                break;
            default:
                break;
        }
    }

    private DialogAction dialogAction(UUID targetId, Consumer<Player> action) {
        return DialogAction.customClick((view, audience) -> {
            if (!(audience instanceof Player player)) {
                return;
            }
            if (!player.getUniqueId().equals(targetId)) {
                return;
            }
            action.accept(player);
        }, ClickCallback.Options.builder().uses(1).build());
    }

    private String describeCallOption(CallOption option, Tile lastDiscard) {
        String tiles = formatTilesForCall(option, lastDiscard);
        if (tiles.isEmpty()) {
            return callName(option.getType());
        }
        return callName(option.getType()) + " " + tiles;
    }

    private String formatKanOptionLabel(KanOption option, int index) {
        String tile = option.getTileId().toShortString();
        String type = option.getType() == MeldType.KAN_CLOSED ? "CLOSED" : "ADDED";
        return index + ". " + type + " " + tile;
    }

    private String describeSelfKan(KanOption option) {
        String tile = option.getTileId().toShortString();
        String type = option.getType() == MeldType.KAN_CLOSED ? "closed" : "added";
        return type + " kan (" + tile + ")";
    }

    private String formatTilesForCall(CallOption option, Tile lastDiscard) {
        List<TileId> ids = new ArrayList<>();
        for (Tile tile : option.getTiles()) {
            ids.add(tile.getId());
        }
        if (lastDiscard != null && option.getType() != CallType.RON) {
            ids.add(lastDiscard.getId());
        } else if (lastDiscard != null && option.getType() == CallType.RON && ids.isEmpty()) {
            ids.add(lastDiscard.getId());
        }
        if (ids.isEmpty()) {
            return "";
        }
        ids.sort(Comparator.comparingInt(this::tileSortKey));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append("-");
            }
            sb.append(ids.get(i).toShortString());
        }
        return sb.toString();
    }

    private String buildTilePayload(Tile tile) {
        if (tile == null) {
            return "";
        }
        return "tile=" + tile.getId().toShortString();
    }

    private String buildDrawPayload(Tile tile, boolean rinshan) {
        if (tile == null) {
            return "";
        }
        return "tile=" + tile.getId().toShortString() + ";rinshan=" + rinshan;
    }

    private String buildCallPayload(CallRequest request, Tile lastDiscard, UUID lastDiscarder) {
        if (request == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("type=").append(request.getType().name());
        List<TileId> ids = new ArrayList<>();
        for (Tile tile : request.getTiles()) {
            ids.add(tile.getId());
        }
        if (lastDiscard != null) {
            ids.add(lastDiscard.getId());
        }
        String tiles = formatTileIds(ids);
        if (!tiles.isEmpty()) {
            sb.append(";tiles=").append(tiles);
        }
        if (lastDiscarder != null) {
            sb.append(";from=").append(lastDiscarder);
        }
        return sb.toString();
    }

    private String buildKanPayload(KanOption option) {
        if (option == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("type=").append(option.getType().name());
        sb.append(";tile=").append(option.getTileId().toShortString());
        return sb.toString();
    }

    private String buildWinPayload(boolean tsumo, UUID discarder) {
        if (tsumo) {
            return "type=TSUMO";
        }
        if (discarder != null) {
            return "type=RON;discarder=" + discarder;
        }
        return "type=RON";
    }

    private String buildRyuukyokuPayload(List<UUID> tenpaiPlayers) {
        if (tenpaiPlayers == null || tenpaiPlayers.isEmpty()) {
            return "tenpai=";
        }
        return "tenpai=" + formatPlayerIdList(tenpaiPlayers);
    }

    private String formatTileIds(List<TileId> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        List<TileId> sorted = new ArrayList<>(ids);
        sorted.sort(Comparator.comparingInt(this::tileSortKey));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                sb.append("-");
            }
            sb.append(sorted.get(i).toShortString());
        }
        return sb.toString();
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

    private NamedTextColor callColor(CallType type) {
        switch (type) {
            case RON:
                return NamedTextColor.RED;
            case KAN:
                return NamedTextColor.GOLD;
            case PON:
                return NamedTextColor.YELLOW;
            case CHI:
                return NamedTextColor.GREEN;
            default:
                return NamedTextColor.WHITE;
        }
    }

    private String callName(CallType type) {
        if (type == null) {
            return "콜";
        }
        switch (type) {
            case RON:
                return "론";
            case KAN:
                return "깡";
            case PON:
                return "퐁";
            case CHI:
                return "치";
            default:
                return type.name();
        }
    }

    private void showCallPopup(Player player, List<String> options, int remainingSeconds) {
        if (player == null || options == null || options.isEmpty()) {
            return;
        }
        String text = "울기: " + String.join("/", options);
        if (remainingSeconds >= 0) {
            text += " (" + remainingSeconds + "s)";
        }
        player.sendActionBar(text);
    }

    private void updateCallPopups(int remainingSeconds) {
        if (engine == null || engine.getState() != GameState.CALL_WINDOW) {     
            return;
        }
        if (dialogsEnabled()) {
            return;
        }
        for (UUID playerId : players) {
            if (playerId.equals(engine.getLastDiscarder())) {
                continue;
            }
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            List<String> options = resolveCallOptions(playerId, player);
            if (!options.isEmpty()) {
                showCallPopup(player, options, remainingSeconds);
            }
        }
    }

    private void clearCallPopups() {
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.sendActionBar("");
            }
        }
    }

    private void clearCallDialogs() {
        if (!dialogsEnabled()) {
            callDialogPlayers.clear();
            return;
        }
        for (UUID playerId : callDialogPlayers) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.closeDialog();
            }
        }
        callDialogPlayers.clear();
    }

    private void closeDialogsForAll() {
        if (!dialogsEnabled()) {
            return;
        }
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.closeDialog();
            }
        }
    }

    private void closeDialog(Player player) {
        if (player == null || !dialogsEnabled()) {
            return;
        }
        player.closeDialog();
    }

    private boolean dialogsEnabled() {
        return plugin.getConfig().getBoolean("ui.enableDialogs", false);
    }

    private boolean shouldConfirmDiscard() {
        return dialogsEnabled() && plugin.getConfig().getBoolean("ui.confirmDiscard", false);
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
                        player.sendMessage("후리텐 상태라 론할 수 없어요.");
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
        broadcast("국: " + round.getRoundWind() + " " + round.getKyoku() + " / 딜러: " + round.getDealerWind());
        broadcast("본장: " + honba + " / 공탁: " + riichiPot + " / 핸드: " + hands);
    }

    private void broadcastRoomRules() {
        if (!roomMode) {
            return;
        }
        broadcast("룸 룰: " + describeRules(getRulesSnapshot()));
        updateRoomLobbyUi();
    }

    private void updateRoomLobbyUi() {
        if (!roomMode || getState() != GameState.LOBBY) {
            clearRoomBossBar();
            return;
        }
        updateRoomBossBar();
        String status = buildRoomStatusLine();
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.sendActionBar(status);
            }
        }
    }

    private void updateRoomBossBar() {
        if (!roomMode || getState() != GameState.LOBBY) {
            clearRoomBossBar();
            return;
        }
        if (roomBossBar == null) {
            roomBossBar = Bukkit.createBossBar(buildRoomBossBarTitle(), BarColor.BLUE, BarStyle.SEGMENTED_10);
        }
        roomBossBar.setTitle(buildRoomBossBarTitle());
        double progress = players.isEmpty() ? 0.0 : (double) readyPlayers.size() / players.size();
        roomBossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        roomBossBar.removeAll();
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                roomBossBar.addPlayer(player);
            }
        }
        roomBossBar.setVisible(true);
    }

    private void clearRoomBossBar() {
        if (roomBossBar != null) {
            roomBossBar.removeAll();
            roomBossBar.setVisible(false);
            roomBossBar = null;
        }
    }

    private String buildRoomBossBarTitle() {
        return buildRoomStatusLine() + " | Rules: " + describeRules(getRulesSnapshot());
    }

    private String buildRoomStatusLine() {
        String code = roomCode == null ? "-" : roomCode;
        String hostName = hostId == null ? "-" : resolveName(hostId);
        return "Room " + code + " (" + players.size() + "/" + MAX_PLAYERS + ") Seats " + seatAssignments.size() + "/" + MAX_PLAYERS + " Ready " + getReadyCount() + "/" + players.size() + " Host " + hostName;
    }

    private boolean assignSeat(Player player, SeatWind seat) {
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
            broadcast(resolveName(playerId) + " 님이 좌석 " + seatLabel(seat) + "에서 일어났어요.");
            updateRoomLobbyUi();
            return true;
        }
        if (currentSeat != null) {
            seatAssignments.remove(currentSeat);
        }
        seatAssignments.put(seat, playerId);
        playerSeats.put(playerId, seat);
        readyPlayers.remove(playerId);
        broadcast(resolveName(playerId) + " 님이 좌석 " + seatLabel(seat) + "에 앉았어요.");
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
                broadcast(resolveName(playerId) + " 님이 좌석 " + seatLabel(seat) + "에 앉았어요.");
                updateRoomLobbyUi();
                return;
            }
        }
    }

    private void clearSeat(UUID playerId, boolean updateUi) {
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

    private String seatLabel(SeatWind seat) {
        if (seat == null) {
            return "UNSEATED";
        }
        switch (seat) {
            case EAST:
                return "E";
            case SOUTH:
                return "S";
            case WEST:
                return "W";
            case NORTH:
            default:
                return "N";
        }
    }

    private String describeRules(GameRules rules) {
        return "redDora=" + onOff(rules.isRedDoraEnabled())
                + ", openTanyao=" + onOff(rules.isOpenTanyaoEnabled())
                + ", ippatsu=" + onOff(rules.isIppatsuEnabled())
                + ", uraDora=" + onOff(rules.isUraDoraEnabled())
                + ", bots=" + onOff(botsEnabled)
                + ", coach=" + onOff(coachEnabled)
                + ", coachRank=" + onOff(coachRankDisabled);
    }

    private String onOff(boolean enabled) {
        return enabled ? "ON" : "OFF";
    }

    private String formatRuleLine(String label, boolean enabled) {
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

    private void sendRoomRulesSummary(Player player) {
        player.sendMessage("룸 룰: " + describeRules(getRulesSnapshot()));
        player.sendMessage("룰 변경: /mj room rules <rule> <on|off> 또는 /mj room rules preset <default|kuitan|classic>");
        player.sendMessage("rule 목록: redDora, openTanyao, ippatsu, uraDora, bots, coach, coachRank");
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

    private boolean isCoachAllowed() {
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

    private String resolveName(UUID playerId) {
        BotProfile bot = bots.get(playerId);
        if (bot != null) {
            return bot.getName();
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }
        return playerId.toString();
    }

    private void broadcast(String message) {
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private void broadcastDoraIndicators() {
        if (engine == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Dora indicator: ");
        List<Tile> indicators = engine.getDoraIndicators();
        for (int i = 0; i < indicators.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(indicators.get(i).getId().toShortString());
        }
        broadcast(sb.toString());
        worldUi.updateDora(this);
    }

    private void startCallBossBar(int seconds) {
        clearCallBossBar();
        callBossBar = Bukkit.createBossBar("Call window: " + seconds + "s", BarColor.YELLOW, BarStyle.SOLID);
        for (UUID playerId : players) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                callBossBar.addPlayer(player);
            }
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
                callBossBar.setTitle("Call window: " + remaining + "s");
                callBossBar.setProgress(Math.max(0.0, Math.min(1.0, remaining / (double) seconds)));
                updateWorldUiActions(remaining);
                updateCallPopups(remaining);
            }
        }, 20L, 20L);
    }

    private void clearCallBossBar() {
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
        worldUi.updateYakuPanel();
    }

    private void updateWorldUi() {
        if (!worldUi.isSpawned()) {
            return;
        }
        worldUi.updateBoard(this);
        worldUi.updateDora(this);
        worldUi.updateDiscards(this);
        worldUi.updateYakuPanel();
        worldUi.updateActionButtons(this, -1);
    }

    private void updateWorldUiActions(int callSecondsRemaining) {
        if (!worldUi.isSpawned()) {
            return;
        }
        worldUi.updateActionButtons(this, callSecondsRemaining);
    }

    private static final class HandResult {
        private final UUID winnerId;
        private final UUID discarderId;
        private final boolean tsumo;
        private final ScoreResult score;
        private final List<UUID> tenpaiPlayers;
        private final Map<UUID, Integer> pointDeltas;
        private final Map<UUID, Integer> pointsAfter;
        private final int honbaApplied;
        private final int riichiPotApplied;
        private final boolean gameOver;
        private final RoundState nextRound;

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

    private static final class CallOption {
        private final CallType type;
        private final int chiIndex;
        private final List<Tile> tiles;

        private CallOption(CallType type, int chiIndex, List<Tile> tiles) {
            this.type = type;
            this.chiIndex = chiIndex;
            this.tiles = tiles == null ? List.of() : List.copyOf(tiles);
        }

        private CallType getType() {
            return type;
        }

        private int getChiIndex() {
            return chiIndex;
        }

        private List<Tile> getTiles() {
            return tiles;
        }
    }
}
