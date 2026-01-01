package wiki.creeper.mahjong.ai;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import wiki.creeper.mahjong.game.Hand;
import wiki.creeper.mahjong.game.Tile;

public final class ShantenCalculator {
    private static final int[] KOKUSHI_INDICES = new int[] {
            0, 8, 9, 17, 18, 26,
            27, 28, 29, 30, 31, 32, 33
    };
    private static final Map<String, Integer> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(512, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                    return size() > 5000;
                }
            });

    private ShantenCalculator() {
    }

    public static int calculate(Hand hand) {
        if (hand == null) {
            return 8;
        }
        return calculate(hand.getConcealed(), hand.getMelds().size());
    }

    public static int calculate(List<Tile> concealed, int openMelds) {
        int[] counts = TileCounter.countTiles(concealed);
        return calculate(counts, openMelds);
    }

    public static int minShantenAfterDiscard(List<Tile> tiles, int openMelds) {
        if (tiles == null || tiles.isEmpty()) {
            return calculate(List.of(), openMelds);
        }
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < tiles.size(); i++) {
            List<Tile> remaining = new java.util.ArrayList<>(tiles);
            remaining.remove(i);
            best = Math.min(best, calculate(remaining, openMelds));
        }
        return best == Integer.MAX_VALUE ? 8 : best;
    }

    private static int calculate(int[] counts, int openMelds) {
        String key = buildCacheKey(counts, openMelds);
        Integer cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        int standard = calculateStandard(Arrays.copyOf(counts, counts.length), openMelds);
        int chiitoi = openMelds == 0 ? calculateChiitoi(counts) : 8;
        int kokushi = openMelds == 0 ? calculateKokushi(counts) : 8;
        int result = Math.min(standard, Math.min(chiitoi, kokushi));
        CACHE.put(key, result);
        return result;
    }

    private static int calculateStandard(int[] counts, int openMelds) {
        SearchContext ctx = new SearchContext(openMelds);
        search(counts, 0, 0, 0, 0, ctx);
        return ctx.best;
    }

    private static void search(int[] counts, int index, int melds, int pairs, int taatsu, SearchContext ctx) {
        if (ctx.best <= -1) {
            return;
        }
        while (index < counts.length && counts[index] == 0) {
            index++;
        }
        if (index >= counts.length) {
            ctx.update(melds, pairs, taatsu);
            return;
        }
        if (counts[index] >= 3) {
            counts[index] -= 3;
            search(counts, index, melds + 1, pairs, taatsu, ctx);
            counts[index] += 3;
        }
        if (isSuit(index) && index % 9 <= 6 && counts[index + 1] > 0 && counts[index + 2] > 0) {
            counts[index]--;
            counts[index + 1]--;
            counts[index + 2]--;
            search(counts, index, melds + 1, pairs, taatsu, ctx);
            counts[index]++;
            counts[index + 1]++;
            counts[index + 2]++;
        }
        if (counts[index] >= 2) {
            counts[index] -= 2;
            search(counts, index, melds, pairs + 1, taatsu, ctx);
            counts[index] += 2;
        }
        if (isSuit(index)) {
            if (index % 9 <= 7 && counts[index + 1] > 0) {
                counts[index]--;
                counts[index + 1]--;
                search(counts, index, melds, pairs, taatsu + 1, ctx);
                counts[index]++;
                counts[index + 1]++;
            }
            if (index % 9 <= 6 && counts[index + 2] > 0) {
                counts[index]--;
                counts[index + 2]--;
                search(counts, index, melds, pairs, taatsu + 1, ctx);
                counts[index]++;
                counts[index + 2]++;
            }
        }
        counts[index]--;
        search(counts, index, melds, pairs, taatsu, ctx);
        counts[index]++;
    }

    private static int calculateChiitoi(int[] counts) {
        int pairs = 0;
        int unique = 0;
        for (int count : counts) {
            if (count >= 2) {
                pairs++;
            }
            if (count > 0) {
                unique++;
            }
        }
        int needUnique = Math.max(0, 7 - unique);
        return 6 - pairs + needUnique;
    }

    private static int calculateKokushi(int[] counts) {
        int unique = 0;
        boolean hasPair = false;
        for (int index : KOKUSHI_INDICES) {
            if (counts[index] > 0) {
                unique++;
                if (counts[index] > 1) {
                    hasPair = true;
                }
            }
        }
        return 13 - unique - (hasPair ? 1 : 0);
    }

    private static boolean isSuit(int index) {
        return index < 27;
    }

    private static String buildCacheKey(int[] counts, int openMelds) {
        return Arrays.toString(counts) + "|" + openMelds;
    }

    private static final class SearchContext {
        private final int openMelds;
        private int best = 8;

        private SearchContext(int openMelds) {
            this.openMelds = openMelds;
        }

        private void update(int melds, int pairs, int taatsu) {
            int meldsTotal = melds + openMelds;
            int pairsUsed = pairs;
            int taatsuUsed = taatsu;
            if (pairsUsed > 1) {
                taatsuUsed += pairsUsed - 1;
                pairsUsed = 1;
            }
            if (meldsTotal > 4) {
                meldsTotal = 4;
            }
            int maxTaatsu = 4 - meldsTotal;
            if (maxTaatsu < 0) {
                maxTaatsu = 0;
            }
            if (taatsuUsed > maxTaatsu) {
                taatsuUsed = maxTaatsu;
            }
            int shanten = 8 - (meldsTotal * 2) - taatsuUsed - pairsUsed;
            if (shanten < best) {
                best = shanten;
            }
        }
    }
}
