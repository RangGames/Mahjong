package wiki.creeper.mahjong;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import wiki.creeper.mahjong.api.MahjongApi;
import wiki.creeper.mahjong.api.MahjongApiImpl;
import wiki.creeper.mahjong.command.MahjongCommand;
import wiki.creeper.mahjong.rank.RankedListener;
import wiki.creeper.mahjong.rank.RankedManager;
import wiki.creeper.mahjong.rank.RankedQueueManager;
import wiki.creeper.mahjong.table.TableManager;
import wiki.creeper.mahjong.ui.MahjongListener;
import wiki.creeper.mahjong.ui.WorldUiListener;

public final class Mahjong extends JavaPlugin {

    private TableManager tableManager;
    private MahjongApi api;
    private RankedManager rankedManager;
    private RankedQueueManager rankedQueueManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.tableManager = new TableManager(this);
        this.rankedManager = new RankedManager(this);
        this.rankedQueueManager = new RankedQueueManager(this, tableManager, rankedManager);
        this.api = new MahjongApiImpl(tableManager, rankedManager);

        PluginCommand command = getCommand("mj");
        if (command != null) {
            MahjongCommand handler = new MahjongCommand(tableManager, rankedManager, rankedQueueManager);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        } else {
            getLogger().warning("Command 'mj' not registered in plugin.yml.");
        }
        getServer().getPluginManager().registerEvents(new MahjongListener(tableManager, rankedQueueManager), this);
        getServer().getPluginManager().registerEvents(new WorldUiListener(this, tableManager), this);
        getServer().getPluginManager().registerEvents(new RankedListener(rankedManager), this);
    }

    @Override
    public void onDisable() {
        if (rankedManager != null) {
            rankedManager.save();
        }
        if (tableManager != null) {
            tableManager.shutdown();
        }
    }

    public MahjongApi getApi() {
        return api;
    }

    public RankedManager getRankedManager() {
        return rankedManager;
    }
}
