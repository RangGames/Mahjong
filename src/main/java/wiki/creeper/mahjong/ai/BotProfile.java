package wiki.creeper.mahjong.ai;

import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public final class BotProfile {
    private final UUID id;
    private final BotDifficulty difficulty;
    private final long seed;
    private final Random random;
    private final String name;

    public BotProfile(UUID id, BotDifficulty difficulty, long seed, String name) {
        this.id = Objects.requireNonNull(id, "id");
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
        this.seed = seed;
        this.random = new Random(seed);
        this.name = Objects.requireNonNull(name, "name");
    }

    public UUID getId() {
        return id;
    }

    public BotDifficulty getDifficulty() {
        return difficulty;
    }

    public long getSeed() {
        return seed;
    }

    public Random getRandom() {
        return random;
    }

    public String getName() {
        return name;
    }
}
