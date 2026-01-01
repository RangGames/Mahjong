package wiki.creeper.mahjong.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GameEventLogger {

    private final List<GameEvent> events = new ArrayList<>();

    public void record(GameEvent event) {
        events.add(event);
    }

    public List<GameEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }

    public void clear() {
        events.clear();
    }

    public Path exportTo(Path file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("file");
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        List<String> lines = new ArrayList<>(events.size());
        for (GameEvent event : events) {
            lines.add(event.toLine());
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }
}
