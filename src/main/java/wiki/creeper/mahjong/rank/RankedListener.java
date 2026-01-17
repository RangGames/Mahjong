package wiki.creeper.mahjong.rank;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import wiki.creeper.mahjong.api.event.MahjongHandEndEvent;

public final class RankedListener implements Listener {
    private final RankedManager rankedManager;

    public RankedListener(RankedManager rankedManager) {
        this.rankedManager = rankedManager;
    }

    @EventHandler
    public void onHandEnd(MahjongHandEndEvent event) {
        if (event == null || !event.isGameOver()) {
            return;
        }
        rankedManager.updateOnGameEnd(event.getTable(), event.getPointsAfter());
    }
}
