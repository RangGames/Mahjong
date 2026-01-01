package wiki.creeper.mahjong.ui;

import java.util.ArrayList;
import java.util.EnumMap;
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
import wiki.creeper.mahjong.game.PlayerState;
import wiki.creeper.mahjong.game.RoundState;
import wiki.creeper.mahjong.game.SeatWind;
import wiki.creeper.mahjong.game.Tile;
import wiki.creeper.mahjong.table.GameTable;

public class WorldUiManager {
    private static final float ACTION_WIDTH = 0.6f;
    private static final float ACTION_HEIGHT = 0.4f;
    private static final double TABLE_Y = 0.2;
    private static final double TABLE_SPACING = 0.85;
    private static final int TABLE_SIZE = 3;
    private static final int DISCARD_COLUMNS = 6;
    private static final double DISCARD_Y = 0.35;
    private static final double DISCARD_SPACING = 0.26;
    private static final double DISCARD_ROW_SPACING = 0.23;
    private static final double DORA_ITEM_Y = 2.45;
    private static final double DORA_ITEM_SPACING = 0.35;
    private static final double MELD_Y = 0.4;
    private static final double MELD_SPACING = 0.28;
    private static final double MELD_GROUP_SPACING = 0.12;
    private static final Vector3f DISCARD_BLOCK_SCALE = new Vector3f(0.22f, 0.06f, 0.22f);
    private static final Vector3f DISCARD_LABEL_SCALE = new Vector3f(0.45f, 0.45f, 0.45f);
    private static final double DISCARD_LABEL_Y = 0.12;
    private static final int SCOREBOARD_WIDTH = 140;
    private static final int DORA_WIDTH = 120;
    private static final int PANEL_WIDTH = 120;
    private static final int ACTION_WIDTH_TEXT = 80;

    private final JavaPlugin plugin;
    private final UUID tableId;
    private final NamespacedKey tableKey;
    private final NamespacedKey actionKey;
    private Location anchor;
    private TextDisplay scoreBoard;
    private TextDisplay doraLine;
    private TextDisplay yakuPanel;
    private TextDisplay actionStatus;
    private TextDisplay handResult;
    private final List<BlockDisplay> tableBlocks = new ArrayList<>();
    private final List<ItemDisplay> doraItems = new ArrayList<>();
    private final List<Display> discardDisplays = new ArrayList<>();
    private final Map<WorldUiAction, ActionButton> buttons = new EnumMap<>(WorldUiAction.class);

    public WorldUiManager(JavaPlugin plugin, UUID tableId) {
        this.plugin = plugin;
        this.tableId = tableId;
        this.tableKey = new NamespacedKey(plugin, "table_id");
        this.actionKey = new NamespacedKey(plugin, "action");
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
        scoreBoard = spawnTextDisplay(world, this.anchor.clone().add(0, 2.8, 0), "Mahjong");
        scoreBoard.setLineWidth(SCOREBOARD_WIDTH);
        scoreBoard.setAlignment(TextDisplay.TextAlignment.CENTER);
        doraLine = spawnTextDisplay(world, this.anchor.clone().add(0, 2.45, 0), "Dora: -");
        doraLine.setLineWidth(DORA_WIDTH);
        doraLine.setAlignment(TextDisplay.TextAlignment.CENTER);
        actionStatus = spawnTextDisplay(world, this.anchor.clone().add(0, 1.55, 0), "");
        actionStatus.setLineWidth(ACTION_WIDTH_TEXT);
        actionStatus.setAlignment(TextDisplay.TextAlignment.CENTER);
        yakuPanel = spawnTextDisplay(world, this.anchor.clone().add(2.0, 1.8, 0), "");
        yakuPanel.setLineWidth(PANEL_WIDTH);
        yakuPanel.setAlignment(TextDisplay.TextAlignment.LEFT);
        handResult = spawnTextDisplay(world, this.anchor.clone().add(-2.0, 1.8, 0), "");
        handResult.setLineWidth(PANEL_WIDTH);
        handResult.setAlignment(TextDisplay.TextAlignment.LEFT);
        spawnActionButtons(world, this.anchor);
    }

    public void remove() {
        for (BlockDisplay block : tableBlocks) {
            block.remove();
        }
        tableBlocks.clear();
        clearDoraItems();
        clearDiscardItems();
        if (scoreBoard != null) {
            scoreBoard.remove();
            scoreBoard = null;
        }
        if (doraLine != null) {
            doraLine.remove();
            doraLine = null;
        }
        if (yakuPanel != null) {
            yakuPanel.remove();
            yakuPanel = null;
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
    }

    public void updateBoard(GameTable table) {
        if (scoreBoard == null) {
            return;
        }
        GameEngine engine = table.getEngine();
        if (engine == null) {
            scoreBoard.text(Component.text("Mahjong - waiting"));
            return;
        }
        RoundState round = engine.getRoundState();
        StringBuilder sb = new StringBuilder();
        int kyoku = round.getKyoku();
        sb.append("Round: ").append(round.getRoundWind())
                .append(" / Kyoku: ").append(kyoku)
                .append(" / Dealer: ").append(round.getDealerWind())
                .append("\nHonba: ").append(round.getHonba())
                .append(" / Riichi: ").append(round.getRiichiPot())
                .append("\nRemaining: ").append(round.getRemainingTiles())
                .append(" / Hands: ").append(round.getHandsPlayed());
        sb.append("\nPoints:");
        for (UUID playerId : table.getPlayers()) {
            PlayerState state = engine.getPlayerState(playerId);
            if (state == null) {
                continue;
            }
            String name = resolveName(playerId);
            sb.append("\n- ").append(name).append(": ").append(state.getPoints());
        }
        scoreBoard.text(Component.text(sb.toString(), NamedTextColor.WHITE));
    }

    public void updateDora(GameTable table) {
        if (doraLine == null) {
            return;
        }
        GameEngine engine = table.getEngine();
        if (engine == null) {
            doraLine.text(Component.text("Dora: -", NamedTextColor.GOLD));
            clearDoraItems();
            return;
        }
        List<Tile> indicators = engine.getDoraIndicators();
        StringBuilder sb = new StringBuilder();
        sb.append("Dora: ");
        for (int i = 0; i < indicators.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(indicators.get(i).getId().toShortString());
        }
        doraLine.text(Component.text(sb.toString(), NamedTextColor.GOLD));
        updateDoraItems(indicators);
    }

    public void updateDiscards(GameTable table) {
        if (anchor == null) {
            return;
        }
        clearDiscardItems();
        GameEngine engine = table.getEngine();
        if (engine == null) {
            return;
        }
        for (UUID playerId : table.getPlayers()) {
            PlayerState state = engine.getPlayerState(playerId);
            if (state == null) {
                continue;
            }
            SeatWind wind = state.getSeatWind();
            spawnDiscardItems(state.getDiscards(), wind);
            spawnMeldItems(state.getHand().getMelds(), wind);
        }
    }

    public void updateYakuPanel() {
        if (yakuPanel == null) {
            return;
        }
        // SIMPLIFIED: static panel for implemented yaku only, no ukeire or per-hand analysis.
        StringBuilder sb = new StringBuilder();
        sb.append("Yaku Panel");
        for (wiki.creeper.mahjong.game.Yaku yaku : wiki.creeper.mahjong.game.Yaku.values()) {
            sb.append("\n- ").append(yaku.getDisplayName());
        }
        yakuPanel.text(Component.text(sb.toString(), NamedTextColor.AQUA));
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
            setActionStatus("Waiting");
            disableAllActions();
            return;
        }
        if (engine.getState() == GameState.HAND_END) {
            setActionStatus("HAND END");
            disableAllActions();
            return;
        }
        boolean callWindow = engine.getState() == GameState.CALL_WINDOW;        
        if (callWindow) {
            setActionStatus(callSecondsRemaining >= 0 ? "CALL " + callSecondsRemaining + "s" : "CALL");
        } else {
            setActionStatus("ACTIONS");
        }

        boolean showChi = false;
        boolean showPon = false;
        boolean showKan = false;
        boolean showRon = false;
        if (callWindow) {
            // SIMPLIFIED: call buttons are shown globally, not per-player visibility.
            UUID chiPlayer = engine.getNextPlayerForCall();
            showChi = chiPlayer != null && engine.getChiOptionCount(chiPlayer) > 0;
            for (UUID playerId : table.getPlayers()) {
                if (engine.canRon(playerId)) {
                    showRon = true;
                }
                if (engine.createPonRequest(playerId).isPresent()) {
                    showPon = true;
                }
                if (engine.createKanRequest(playerId).isPresent()) {
                    showKan = true;
                }
            }
        } else {
            UUID active = engine.getActivePlayer();
            showKan = active != null && engine.canDeclareKan(active);
        }
        setActionEnabled(WorldUiAction.CHI, showChi);
        setActionEnabled(WorldUiAction.PON, showPon);
        setActionEnabled(WorldUiAction.KAN, showKan);
        setActionEnabled(WorldUiAction.RON, showRon);

        UUID active = engine.getActivePlayer();
        boolean canRiichi = active != null && engine.canDeclareRiichi(active);
        boolean canTsumo = active != null && engine.canTsumo(active);
        setActionEnabled(WorldUiAction.RIICHI, !callWindow && canRiichi);
        setActionEnabled(WorldUiAction.TSUMO, !callWindow && canTsumo);
    }

    private void setActionStatus(String text) {
        actionStatus.text(Component.text(text, NamedTextColor.YELLOW));
    }

    private void disableAllActions() {
        for (WorldUiAction action : WorldUiAction.values()) {
            setActionEnabled(action, false);
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
        display.setBillboard(Display.Billboard.CENTER);
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

    private void spawnDiscardItems(List<Tile> discards, SeatWind wind) {
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
            spawnDiscardBlock(world, location, tile);
            spawnDiscardLabel(world, location, tile);
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
        Vector base = meldBaseOffset(wind);
        Vector dir = layout.col.clone();
        int offsetIndex = 0;
        for (Meld meld : melds) {
            for (Tile tile : meld.getTiles()) {
                Vector offset = base.clone().add(dir.clone().multiply(offsetIndex * MELD_SPACING));
                Location location = anchor.clone().add(offset);
            spawnDiscardBlock(world, location, tile);
            spawnDiscardLabel(world, location, tile);
            offsetIndex++;
        }
            offsetIndex++;
        }
        // SIMPLIFIED: meld layout ignores called-from orientation and uses a flat row.
    }

    private void clearDiscardItems() {
        for (Display display : discardDisplays) {
            display.remove();
        }
        discardDisplays.clear();
    }

    private void spawnDiscardBlock(World world, Location location, Tile tile) {
        BlockDisplay display = world.spawn(location, BlockDisplay.class);
        display.setBlock(createDiscardBlockData(tile));
        display.setBillboard(Display.Billboard.FIXED);
        display.setShadowRadius(0);
        display.setShadowStrength(0);
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(DISCARD_BLOCK_SCALE), new Quaternionf()));
        discardDisplays.add(display);
    }

    private void spawnDiscardLabel(World world, Location location, Tile tile) {
        if (tile == null || tile.getId() == null) {
            return;
        }
        TextDisplay label = world.spawn(location.clone().add(0, DISCARD_LABEL_Y, 0), TextDisplay.class);
        label.text(Component.text(tile.getId().toShortString(), NamedTextColor.WHITE));
        label.setBillboard(Display.Billboard.FIXED);
        label.setShadowed(false);
        label.setSeeThrough(true);
        label.setLineWidth(30);
        label.setBackgroundColor(Color.fromRGB(0, 0, 0));
        label.setDefaultBackground(false);
        label.setTextOpacity((byte) 220);
        label.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(DISCARD_LABEL_SCALE), new Quaternionf()));
        discardDisplays.add(label);
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

    private DiscardLayout discardLayout(SeatWind wind) {
        Vector base;
        Vector col;
        Vector row;
        switch (wind) {
            case EAST:
                base = new Vector(-0.6, DISCARD_Y, 1.3);
                col = new Vector(1, 0, 0);
                row = new Vector(0, 0, -1);
                break;
            case SOUTH:
                base = new Vector(1.3, DISCARD_Y, 0.6);
                col = new Vector(0, 0, -1);
                row = new Vector(-1, 0, 0);
                break;
            case WEST:
                base = new Vector(0.6, DISCARD_Y, -1.3);
                col = new Vector(-1, 0, 0);
                row = new Vector(0, 0, 1);
                break;
            case NORTH:
            default:
                base = new Vector(-1.3, DISCARD_Y, -0.6);
                col = new Vector(0, 0, 1);
                row = new Vector(1, 0, 0);
                break;
        }
        return new DiscardLayout(base, col, row);
    }

    private Vector meldBaseOffset(SeatWind wind) {
        switch (wind) {
            case EAST:
                return new Vector(-0.6, MELD_Y, 1.9);
            case SOUTH:
                return new Vector(1.9, MELD_Y, 0.6);
            case WEST:
                return new Vector(0.6, MELD_Y, -1.9);
            case NORTH:
            default:
                return new Vector(-1.9, MELD_Y, -0.6);
        }
    }

    private ItemStack createTileItem(Tile tile) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(tile.getId().toShortString(), NamedTextColor.WHITE));
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
}
