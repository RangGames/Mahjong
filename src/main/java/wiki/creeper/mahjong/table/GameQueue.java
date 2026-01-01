package wiki.creeper.mahjong.table;

import java.util.ArrayDeque;
import java.util.Queue;

public class GameQueue {

    private final Queue<Runnable> queue = new ArrayDeque<>();
    private boolean processing;

    public synchronized void enqueue(Runnable action) {
        queue.add(action);
        if (!processing) {
            processing = true;
            drain();
        }
    }

    private void drain() {
        while (true) {
            Runnable next;
            synchronized (this) {
                next = queue.poll();
                if (next == null) {
                    processing = false;
                    return;
                }
            }
            next.run();
        }
    }

    public synchronized void clear() {
        queue.clear();
        processing = false;
    }
}
