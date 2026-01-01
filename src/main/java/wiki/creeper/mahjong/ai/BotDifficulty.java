package wiki.creeper.mahjong.ai;

import java.util.Locale;
import java.util.Optional;

public enum BotDifficulty {
    BEGINNER,
    NORMAL,
    HARD;

    public static Optional<BotDifficulty> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return Optional.of(BotDifficulty.valueOf(normalized));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
