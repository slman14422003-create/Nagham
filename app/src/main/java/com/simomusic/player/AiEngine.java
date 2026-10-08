package com.simomusic.player;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Builds the "AI Mix" playlist entirely on the phone: no network call, no model file, just arithmetic over
 * {@link PlayStats}. Every song gets a score from five signals and the best ones become the playlist:
 *   - how often you've let it play through (frequency)
 *   - how recently you played it, decaying over about a month (recency)
 *   - how often you skip away from it (a penalty)
 *   - whether you starred it (favorites bonus)
 *   - whether its artist is one you lean on, even for songs of theirs you haven't played yet (discovery)
 * A touch of randomness keeps a regenerate from looking identical to the last one.
 */
public final class AiEngine {
    private AiEngine() {
    }

    private static final int TARGET = 60;

    public static synchronized void generate(Context c) {
        if (!Store.aiEnabled(c)) return;
        List<Track> lib = Library.tracks;
        if (lib.isEmpty()) {
            Store.setOrder(c, Store.AI, new ArrayList<>());
            return;
        }
        Map<Long, PlayStats.Row> stats = PlayStats.all(c);

        // how much you lean on each artist overall: plays count for it, skips count a little against it
        Map<String, Double> artistWeight = new HashMap<>();
        for (Track t : lib) {
            if (t.artist.isEmpty()) continue;
            PlayStats.Row r = stats.get(t.id);
            if (r == null) continue;
            double w = r.plays - r.skips * 0.5;
            if (w <= 0) continue;
            Double prev = artistWeight.get(t.artist);
            artistWeight.put(t.artist, prev == null ? w : prev + w);
        }

        long now = System.currentTimeMillis() / 1000;
        Random rnd = new Random();
        final Map<Long, Double> score = new HashMap<>();
        for (Track t : lib) {
            PlayStats.Row r = stats.get(t.id);
            int plays = r == null ? 0 : r.plays;
            int skips = r == null ? 0 : r.skips;
            long lastPlayed = r == null ? 0 : r.lastPlayed;

            double frequency = Math.log(1 + plays) * 1.6;
            double recency = 0;
            if (lastPlayed > 0) {
                double days = Math.max(0, (now - lastPlayed) / 86400.0);
                recency = Math.exp(-days / 30.0) * 1.3;
            }
            double skipPenalty = (plays + skips) > 0 ? (skips / (double) (plays + skips + 1)) * 1.4 : 0;
            double favBonus = Store.has(c, Store.FAV, t.id) ? 0.9 : 0;
            double artistBoost = 0;
            Double aw = artistWeight.get(t.artist);
            if (aw != null) artistBoost = Math.min(1.0, Math.log(1 + aw) / 6.0) * (plays == 0 ? 0.7 : 0.3);
            double discovery = (plays == 0 && skips == 0) ? rnd.nextDouble() * 0.25 : 0;
            double jitter = rnd.nextDouble() * 0.08;

            score.put(t.id, frequency + recency + favBonus + artistBoost + discovery + jitter - skipPenalty);
        }

        List<Track> ranked = new ArrayList<>(lib);
        ranked.sort((a, b) -> Double.compare(score.get(b.id), score.get(a.id)));
        int n = Math.min(TARGET, ranked.size());
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) ids.add(ranked.get(i).id);
        Store.setOrder(c, Store.AI, ids);
    }
}
