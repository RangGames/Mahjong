package wiki.creeper.mahjong.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import wiki.creeper.mahjong.game.GameEngine;
import wiki.creeper.mahjong.game.GameState;
import wiki.creeper.mahjong.game.Meld;
import wiki.creeper.mahjong.game.MeldType;
import wiki.creeper.mahjong.game.PlayerState;
import wiki.creeper.mahjong.game.RoundState;
import wiki.creeper.mahjong.game.SeatWind;
import wiki.creeper.mahjong.game.Tile;
import wiki.creeper.mahjong.table.GameTable;

public class WorldUiManager {
    private static final float ACTION_WIDTH = 0.6f;
    private static final float ACTION_HEIGHT = 0.4f;
    private static final double TABLE_Y = 0.2;
    private static final float TABLE_HEIGHT_SCALE = 0.2f;
    private static final double TABLE_SPACING = 0.9;
    private static final int TABLE_SIZE = 3;
    private static final double SCOREBOARD_Y = 3.15;
    private static final double DORA_TEXT_Y = 2.75;
    private static final double ACTION_STATUS_Y = 1.85;
    private static final double HAND_RESULT_X = -2.6;
    private static final double HAND_RESULT_Y = 2.2;
    private static final int DISCARD_COLUMNS = 6;
    private static final double DISCARD_Y = 1.05;
    private static final double DISCARD_DEPTH = 2.3;
    private static final double DISCARD_SPACING = 0.38;
    private static final double DISCARD_ROW_SPACING = 0.34;
    private static final double DORA_ITEM_Y = 2.35;
    private static final double DORA_ITEM_SPACING = 0.34;
    private static final double MELD_Y = 1.15;
    private static final double MELD_DEPTH = 2.6;
    private static final double MELD_SPACING = 0.36;
    private static final double MELD_GROUP_SPACING = 0.22;
    private static final double MELD_TYPE_ROW_GAP = 0.4;
    private static final double HAND_Y = 1.42;
    private static final double HAND_SPACING = 0.34;
    private static final double HAND_DRAWN_GAP = 0.3;
    private static final double HAND_DRAWN_RAISE = 0.2;
    private static final Vector3f DISCARD_BLOCK_SCALE = new Vector3f(0.23f, 0.07f, 0.23f);
    private static final Vector3f DISCARD_ITEM_SCALE = new Vector3f(0.3f, 0.3f, 0.3f);
    private static final Vector3f DISCARD_LABEL_SCALE = new Vector3f(0.28f, 0.28f, 0.28f);
    private static final Vector3f HAND_ITEM_SCALE = new Vector3f(0.32f, 0.32f, 0.32f);
    private static final Vector3f HAND_LABEL_SCALE = new Vector3f(0.28f, 0.28f, 0.28f);
    private static final Vector3f MELD_LABEL_SCALE = new Vector3f(0.3f, 0.3f, 0.3f);
    private static final Vector3f SEAT_LABEL_SCALE = new Vector3f(0.36f, 0.36f, 0.36f);
    private static final float CALLED_TILE_YAW = 90f;
    private static final double DISCARD_LABEL_Y = 0.12;
    private static final double MELD_LABEL_Y = 0.16;
    private static final double HAND_LABEL_Y = 0.12;
    private static final double SEAT_LABEL_Y = 1.85;
    private static final double SEAT_LABEL_RADIUS = 3.2;
    private static final int SCOREBOARD_WIDTH = 160;
    private static final int DORA_WIDTH = 140;
    private static final int PANEL_WIDTH = 140;
    private static final int ACTION_WIDTH_TEXT = 140;

    private final JavaPlugin plugin;
    private final UUID tableId;
    private final NamespacedKey tableKey;
    private final NamespacedKey actionKey;
    private final NamespacedKey handTileKey;
    private final NamespacedKey handOwnerKey;
    private Location anchor;
    private TextDisplay scoreBoard;
    private TextDisplay doraLine;
    private TextDisplay actionStatus;
    private TextDisplay handResult;
    private final List<BlockDisplay> tableBlocks = new ArrayList<>();
    private final List<ItemDisplay> doraItems = new ArrayList<>();
    private final List<Display> discardDisplays = new ArrayList<>();
    private final List<TextDisplay> seatLabels = new ArrayList<>();
    private final Map<UUID, List<Entity>> handDisplays = new HashMap<>();
    private final Map<WorldUiAction, ActionButton> buttons = new EnumMap<>(WorldUiAction.class);
    private String lastDiscardSignature;

    public WorldUiManager(JavaPlugin plugin, UUID tableId) {
        this.plugin = plugin;
        this.tableId = tableId;
        this.tableKey = new NamespacedKey(plugin, "table_id");
        this.actionKey = new NamespacedKey(plugin, "action");
        this.handTileKey = new NamespacedKey(plugin, "hand_tile");
        this.handOwnerKey = new NamespacedKey(plugin, "hand_owner");
    }

    public boolean isSpawned() {
        return anchor != null;
    }

    public void spawn(Location anchor) {
        if (anchor == null || isSpawned()) {
            return;
        }
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        // SIMPLIFIED: anchor uses the first player's location, not a fixed board layout.
        Location base = anchor.clone();
        base.setYaw(0);
        base.setPitch(0);
        this.anchor = base;
        spawnTableSurface(world, this.anchor);
        scoreBoard = spawnTextDisplay(world, this.anchor.clone().add(0, SCOREBOARD_Y, 0), "마작");
        scoreBoard.setLineWidth(SCOREBOARD_WIDTH);
        scoreBoard.setAlignment(TextDisplay.TextAlignment.CENTER);
        doraLine = spawnTextDisplay(world, this.anchor.clone().add(0, DORA_TEXT_Y, 0), "도라: -");
        doraLine.setLineWidth(DORA_WIDTH);
        doraLine.setAlignment(TextDisplay.TextAlignment.CENTER);
        actionStatus = spawnTextDisplay(world, this.anchor.clone().add(0, ACTION_STATUS_Y, 0), "대기 중");
        actionStatus.setLineWidth(ACTION_WIDTH_TEXT);
        actionStatus.setAlignment(TextDisplay.TextAlignment.CENTER);
        handResult = spawnTextDisplay(world, this.anchor.clone().add(HAND_RESULT_X, HAND_RESULT_Y, 0), "");
        handResult.setLineWidth(PANEL_WIDTH);
        handResult.setAlignment(TextDisplay.TextAlignment.LEFT);
        spawnActionButtons(world, this.anchor);
    }

    public void remove() {
        for (BlockDisplay block : tableBlocks) {
            block.remove();
        }
        tableBlocks.clear();
        clearAllHandDisplays();
        clearDoraItems();
        clearDiscardItems();
        clearSeatLabels();
        if (scoreBoard != null) {
            scoreBoard.remove();
            scoreBoard = null;
        }
        if (doraLine != null) {
            doraLine.remove();
            doraLine = null;
        }
        if (actionStatus != null) {
            actionStatus.remove();
            actionStatus = null;
        }
        if (handResult != null) {
            handResult.remove();
            handResult = null;
        }
        for (ActionButton button : buttons.values()) {
            button.remove();
        }
        buttons.clear();
        anchor = null;
        lastDiscardSignature = null;
    }

    public void updateBoard(GameTable table) {
        if (scoreBoard == null) {
            return;
        }
        GameEngine engine = table.getEngine();
        if (engine == null) {
            scoreBoard.text(Component.text("마작 - 대기 중"));
            return;
        }
        RoundState round = engine.getRoundState();
        StringBuilder sb = new StringBuilder();
        int kyoku = round.getKyoku();
        sb.append("국: ").append(seatLabel(round.getRoundWind())).append(" ").append(kyoku)
                .append(" / 딜러: ").append(seatLabel(round.getDealerWind()))
                .append("\n본장: ").append(round.getHonba())
                .append(" / 공탁: ").append(round.getRiichiPot())
                .append("\n남은 패: ").append(round.getRemainingTiles())
                .append(" / 진행: ").append(round.getHandsPlayed());
        sb.append("\n점수:");
        Map<SeatWind, PlayerState> seatStates = new EnumMap<>(SeatWind.class);
        Map<SeatWind, String> seatNames = new EnumMap<>(SeatWind.class);
        for (UUID playerId : table.getPlayers()) {
            PlayerState state = engine.getPlayerState(playerId);
            if (state == null) {
                continue;
            }
            seatStates.put(state.getSeatWind(), state);
            seatNames.put(state.getSeatWind(), table.getDisplayName(playerId));
        }
        for (SeatWind seat : SeatWind.values()) {
            PlayerState state = seatStates.get(seat);
            String name = seatNames.get(seat);
            if (state == null || name == null) {
                continue;
            }
            boolean dealer = round.getDealerWind() == seat;
            boolean riichi = state.getHand().isRiichiDeclared();
            sb.append("\n- ").append(seatLabel(seat));
            if (dealer) {
                sb.append("(딜러)");
            }
            sb.append(" ").append(name).append(": ").append(state.getPoints());
            if (riichi) {
                sb.append(" [리치]");
            }
        }
        scoreBoard.text(Component.text(sb.toString(), NamedTextColor.WHITE));
    }

    public void updateDora(GameTable table) {
        if (doraLine == null) {
            return;
        }
        GameEngine engine = table.getEngine();
        if (engine == null) {
            doraLine.text(Component.text("도라: -", NamedTextColor.GOLD));
            clearDoraItems();
            return;
        }
        List<Tile> indicators = engine.getDoraIndicators();
        StringBuilder sb = new StringBuilder();
        sb.append("도라: ");
        for (int i = 0; i < indicators.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(indicators.get(i).getId().toDisplayString());
        }
        doraLine.text(Component.text(sb.toString(), NamedTextColor.GOLD));
        updateDoraItems(indicators);
    }

    public void updateDiscards(GameTable table) {
        if (anchor == null) {
            return;
        }
        GameEngine engine = table.getEngine();
        if (engine == null) {
            lastDiscardSignature = null;
            clearDiscardItems();
            clearSeatLabels();
            return;
        }
        Map<SeatWind, PlayerState> seatStates = new EnumMap<>(SeatWind.class);
        Map<SeatWind, UUID> seatPlayers = new EnumMap<>(SeatWind.class);
        for (UUID playerId : table.getPlayers()) {
            PlayerState state = engine.getPlayerState(playerId);
            if (state == null) {
                continue;
            }
            seatStates.put(state.getSeatWind(), state);
            seatPlayers.put(state.getSeatWind(), playerId);
        }
        Tile lastDiscard = engine.getLastDiscard();
        RoundState round = engine.getRoundState();
        String signature = buildDiscardSignature(table, seatStates, seatPlayers, lastDiscard);
        if (signature.equals(lastDiscardSignature)) {
            return;
        }
        lastDiscardSignature = signature;
        clearDiscardItems();
        clearSeatLabels();
        for (SeatWind wind : SeatWind.values()) {
            PlayerState state = seatStates.get(wind);
            UUID playerId = seatPlayers.get(wind);
            if (state == null || playerId == null) {
                continue;
            }
            spawnSeatLabel(wind, table.getDisplayName(playerId), state, round);
            spawnDiscardItems(state.getDiscards(), wind, lastDiscard);
            spawnMeldItems(state.getHand().getMelds(), wind);
        }
    }

    public void updateHandDisplay(GameTable table, UUID playerId, PlayerState state) {
        if (anchor == null || table == null || playerId == null || state == null) {
            return;
        }
        clearHandDisplay(playerId);
        Player owner = plugin.getServer().getPlayer(playerId);
        if (owner == null) {
            return;
        }
        GameEngine engine = table.getEngine();
        Tile drawn = null;
        if (engine != null && playerId.equals(engine.getLastDrawnPlayer())) {
            drawn = engine.getLastDrawnTile();
        }
        List<Tile> tiles = new ArrayList<>(state.getHand().getConcealed());
        if (drawn != null) {
            int drawnId = drawn.getInstanceId();
            tiles.removeIf(tile -> tile.getInstanceId() == drawnId);
        }
        tiles.sort(Comparator
                .comparingInt((Tile tile) -> tileSortKey(tile))
                .thenComparing(tile -> tile.getId().isRed() ? 1 : 0));
        HandLayout layout = handLayout(state.getSeatWind());
        List<Entity> spawned = new ArrayList<>();
        for (int i = 0; i < tiles.size(); i++) {
            Vector offset = layout.base.clone().add(layout.col.clone().multiply(i * HAND_SPACING));
            Location location = anchor.clone().add(offset);
            spawned.addAll(spawnHandTile(owner, playerId, tiles.get(i), location, false));
        }
        if (drawn != null) {
            Vector offset = layout.base.clone().add(layout.col.clone().multiply(tiles.size() * HAND_SPACING + HAND_DRAWN_GAP));
            Location location = anchor.clone().add(offset);
            spawned.addAll(spawnHandTile(owner, playerId, drawn, location, true));
        }
        if (!spawned.isEmpty()) {
            handDisplays.put(playerId, spawned);
        }
    }

    public void clearHandDisplay(UUID playerId) {
        if (playerId == null) {
            return;
        }
        List<Entity> entities = handDisplays.remove(playerId);
        if (entities == null) {
            return;
        }
        for (Entity entity : entities) {
            entity.remove();
        }
    }

    public void clearAllHandDisplays() {
        for (UUID playerId : new ArrayList<>(handDisplays.keySet())) {
            clearHandDisplay(playerId);
        }
    }

    public void hideHandDisplaysFor(Player viewer) {
        if (viewer == null) {
            return;
        }
        UUID viewerId = viewer.getUniqueId();
        for (Map.Entry<UUID, List<Entity>> entry : handDisplays.entrySet()) {
            if (viewerId.equals(entry.getKey())) {
                continue;
            }
            for (Entity entity : entry.getValue()) {
                viewer.hideEntity(plugin, entity);
            }
        }
    }

    public void updateHandResult(List<String> lines) {
        if (handResult == null) {
            return;
        }
        if (lines == null || lines.isEmpty()) {
            handResult.text(Component.empty());
            return;
        }
        handResult.text(Component.text(String.join("\n", lines), NamedTextColor.WHITE));
    }

    public void clearHandResult() {
        if (handResult != null) {
            handResult.text(Component.empty());
        }
    }

    public void updateActionButtons(GameTable table, int callSecondsRemaining) {
        if (actionStatus == null) {
            return;
        }
        GameEngine engine = table.getEngine();
        if (engine == null) {
            setActionStatus("대기 중");
            disableAllActions();
            hideAllActionButtons();
            return;
        }
        if (engine.getState() == GameState.HAND_END) {
            setActionStatus("국 종료");
            disableAllActions();
            hideAllActionButtons();
            return;
        }
        boolean callWindow = engine.getState() == GameState.CALL_WINDOW;
        if (callWindow) {
            setActionStatus("진행 중");
        } else if (engine.getState() == GameState.TURN_DISCARD) {
            UUID active = engine.getActivePlayer();
            PlayerState activeState = active != null ? engine.getPlayerState(active) : null;
            String name = active != null ? table.getDisplayName(active) : "-";
            String seatTag = activeState != null ? seatLabel(activeState.getSeatWind()) + " " : "";
            int remaining = table.getTurnSecondsRemaining();
            if (remaining > 0) {
                setActionStatus("턴: " + seatTag + name + " (" + remaining + "초)");
            } else {
                setActionStatus("턴: " + seatTag + name);
            }
        } else if (engine.getState() == GameState.LOBBY) {
            setActionStatus("대기 중");
        } else {
            setActionStatus("진행 중");
        }

        Map<UUID, ActionVisibility> visibility = new HashMap<>();
        boolean showChi = false;
        boolean showPon = false;
        boolean showKan = false;
        boolean showRon = false;
        boolean showRiichi = false;
        boolean showTsumo = false;
        for (UUID playerId : table.getPlayers()) {
            if (table.isBot(playerId)) {
                continue;
            }
            boolean chi = false;
            boolean pon = false;
            boolean kan = false;
            boolean ron = false;
            boolean riichi = false;
            boolean tsumo = false;
            if (callWindow) {
                chi = engine.getChiOptionCount(playerId) > 0;
                pon = engine.createPonRequest(playerId).isPresent();
                kan = engine.createKanRequest(playerId).isPresent();
                ron = engine.canRon(playerId);
            } else {
                UUID active = engine.getActivePlayer();
                if (playerId.equals(active)) {
                    kan = engine.canDeclareKan(playerId);
                    riichi = engine.canDeclareRiichi(playerId);
                    tsumo = engine.canTsumo(playerId);
                }
            }
            visibility.put(playerId, new ActionVisibility(chi, pon, kan, ron, riichi, tsumo));
            showChi |= chi;
            showPon |= pon;
            showKan |= kan;
            showRon |= ron;
            showRiichi |= riichi;
            showTsumo |= tsumo;
        }
        setActionEnabled(WorldUiAction.CHI, showChi);
        setActionEnabled(WorldUiAction.PON, showPon);
        setActionEnabled(WorldUiAction.KAN, showKan);
        setActionEnabled(WorldUiAction.RON, showRon);
        setActionEnabled(WorldUiAction.RIICHI, !callWindow && showRiichi);
        setActionEnabled(WorldUiAction.TSUMO, !callWindow && showTsumo);
        updateActionVisibility(visibility);
    }

    private void setActionStatus(String text) {
        actionStatus.text(Component.text(text, NamedTextColor.YELLOW));
    }

    private void disableAllActions() {
        for (WorldUiAction action : WorldUiAction.values()) {
            setActionEnabled(action, false);
        }
    }

    private void updateActionVisibility(Map<UUID, ActionVisibility> visibility) {
        if (anchor == null) {
            return;
        }
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            ActionVisibility state = visibility.get(player.getUniqueId());
            if (state == null) {
                hideAllActions(player);
                continue;
            }
            setActionVisibility(player, WorldUiAction.CHI, state.chi);
            setActionVisibility(player, WorldUiAction.PON, state.pon);
            setActionVisibility(player, WorldUiAction.KAN, state.kan);
            setActionVisibility(player, WorldUiAction.RON, state.ron);
            setActionVisibility(player, WorldUiAction.RIICHI, state.riichi);
            setActionVisibility(player, WorldUiAction.TSUMO, state.tsumo);
        }
    }

    private void hideAllActionButtons() {
        if (anchor == null) {
            return;
        }
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            hideAllActions(player);
        }
    }

    private void hideAllActions(Player player) {
        for (WorldUiAction action : WorldUiAction.values()) {
            setActionVisibility(player, action, false);
        }
    }

    private void setActionVisibility(Player player, WorldUiAction action, boolean visible) {
        if (player == null || action == null) {
            return;
        }
        ActionButton button = buttons.get(action);
        if (button == null) {
            return;
        }
        if (visible) {
            player.showEntity(plugin, button.label);
            player.showEntity(plugin, button.interaction);
        } else {
            player.hideEntity(plugin, button.label);
            player.hideEntity(plugin, button.interaction);
        }
    }

    private void setActionEnabled(WorldUiAction action, boolean enabled) {
        ActionButton button = buttons.get(action);
        if (button == null) {
            return;
        }
        button.setEnabled(enabled);
    }

    private void spawnActionButtons(World world, Location anchor) {
        double callRowY = 1.05;
        double selfRowY = 0.75;
        double spacing = 0.55;
        createButton(world, anchor.clone().add(-0.85, callRowY, 0), WorldUiAction.CHI);
        createButton(world, anchor.clone().add(-0.3, callRowY, 0), WorldUiAction.PON);
        createButton(world, anchor.clone().add(0.3, callRowY, 0), WorldUiAction.KAN);
        createButton(world, anchor.clone().add(0.85, callRowY, 0), WorldUiAction.RON);
        createButton(world, anchor.clone().add(-spacing, selfRowY, 0), WorldUiAction.RIICHI);
        createButton(world, anchor.clone().add(spacing, selfRowY, 0), WorldUiAction.TSUMO);
        disableAllActions();
    }

    private void spawnTableSurface(World world, Location anchor) {
        double start = -(TABLE_SPACING * (TABLE_SIZE - 1)) / 2.0;
        for (int x = 0; x < TABLE_SIZE; x++) {
            for (int z = 0; z < TABLE_SIZE; z++) {
                Location location = anchor.clone().add(start + (x * TABLE_SPACING), TABLE_Y, start + (z * TABLE_SPACING));
                BlockDisplay display = world.spawn(location, BlockDisplay.class);
                display.setBlock(Material.GREEN_TERRACOTTA.createBlockData());
                display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(1f, TABLE_HEIGHT_SCALE, 1f), new Quaternionf()));
                tableBlocks.add(display);
            }
        }
        // SIMPLIFIED: table surface uses a 3x3 block display grid without rotation.
    }

    private void createButton(World world, Location location, WorldUiAction action) {
        TextDisplay label = spawnTextDisplay(world, location.clone().add(0, 0.1, 0), action.getLabel());
        label.text(Component.text(action.getLabel(), NamedTextColor.DARK_GRAY));
        Interaction interaction = world.spawn(location, Interaction.class);
        interaction.setInteractionHeight(0);
        interaction.setInteractionWidth(0);
        PersistentDataContainer container = interaction.getPersistentDataContainer();
        container.set(tableKey, PersistentDataType.STRING, tableId.toString());
        container.set(actionKey, PersistentDataType.STRING, action.name());
        buttons.put(action, new ActionButton(label, interaction, action.getLabel()));
    }

    private TextDisplay spawnTextDisplay(World world, Location location, String text) {
        TextDisplay display = world.spawn(location, TextDisplay.class);
        display.text(Component.text(text));
        display.setBillboard(Display.Billboard.VERTICAL);
        display.setRotation(0f, 0f);
        display.setSeeThrough(true);
        display.setShadowed(true);
        display.setBackgroundColor(Color.fromRGB(0, 0, 0));
        display.setDefaultBackground(false);
        return display;
    }

    private void updateDoraItems(List<Tile> indicators) {
        clearDoraItems();
        if (anchor == null || indicators == null) {
            return;
        }
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        double start = -(DORA_ITEM_SPACING * Math.max(0, indicators.size() - 1)) / 2.0;
        for (int i = 0; i < indicators.size(); i++) {
            Location location = anchor.clone().add(start + (i * DORA_ITEM_SPACING), DORA_ITEM_Y, 0);
            ItemDisplay display = world.spawn(location, ItemDisplay.class);
            display.setItemStack(createTileItem(indicators.get(i)));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setRotation(0f, 0f);
            display.setShadowRadius(0);
            display.setShadowStrength(0);
            applyFlatTransform(display, DISCARD_ITEM_SCALE);
            doraItems.add(display);
        }
        // SIMPLIFIED: dora line uses flat item displays without orientation control.
    }

    private void clearDoraItems() {
        for (ItemDisplay display : doraItems) {
            display.remove();
        }
        doraItems.clear();
    }

    private void spawnDiscardItems(List<Tile> discards, SeatWind wind, Tile lastDiscard) {
        if (anchor == null || discards == null || discards.isEmpty()) {
            return;
        }
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        DiscardLayout layout = discardLayout(wind);
        for (int i = 0; i < discards.size(); i++) {
            int row = i / DISCARD_COLUMNS;
            int col = i % DISCARD_COLUMNS;
            Vector offset = layout.base.clone()
                    .add(layout.col.clone().multiply(col * DISCARD_SPACING))
                    .add(layout.row.clone().multiply(row * DISCARD_ROW_SPACING));
            Location location = anchor.clone().add(offset);
            Tile tile = discards.get(i);
            boolean highlight = lastDiscard != null
                    && tile.getInstanceId() == lastDiscard.getInstanceId();
            spawnDiscardBlock(world, location, tile, highlight, wind, false);
            spawnDiscardLabel(world, location, tile, highlight, wind);
        }
        // SIMPLIFIED: discard layout uses fixed world axes and ignores player orientation.
    }

    private void spawnMeldItems(List<Meld> melds, SeatWind wind) {
        if (anchor == null || melds == null || melds.isEmpty()) {
            return;
        }
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        DiscardLayout layout = discardLayout(wind);
        Vector dir = layout.col.clone();
        Map<MeldType, Integer> offsets = new EnumMap<>(MeldType.class);
        for (Meld meld : melds) {
            MeldType type = meld.getType();
            Vector base = meldBaseOffset(wind).clone().add(meldTypeOffset(layout, type));
            int offsetIndex = offsets.getOrDefault(type, 0);
            int groupStart = offsetIndex;
            List<Tile> tiles = meld.getTiles();
            int calledIndex = resolveCalledIndex(meld);
            for (int i = 0; i < tiles.size(); i++) {
                Vector offset = base.clone().add(dir.clone().multiply(offsetIndex * MELD_SPACING));
                Location location = anchor.clone().add(offset);
                boolean sideways = i == calledIndex;
                spawnDiscardBlock(world, location, tiles.get(i), false, wind, sideways);
                spawnDiscardLabel(world, location, tiles.get(i), false, wind);
                offsetIndex++;
            }
            if (!tiles.isEmpty()) {
                double centerIndex = groupStart + (tiles.size() - 1) / 2.0;
                Vector offset = base.clone().add(dir.clone().multiply(centerIndex * MELD_SPACING));
                Location labelLocation = anchor.clone().add(offset);
                spawnMeldLabel(world, labelLocation, type, wind);
            }
            int gap = Math.max(1, (int) Math.round(MELD_GROUP_SPACING / MELD_SPACING));
            offsetIndex += gap;
            offsets.put(type, offsetIndex);
        }
    }

    private void clearDiscardItems() {
        for (Display display : discardDisplays) {
            display.remove();
        }
        discardDisplays.clear();
    }

    private void clearSeatLabels() {
        for (TextDisplay label : seatLabels) {
            label.remove();
        }
        seatLabels.clear();
    }

    private void spawnSeatLabel(SeatWind wind, String name, PlayerState state, RoundState round) {
        if (anchor == null || wind == null || name == null || name.isBlank()) {
            return;
        }
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        boolean dealer = round != null && round.getDealerWind() == wind;
        int points = state != null ? state.getPoints() : 0;
        boolean riichi = state != null && state.getHand().isRiichiDeclared();
        String seatText = seatLabel(wind) + (dealer ? " (딜러)" : "");
        StringBuilder text = new StringBuilder();
        text.append(seatText).append(": ").append(name);
        text.append("\n점수: ").append(points);
        if (riichi) {
            text.append(" / 리치");
        }
        Vector offset = seatLabelOffset(wind);
        Location location = anchor.clone().add(offset).add(0, SEAT_LABEL_Y, 0);
        TextDisplay label = world.spawn(location, TextDisplay.class);
        NamedTextColor color = dealer ? NamedTextColor.GOLD : NamedTextColor.AQUA;
        label.text(Component.text(text.toString(), color));
        label.setBillboard(Display.Billboard.VERTICAL);
        label.setRotation(0f, 0f);
        label.setShadowed(false);
        label.setSeeThrough(true);
        label.setLineWidth(80);
        label.setAlignment(TextDisplay.TextAlignment.CENTER);
        label.setBackgroundColor(Color.fromRGB(0, 0, 0));
        label.setDefaultBackground(true);
        label.setTextOpacity((byte) 200);
        label.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(SEAT_LABEL_SCALE), new Quaternionf()));
        seatLabels.add(label);
    }

    private Vector seatLabelOffset(SeatWind wind) {
        switch (wind) {
            case EAST:
                return new Vector(0, 0, SEAT_LABEL_RADIUS);
            case SOUTH:
                return new Vector(SEAT_LABEL_RADIUS, 0, 0);
            case WEST:
                return new Vector(0, 0, -SEAT_LABEL_RADIUS);
            case NORTH:
            default:
                return new Vector(-SEAT_LABEL_RADIUS, 0, 0);
        }
    }

    private List<Entity> spawnHandTile(Player owner, UUID playerId, Tile tile, Location location, boolean drawn) {
        List<Entity> entities = new ArrayList<>();
        if (tile == null || location == null || owner == null) {
            return entities;
        }
        World world = location.getWorld();
        if (world == null) {
            return entities;
        }
        Location baseLocation = drawn ? location.clone().add(0, HAND_DRAWN_RAISE, 0) : location;
        ItemDisplay display = world.spawn(baseLocation, ItemDisplay.class);
        display.setItemStack(createTileItem(tile));
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBillboard(Display.Billboard.FIXED);
        display.setRotation(0f, 0f);
        display.setShadowRadius(0);
        display.setShadowStrength(0);
        display.setGlowing(drawn);
        applyUprightTransform(display, HAND_ITEM_SCALE);
        applyHandVisibility(owner, display);
        entities.add(display);

        if (!plugin.getConfig().getBoolean("resourcePack.enabled", false)) {
            TextDisplay label = world.spawn(baseLocation.clone().add(0, HAND_LABEL_Y, 0), TextDisplay.class);
                label.text(Component.text(tile.getId().toDisplayString(), NamedTextColor.WHITE));
            label.setBillboard(Display.Billboard.VERTICAL);
            label.setRotation(0f, 0f);
            label.setShadowed(false);
            label.setSeeThrough(true);
            label.setLineWidth(16);
            label.setAlignment(TextDisplay.TextAlignment.CENTER);
            label.setBackgroundColor(Color.fromRGB(0, 0, 0));
            label.setDefaultBackground(true);
            label.setTextOpacity((byte) 220);
            label.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(HAND_LABEL_SCALE), new Quaternionf()));
            applyHandVisibility(owner, label);
            entities.add(label);
        }

        Interaction interaction = world.spawn(baseLocation, Interaction.class);
        interaction.setInteractionHeight(0.25f);
        interaction.setInteractionWidth(0.25f);
        PersistentDataContainer container = interaction.getPersistentDataContainer();
        container.set(tableKey, PersistentDataType.STRING, tableId.toString());
        container.set(handTileKey, PersistentDataType.INTEGER, tile.getInstanceId());
        container.set(handOwnerKey, PersistentDataType.STRING, playerId.toString());
        applyHandVisibility(owner, interaction);
        entities.add(interaction);
        return entities;
    }

    private void applyHandVisibility(Player owner, Entity entity) {
        if (owner == null || entity == null) {
            return;
        }
        for (Player player : owner.getWorld().getPlayers()) {
            if (player.getUniqueId().equals(owner.getUniqueId())) {
                player.showEntity(plugin, entity);
            } else {
                player.hideEntity(plugin, entity);
            }
        }
    }

    private void spawnDiscardBlock(World world, Location location, Tile tile, boolean highlight, SeatWind wind, boolean sideways) {
        float yaw = yawForSeat(wind);
        if (sideways) {
            yaw += CALLED_TILE_YAW;
        }
        if (!shouldRenderDiscardTiles()) {
            return;
        }
        if (plugin.getConfig().getBoolean("resourcePack.enabled", false)) {
            ItemDisplay display = world.spawn(location, ItemDisplay.class);
            display.setItemStack(createTileItem(tile));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setRotation(yaw, 0f);
            display.setShadowRadius(0);
            display.setShadowStrength(0);
            display.setGlowing(highlight);
            applyFlatTransform(display, DISCARD_ITEM_SCALE);
            discardDisplays.add(display);
            return;
        }
        BlockDisplay display = world.spawn(location, BlockDisplay.class);
        display.setBlock(createDiscardBlockData(tile));
        display.setBillboard(Display.Billboard.FIXED);
        display.setRotation(yaw, 0f);
        display.setShadowRadius(0);
        display.setShadowStrength(0);
        display.setGlowing(highlight);
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(DISCARD_BLOCK_SCALE), new Quaternionf()));
        discardDisplays.add(display);
    }

    private void spawnDiscardLabel(World world, Location location, Tile tile, boolean highlight, SeatWind wind) {
        if (tile == null || tile.getId() == null) {
            return;
        }
        if (plugin.getConfig().getBoolean("resourcePack.enabled", false)) {
            return;
        }
        TextDisplay label = world.spawn(location.clone().add(0, DISCARD_LABEL_Y, 0), TextDisplay.class);
        String text = tile.getId().toDisplayString();
        if (highlight) {
            text += " (마지막)";
        }
        NamedTextColor color = highlight ? NamedTextColor.YELLOW : tileLabelColor(tile);
        label.text(Component.text(text, color));
        label.setBillboard(Display.Billboard.FIXED);
        label.setRotation(yawForSeat(wind), 0f);
        label.setShadowed(false);
        label.setSeeThrough(true);
        label.setLineWidth(18);
        label.setAlignment(TextDisplay.TextAlignment.CENTER);
        label.setBackgroundColor(Color.fromRGB(0, 0, 0));
        label.setDefaultBackground(true);
        label.setTextOpacity((byte) 220);
        label.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(DISCARD_LABEL_SCALE), new Quaternionf()));
        discardDisplays.add(label);
    }

    private void spawnMeldLabel(World world, Location location, MeldType type, SeatWind wind) {
        if (type == null) {
            return;
        }
        TextDisplay label = world.spawn(location.clone().add(0, MELD_LABEL_Y, 0), TextDisplay.class);
        label.text(Component.text(meldLabelText(type), meldColor(type)));
        label.setBillboard(Display.Billboard.FIXED);
        label.setRotation(yawForSeat(wind), 0f);
        label.setShadowed(false);
        label.setSeeThrough(true);
        label.setLineWidth(20);
        label.setAlignment(TextDisplay.TextAlignment.CENTER);
        label.setBackgroundColor(Color.fromRGB(0, 0, 0));
        label.setDefaultBackground(true);
        label.setTextOpacity((byte) 220);
        label.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(MELD_LABEL_SCALE), new Quaternionf()));
        discardDisplays.add(label);
    }

    private Vector meldTypeOffset(DiscardLayout layout, MeldType type) {
        if (layout == null || type == null) {
            return new Vector();
        }
        double rowOffset;
        switch (type) {
            case CHI:
                rowOffset = -MELD_TYPE_ROW_GAP;
                break;
            case PON:
                rowOffset = 0;
                break;
            case KAN_OPEN:
            case KAN_ADDED:
            case KAN_CLOSED:
            default:
                rowOffset = MELD_TYPE_ROW_GAP;
                break;
        }
        return layout.row.clone().multiply(rowOffset);
    }

    private int resolveCalledIndex(Meld meld) {
        if (meld == null || meld.getCalledFrom() == null) {
            return -1;
        }
        List<Tile> tiles = meld.getTiles();
        if (tiles.isEmpty()) {
            return -1;
        }
        switch (meld.getType()) {
            case CHI:
            case PON:
            case KAN_OPEN:
                return tiles.size() - 1;
            case KAN_ADDED:
                return tiles.size() >= 2 ? tiles.size() - 2 : -1;
            default:
                return -1;
        }
    }

    private String meldLabelText(MeldType type) {
        switch (type) {
            case CHI:
                return "치";
            case PON:
                return "퐁";
            case KAN_OPEN:
                return "깡";
            case KAN_CLOSED:
                return "암깡";
            case KAN_ADDED:
                return "가깡";
            default:
                return "부로";
        }
    }

    private String meldLabel(MeldType type) {
        switch (type) {
            case CHI:
                return "치";
            case PON:
                return "퐁";
            case KAN_OPEN:
                return "깡";
            case KAN_CLOSED:
                return "암깡";
            case KAN_ADDED:
                return "가깡";
            default:
                return "부로";
        }
    }

    private NamedTextColor meldColor(MeldType type) {
        switch (type) {
            case CHI:
                return NamedTextColor.GREEN;
            case PON:
                return NamedTextColor.YELLOW;
            case KAN_OPEN:
            case KAN_CLOSED:
            case KAN_ADDED:
                return NamedTextColor.GOLD;
            default:
                return NamedTextColor.WHITE;
        }
    }

    private NamedTextColor tileLabelColor(Tile tile) {
        if (tile == null || tile.getId() == null) {
            return NamedTextColor.WHITE;
        }
        if (tile.getId().isRed()) {
            return NamedTextColor.RED;
        }
        switch (tile.getId().getSuit()) {
            case MAN:
                return NamedTextColor.RED;
            case PIN:
                return NamedTextColor.AQUA;
            case SOU:
                return NamedTextColor.GREEN;
            case HONOR:
            default:
                return NamedTextColor.GOLD;
        }
    }

    private float yawForSeat(SeatWind wind) {
        if (wind == null) {
            return 0f;
        }
        switch (wind) {
            case EAST:
                return 0f;
            case SOUTH:
                return -90f;
            case WEST:
                return 180f;
            case NORTH:
            default:
                return 90f;
        }
    }

    private void applyFlatTransform(Display display, Vector3f scale) {
        if (display == null || scale == null) {
            return;
        }
        Quaternionf rotation = new Quaternionf().rotationX((float) Math.toRadians(90));
        display.setTransformation(new Transformation(new Vector3f(), rotation, new Vector3f(scale),
                new Quaternionf()));
    }

    private void applyUprightTransform(Display display, Vector3f scale) {
        if (display == null || scale == null) {
            return;
        }
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale),
                new Quaternionf()));
    }

    private String seatLabel(SeatWind wind) {
        if (wind == null) {
            return "-";
        }
        switch (wind) {
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

    private BlockData createDiscardBlockData(Tile tile) {
        Material material = Material.WHITE_CONCRETE;
        if (tile != null && tile.getId() != null) {
            if (tile.getId().isRed()) {
                material = Material.ORANGE_CONCRETE;
            } else {
                switch (tile.getId().getSuit()) {
                    case MAN:
                        material = Material.RED_CONCRETE;
                        break;
                    case PIN:
                        material = Material.LIGHT_BLUE_CONCRETE;
                        break;
                    case SOU:
                        material = Material.LIME_CONCRETE;
                        break;
                    case HONOR:
                    default:
                        material = Material.YELLOW_CONCRETE;
                        break;
                }
            }
        }
        return material.createBlockData();
    }

    private HandLayout handLayout(SeatWind wind) {
        Vector base;
        Vector col;
        switch (wind) {
            case EAST:
                base = new Vector(-1.3, HAND_Y, 3.0);
                col = new Vector(1, 0, 0);
                break;
            case SOUTH:
                base = new Vector(3.0, HAND_Y, 1.3);
                col = new Vector(0, 0, -1);
                break;
            case WEST:
                base = new Vector(1.3, HAND_Y, -3.0);
                col = new Vector(-1, 0, 0);
                break;
            case NORTH:
            default:
                base = new Vector(-3.0, HAND_Y, -1.3);
                col = new Vector(0, 0, 1);
                break;
        }
        return new HandLayout(base, col);
    }

    private int tileSortKey(Tile tile) {
        if (tile == null || tile.getId() == null) {
            return Integer.MAX_VALUE;
        }
        int suitBase;
        switch (tile.getId().getSuit()) {
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
        return suitBase + tile.getId().getRank();
    }

    private DiscardLayout discardLayout(SeatWind wind) {
        Vector base;
        Vector col;
        Vector row;
        double halfWidth = (DISCARD_COLUMNS - 1) * DISCARD_SPACING / 2.0;
        double depth = DISCARD_DEPTH;
        switch (wind) {
            case EAST:
                base = new Vector(-halfWidth, DISCARD_Y, depth);
                col = new Vector(1, 0, 0);
                row = new Vector(0, 0, -1);
                break;
            case SOUTH:
                base = new Vector(depth, DISCARD_Y, halfWidth);
                col = new Vector(0, 0, -1);
                row = new Vector(-1, 0, 0);
                break;
            case WEST:
                base = new Vector(halfWidth, DISCARD_Y, -depth);
                col = new Vector(-1, 0, 0);
                row = new Vector(0, 0, 1);
                break;
            case NORTH:
            default:
                base = new Vector(-depth, DISCARD_Y, -halfWidth);
                col = new Vector(0, 0, 1);
                row = new Vector(1, 0, 0);
                break;
        }
        return new DiscardLayout(base, col, row);
    }

    private Vector meldBaseOffset(SeatWind wind) {
        double halfWidth = (DISCARD_COLUMNS - 1) * MELD_SPACING / 2.0;
        switch (wind) {
            case EAST:
                return new Vector(-halfWidth, MELD_Y, MELD_DEPTH);
            case SOUTH:
                return new Vector(MELD_DEPTH, MELD_Y, halfWidth);
            case WEST:
                return new Vector(halfWidth, MELD_Y, -MELD_DEPTH);
            case NORTH:
            default:
                return new Vector(-MELD_DEPTH, MELD_Y, -halfWidth);
        }
    }

    private boolean shouldRenderDiscardTiles() {
        if (plugin.getConfig().getBoolean("resourcePack.enabled", false)) {
            return true;
        }
        return !plugin.getConfig().getBoolean("ui.world.compactDiscards", true);
    }

    private String buildDiscardSignature(GameTable table, Map<SeatWind, PlayerState> seatStates,
                                         Map<SeatWind, UUID> seatPlayers, Tile lastDiscard) {
        StringBuilder sb = new StringBuilder();
        sb.append("last=");
        sb.append(lastDiscard != null ? lastDiscard.getInstanceId() : -1);
        sb.append('|');
        for (SeatWind seat : SeatWind.values()) {
            PlayerState state = seatStates.get(seat);
            UUID playerId = seatPlayers.get(seat);
            if (state == null) {
                sb.append(seat.name()).append(":null|");
                continue;
            }
            if (playerId != null) {
                sb.append(table.getDisplayName(playerId)).append(':');
            }
            sb.append(seat.name())
                    .append(':').append(state.getPoints())
                    .append(':').append(state.getHand().isRiichiDeclared())
                    .append(':');
            for (Tile tile : state.getDiscards()) {
                sb.append(tile.getInstanceId()).append(',');
            }
            sb.append(':');
            for (Meld meld : state.getHand().getMelds()) {
                sb.append(meld.getType()).append('[');
                for (Tile tile : meld.getTiles()) {
                    sb.append(tile.getInstanceId()).append(',');
                }
                sb.append(']');
            }
            sb.append('|');
        }
        return sb.toString();
    }

    private ItemStack createTileItem(Tile tile) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(tile.getId().toDisplayString(), NamedTextColor.WHITE));
        if (plugin.getConfig().getBoolean("resourcePack.enabled", false)) {
            NamespacedKey modelKey = TileItemModel.resolve(plugin, tile.getId());
            if (modelKey != null) {
                meta.setItemModel(modelKey);
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    private String resolveName(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }
        return playerId.toString();
    }

    private static final class ActionVisibility {
        private final boolean chi;
        private final boolean pon;
        private final boolean kan;
        private final boolean ron;
        private final boolean riichi;
        private final boolean tsumo;

        private ActionVisibility(boolean chi, boolean pon, boolean kan, boolean ron, boolean riichi, boolean tsumo) {
            this.chi = chi;
            this.pon = pon;
            this.kan = kan;
            this.ron = ron;
            this.riichi = riichi;
            this.tsumo = tsumo;
        }
    }

    private static final class ActionButton {
        private final TextDisplay label;
        private final Interaction interaction;
        private final String labelText;

        private ActionButton(TextDisplay label, Interaction interaction, String labelText) {
            this.label = label;
            this.interaction = interaction;
            this.labelText = labelText;
        }

        private void setEnabled(boolean enabled) {
            label.text(enabled ? Component.text(labelText, NamedTextColor.YELLOW) : Component.empty());
            interaction.setInteractionWidth(enabled ? ACTION_WIDTH : 0);
            interaction.setInteractionHeight(enabled ? ACTION_HEIGHT : 0);
        }

        private void remove() {
            label.remove();
            interaction.remove();
        }
    }

    private static final class DiscardLayout {
        private final Vector base;
        private final Vector col;
        private final Vector row;

        private DiscardLayout(Vector base, Vector col, Vector row) {
            this.base = base;
            this.col = col;
            this.row = row;
        }
    }

    private static final class HandLayout {
        private final Vector base;
        private final Vector col;

        private HandLayout(Vector base, Vector col) {
            this.base = base;
            this.col = col;
        }
    }
}



