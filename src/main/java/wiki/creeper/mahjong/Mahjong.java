package wiki.creeper.mahjong;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import wiki.creeper.mahjong.api.MahjongApi;
import wiki.creeper.mahjong.api.MahjongApiImpl;
import wiki.creeper.mahjong.command.MahjongCommand;
import wiki.creeper.mahjong.table.TableManager;
import wiki.creeper.mahjong.ui.MahjongListener;
import wiki.creeper.mahjong.ui.WorldUiListener;

public final class Mahjong extends JavaPlugin {

    private TableManager tableManager;
    private MahjongApi api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.tableManager = new TableManager(this);
        this.api = new MahjongApiImpl(tableManager);

        PluginCommand command = getCommand("mj");
        if (command != null) {
            MahjongCommand handler = new MahjongCommand(tableManager);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        } else {
            getLogger().warning("Command 'mj' not registered in plugin.yml.");
        }
        getServer().getPluginManager().registerEvents(new MahjongListener(tableManager), this);
        getServer().getPluginManager().registerEvents(new WorldUiListener(this, tableManager), this);
    }

    @Override
    public void onDisable() {
        if (tableManager != null) {
            tableManager.shutdown();
        }
    }

    public MahjongApi getApi() {
        return api;
    }
}
