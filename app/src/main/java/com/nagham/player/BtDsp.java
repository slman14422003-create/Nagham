package com.nagham.player;

/**
 * Headset sound engine: a floating-point DSP chain that runs inside the player's audio pipeline, so the music is
 * analysed, shaped and level-protected BEFORE it is handed to the Bluetooth encoder (SBC / AAC / LDAC ...).
 * <pre>
 *  input -> ITU-R BS.1770 K-weighted loudness analysis (gated, integrated)     [measurement only]
 *        -> loudness normalization gain (smooth, +/- limited)
 *        -> 10-band parametric EQ (RBJ peaking biquads, double precision)
 *        -> virtual bass (harmonics of the sub-bass that tiny drivers cannot play)
 *        -> 3-band compressor (Linkwitz-Riley 4th-order crossovers, stereo-linked, soft knee)
 *        -> mid/side stereo widening (bass stays mono)
 *        -> look-ahead peak limiter (box-filtered gain curve, so it cannot overshoot its ceiling)
 *  output (the caller adds TPDF dither when it quantises to 16 bit)
 * </pre>
 * No Android dependencies: it is unit-tested on a plain JVM.
 */
public final class BtDsp {
    public static final int BANDS = 10;
    static final double[] FREQ = {31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};

    /** Immutable-by-convention settings snapshot; build a new one and call setParams(). */
    public static final class Params {
        public boolean enabled;
        public final float[] eq = new float[BANDS];     // dB per band
        public float targetLufs = Float.NaN;            // NaN = loudness normalization off
        public boolean comp, wide, vbass;
        public float ceilingDb = -1.5f;                 // limiter ceiling (dBFS); below 0 leaves headroom for codec overshoot
    }

    /** Live measurements for the UI; written by the audio thread, read by the UI. */
    public static final class Stats {
        public volatile long stampNs;                   // System.nanoTime() of the last update: stale = not running
        public volatile float lufsI = Float.NaN, lufsM = Float.NaN, normDb, compDb, limDb, peakDb = -120f;
    }

    public final Stats stats = new Stats();

    // ------------------------------------------------------------------ biquad
    static final class Bq {
        double b0 = 1, b1, b2, a1, a2;
        final double[] z1 = new double[2], z2 = new double[2];

        double run(double x, int c) {
            double y = b0 * x + z1[c];
            z1[c] = b1 * x - a1 * y + z2[c];
            z2[c] = b2 * x - a2 * y;
            return y;
        }

        void clear() {
            z1[0] = z1[1] = z2[0] = z2[1] = 0;
        }

        void set(double nb0, double nb1, double nb2, double na0, double na1, double na2) {
            b0 = nb0 / na0;
            b1 = nb1 / na0;
            b2 = nb2 / na0;
            a1 = na1 / na0;
            a2 = na2 / na0;
        }

        void ident() {
            b0 = 1;
            b1 = b2 = a1 = a2 = 0;
        }

        void peaking(double fs, double f, double gainDb, double q) {
            double A = Math.pow(10, gainDb / 40), w = 2 * Math.PI * f / fs, al = Math.sin(w) / (2 * q), cs = Math.cos(w);
            set(1 + al * A, -2 * cs, 1 - al * A, 1 + al / A, -2 * cs, 1 - al / A);
        }

        void lowpass(double fs, double f, double q) {
            double w = 2 * Math.PI * f / fs, al = Math.sin(w) / (2 * q), cs = Math.cos(w);
            set((1 - cs) / 2, 1 - cs, (1 - cs) / 2, 1 + al, -2 * cs, 1 - al);
        }

        void highpass(double fs, double f, double q) {
            double w = 2 * Math.PI * f / fs, al = Math.sin(w) / (2 * q), cs = Math.cos(w);
            set((1 + cs) / 2, -(1 + cs), (1 + cs) / 2, 1 + al, -2 * cs, 1 - al);
        }

        /** BS.1770 stage 1: head-related high shelf. */
        void kShelf(double fs) {
            double f0 = 1681.974450955533, G = 3.999843853973347, Q = 0.7071752369554196;
            double K = Math.tan(Math.PI * f0 / fs), Vh = Math.pow(10, G / 20), Vb = Math.pow(Vh, 0.4996667741545416);
            double a0 = 1 + K / Q + K * K;
            set(Vh + Vb * K / Q + K * K, 2 * (K * K - Vh), Vh - Vb * K / Q + K * K, a0, 2 * (K * K - 1), 1 - K / Q + K * K);
        }

        /** BS.1770 stage 2: revised low-frequency B-curve (high-pass). */
        void kRlb(double fs) {
            double f0 = 38.13547087602444, Q = 0.5003270373238773;
            double K = Math.tan(Math.PI * f0 / fs), a0 = 1 + K / Q + K * K;
            set(1, -2, 1, a0, 2 * (K * K - 1), 1 - K / Q + K * K);
        }
    }

    /** Linkwitz-Riley 4th order = two identical Butterworth 2nd-order sections. */
    static final class Lr4 {
        final Bq a = new Bq(), b = new Bq();

        void lp(double fs, double f) {
            a.lowpass(fs, f, Math.sqrt(0.5));
            b.lowpass(fs, f, Math.sqrt(0.5));
        }

        void hp(double fs, double f) {
            a.highpass(fs, f, Math.sqrt(0.5));
            b.highpass(fs, f, Math.sqrt(0.5));
        }

        double run(double x, int c) {
            return b.run(a.run(x, c), c);
        }

        void clear() {
            a.clear();
            b.clear();
        }
    }

    /** One compressor band: peak envelope follower + soft-knee gain computer. */
    static final class Comp {
        double thr, ratio, knee, att, rel, makeupDb, env, makeup = 1;

        void init(double fs, double thrDb, double ratio, double kneeDb, double attMs, double relMs, double makeupFraction) {
            this.thr = thrDb;
            this.ratio = ratio;
            this.knee = kneeDb;
            att = 1 - Math.exp(-1.0 / (attMs * 0.001 * fs));
            rel = 1 - Math.exp(-1.0 / (relMs * 0.001 * fs));
            makeupDb = -thrDb * (1 - 1 / ratio) * makeupFraction;
            makeup = Math.pow(10, makeupDb / 20);
        }

        /** level: linked absolute peak of this band. Returns the linear gain (including make-up); grDb[0] = reduction. */
        double gain(double level, double[] grDb) {
            env += (level > env ? att : rel) * (level - env);
            double db = 20 * Math.log10(Math.max(env, 1e-9)), over = db - thr, gr;
            if (2 * over < -knee) gr = 0;
            else if (2 * Math.abs(over) <= knee) gr = (1 - 1 / ratio) * (over + knee / 2) * (over + knee / 2) / (2 * knee);
            else gr = (1 - 1 / ratio) * over;
            grDb[0] = gr;
            return Math.pow(10, -gr / 20) * makeup;
        }
    }

    // ------------------------------------------------------------------ state
    private volatile Params want = new Params();
    private Params cur;
    private int fs = 44100, ch = 2;
    boolean testNoMakeup;                                   // unit tests only

    // analysis
    private final Bq[] kA = {new Bq(), new Bq()}, kB = {new Bq(), new Bq()};
    private double blockSum;
    private int blockPos, blockLen, subCount;
    private final double[] sub = new double[4];
    private final int[] histN = new int[800];
    private final double[] histE = new double[800];
    private double blocksSeen;
    private double normDb, normLin = 1, normLinSm = 1;      // dB target-following value; per-sample smoothed linear

    // eq
    private final Bq[] eq = new Bq[BANDS];
    private final boolean[] eqOn = new boolean[BANDS];
    private double headroomDb;

    // virtual bass
    private final Bq vbLp = new Bq(), vbHp1 = new Bq(), vbHp2 = new Bq();

    // compressor
    private final Lr4 lowLp1 = new Lr4(), hiHp1 = new Lr4(), midLp2 = new Lr4(), hiHp2 = new Lr4(), apLp2 = new Lr4(), apHp2 = new Lr4();
    private final Comp[] comp = {new Comp(), new Comp(), new Comp()};
    private final double[] gr = new double[1];
    private final double[] bandL = new double[3], bandR = new double[3];

    // widener
    private final Bq sideHp = new Bq();

    // limiter
    private int lookN = 66;
    private float[] audioRing = new float[2 * 65];
    private int audioIdx;
    private float[] reqRing = new float[66], minRing = new float[66];
    private int reqIdx, minIdx;
    private double minSum, limGain = 1, limRel;
    private float winMin = 1f;

    // stats accumulation
    private int statPos;
    private float statPeak, statLim = 1, statComp;

    public BtDsp() {
        for (int i = 0; i < BANDS; i++) eq[i] = new Bq();
    }

    public void setParams(Params p) {
        want = p;
    }

    public boolean enabled() {
        return want.enabled;
    }

    /** Samples (frames) of delay between input and output: the limiter's look-ahead. */
    public int latency() {
        return lookN - 1;
    }

    public void configure(int sampleRate, int channels) {
        fs = Math.max(8000, sampleRate);
        ch = Math.max(1, Math.min(2, channels));
        cur = null;
        kA[0].kShelf(fs);
        kA[1].kShelf(fs);
        kB[0].kRlb(fs);
        kB[1].kRlb(fs);
        blockLen = fs / 10;

        lowLp1.lp(fs, 180);
        hiHp1.hp(fs, 180);
        midLp2.lp(fs, 2500);
        hiHp2.hp(fs, 2500);
        apLp2.lp(fs, 2500);
        apHp2.hp(fs, 2500);
        comp[0].init(fs, -26, 2.0, 8, 20, 220, 0.30);
        comp[1].init(fs, -24, 2.2, 6, 10, 130, 0.30);
        comp[2].init(fs, -28, 1.8, 6, 3, 80, 0.30);

        vbLp.lowpass(fs, 120, Math.sqrt(0.5));
        vbHp1.highpass(fs, 160, Math.sqrt(0.5));
        vbHp2.highpass(fs, 160, Math.sqrt(0.5));
        sideHp.highpass(fs, 150, Math.sqrt(0.5));

        lookN = Math.max(8, (int) Math.round(0.0015 * fs));
        audioRing = new float[(lookN - 1) * 2];
        reqRing = new float[lookN];
        minRing = new float[lookN];
        limRel = 1 - Math.exp(-1.0 / (0.060 * fs));
        reset();
    }

    /** Clears all signal state (seek, new track, route change). The smoothed loudness gain is kept on purpose. */
    public void reset() {
        for (Bq b : kA) b.clear();
        for (Bq b : kB) b.clear();
        for (Bq b : eq) b.clear();
        vbLp.clear();
        vbHp1.clear();
        vbHp2.clear();
        sideHp.clear();
        lowLp1.clear();
        hiHp1.clear();
        midLp2.clear();
        hiHp2.clear();
        apLp2.clear();
        apHp2.clear();
        for (Comp c : comp) c.env = 0;
        blockSum = 0;
        blockPos = 0;
        subCount = 0;
        blocksSeen = 0;
        java.util.Arrays.fill(sub, 0);
        java.util.Arrays.fill(histN, 0);
        java.util.Arrays.fill(histE, 0);
        java.util.Arrays.fill(audioRing, 0f);
        java.util.Arrays.fill(reqRing, 1f);
        java.util.Arrays.fill(minRing, 1f);
        audioIdx = reqIdx = minIdx = 0;
        minSum = lookN;
        winMin = 1f;
        limGain = 1;
        statPos = 0;
        statPeak = 0;
        statLim = 1;
        statComp = 0;
    }

    /** Magnitude (dB) of a biquad at frequency f. */
    private static double magDb(Bq q, double f, double fs) {
        double w = 2 * Math.PI * f / fs, c1 = Math.cos(w), s1 = Math.sin(w), c2 = Math.cos(2 * w), s2 = Math.sin(2 * w);
        double nr = q.b0 + q.b1 * c1 + q.b2 * c2, ni = -(q.b1 * s1 + q.b2 * s2);
        double dr = 1 + q.a1 * c1 + q.a2 * c2, di = -(q.a1 * s1 + q.a2 * s2);
        double num = nr * nr + ni * ni, den = dr * dr + di * di;
        return 10 * Math.log10(Math.max(num, 1e-30) / Math.max(den, 1e-30));
    }

    private final double[] fitGain = new double[BANDS];
    private final Bq probe = new Bq();

    /**
     * Neighbouring peaking filters overlap, so setting each band to its nominal gain gives a response that is
     * off at the centres. A few damped fixed-point iterations make the cascade hit the requested curve at all
     * ten centres.
     */
    private void fitEq(Params p) {
        double[] target = new double[BANDS];
        for (int i = 0; i < BANDS; i++) {
            target[i] = Math.max(-12, Math.min(12, p.eq[i]));
            fitGain[i] = target[i];
        }
        boolean any = false;
        for (double t : target) if (Math.abs(t) >= 0.05) any = true;
        if (!any) return;
        for (int it = 0; it < 40; it++) {
            double worst = 0;
            for (int i = 0; i < BANDS; i++) {
                if (FREQ[i] >= 0.45 * fs) continue;
                double tot = 0;
                for (int j = 0; j < BANDS; j++) {
                    if (FREQ[j] >= 0.45 * fs || Math.abs(fitGain[j]) < 0.001) continue;
                    probe.peaking(fs, FREQ[j], fitGain[j], 1.41);
                    tot += magDb(probe, FREQ[i], fs);
                }
                double err = target[i] - tot;
                worst = Math.max(worst, Math.abs(err));
                fitGain[i] = Math.max(-18, Math.min(18, fitGain[i] + 0.7 * err));
            }
            if (worst < 0.02) break;
        }
    }

    private void applyParams(Params p) {
        cur = p;
        fitEq(p);
        double maxUp = 0;
        for (int i = 0; i < BANDS; i++) {
            double g = fitGain[i];
            boolean on = Math.abs(g) >= 0.05 && FREQ[i] < 0.45 * fs;
            if (on) {
                eq[i].peaking(fs, FREQ[i], g, 1.41);
                if (!eqOn[i]) eq[i].clear();
            } else {
                eq[i].ident();
                eq[i].clear();
            }
            eqOn[i] = on;
        }
        // headroom for the largest boost of the real combined response, not just the nominal gains
        for (int i = 0; i < BANDS; i++) {
            double tot = 0;
            for (int j = 0; j < BANDS; j++) if (eqOn[j]) tot += magDb(eq[j], FREQ[i], fs);
            maxUp = Math.max(maxUp, tot);
        }
        headroomDb = 0.5 * maxUp;
    }

    // ------------------------------------------------------------------ processing
    /** In place, interleaved, length >= frames*channels. Output is delayed by latency() frames. */
    public void process(float[] buf, int frames) {
        Params p = want;
        if (p != cur) applyParams(p);
        final boolean st = ch == 2;
        final double ceil = Math.pow(10, p.ceilingDb / 20);
        final boolean norm = !Float.isNaN(p.targetLufs);
        final int D = lookN - 1;
        for (int n = 0; n < frames; n++) {
            double l = buf[n * ch], r = st ? buf[n * ch + 1] : l;

            // ---- analysis (BS.1770 K-weighting of the untouched input)
            double kl = kB[0].run(kA[0].run(l, 0), 0);
            double e = kl * kl;
            if (st) {
                double kr = kB[0].run(kA[0].run(r, 1), 1);
                e += kr * kr;
            }
            blockSum += e;
            if (++blockPos >= blockLen) endBlock(norm ? p.targetLufs : Float.NaN);

            // ---- normalization + EQ headroom
            normLinSm += (normLin - normLinSm) * 0.0015;
            double pre = normLinSm * Math.pow(10, -headroomDb / 20);
            l *= pre;
            r *= pre;
            for (int i = 0; i < BANDS; i++) {
                if (!eqOn[i]) continue;
                l = eq[i].run(l, 0);
                if (st) r = eq[i].run(r, 1);
            }

            // ---- virtual bass
            if (p.vbass) {
                double bl = vbLp.run(l, 0), hl = Math.tanh(4 * bl) * 0.25;
                hl = vbHp2.run(vbHp1.run(hl, 0), 0);
                double hr = 0;
                if (st) {
                    // right channel shares the filter objects through channel index 1
                    double br = vbLp.run(r, 1);
                    hr = Math.tanh(4 * br) * 0.25;
                    hr = vbHp2.run(vbHp1.run(hr, 1), 1);
                }
                l += 0.9 * hl;
                if (st) r += 0.9 * hr;
            }

            // ---- 3-band compressor
            if (p.comp) {
                double[] o = compress(l, r, st);
                l = o[0];
                r = o[1];
            }

            // ---- widener
            if (p.wide && st) {
                double m = 0.5 * (l + r), s = 0.5 * (l - r);
                s += 0.4 * sideHp.run(s, 0);
                l = m + s;
                r = m - s;
            }

            // ---- look-ahead limiter
            float pk = (float) Math.max(Math.abs(l), Math.abs(r));
            float req = pk > ceil ? (float) (ceil / pk) : 1f;
            float old = reqRing[reqIdx];
            reqRing[reqIdx] = req;
            reqIdx = (reqIdx + 1) % lookN;
            if (req <= winMin) {
                winMin = req;
            } else if (old <= winMin) {
                float m2 = 1f;
                for (int k = 0; k < lookN; k++) if (reqRing[k] < m2) m2 = reqRing[k];
                winMin = m2;
            }
            float wm = winMin;
            minSum += wm - minRing[minIdx];
            minRing[minIdx] = wm;
            minIdx = (minIdx + 1) % lookN;
            double avg = minSum / lookN;
            if (avg < limGain) limGain = avg;
            else limGain += (avg - limGain) * limRel;
            if (limGain > avg) limGain = avg;

            float ol, orr = 0;
            if (D > 0) {
                int i0 = audioIdx * 2;
                ol = audioRing[i0];
                orr = audioRing[i0 + 1];
                audioRing[i0] = (float) l;
                audioRing[i0 + 1] = (float) r;
                audioIdx = (audioIdx + 1) % D;
            } else {
                ol = (float) l;
                orr = (float) r;
            }
            ol *= (float) limGain;
            orr *= (float) limGain;
            buf[n * ch] = ol;
            if (st) buf[n * ch + 1] = orr;

            // ---- stats
            float a = Math.max(Math.abs(ol), Math.abs(orr));
            if (a > statPeak) statPeak = a;
            if (limGain < statLim) statLim = (float) limGain;
            if (++statPos >= fs / 4) {
                stats.peakDb = 20f * (float) Math.log10(Math.max(statPeak, 1e-6f));
                stats.limDb = 20f * (float) Math.log10(Math.max(statLim, 1e-6f));
                stats.compDb = statComp;
                stats.normDb = (float) normDb;
                stats.stampNs = System.nanoTime();
                statPos = 0;
                statPeak = 0;
                statLim = 1;
                statComp = 0;
            }
        }
    }

    private final double[] cOut = new double[2];

    private double[] compress(double l, double r, boolean st) {
        // split into low / mid / high; the low band goes through an all-pass (LP+HP of the 2.5 kHz crossover)
        // so that the three bands sum back to a flat response
        double h1 = hiHp1.run(l, 0);
        double lowL = lowLp1.run(l, 0), midL = midLp2.run(h1, 0), hiL = hiHp2.run(h1, 0);
        lowL = apLp2.run(lowL, 0) + apHp2.run(lowL, 0);
        double lowR = 0, midR = 0, hiR = 0;
        if (st) {
            double h2 = hiHp1.run(r, 1);
            lowR = lowLp1.run(r, 1);
            midR = midLp2.run(h2, 1);
            hiR = hiHp2.run(h2, 1);
            lowR = apLp2.run(lowR, 1) + apHp2.run(lowR, 1);
        }
        bandL[0] = lowL;
        bandL[1] = midL;
        bandL[2] = hiL;
        bandR[0] = lowR;
        bandR[1] = midR;
        bandR[2] = hiR;
        double outL = 0, outR = 0;
        for (int b = 0; b < 3; b++) {
            double lvl = st ? Math.max(Math.abs(bandL[b]), Math.abs(bandR[b])) : Math.abs(bandL[b]);
            double g = comp[b].gain(lvl, gr);
            if (testNoMakeup) g /= comp[b].makeup;
            if (gr[0] > statComp) statComp = (float) gr[0];
            outL += bandL[b] * g;
            outR += bandR[b] * g;
        }
        cOut[0] = outL;
        cOut[1] = st ? outR : outL;
        return cOut;
    }

    private void endBlock(float target) {
        double mean = blockSum / blockLen;   // sum over channels of mean-square
        blockSum = 0;
        blockPos = 0;
        sub[subCount % 4] = mean;
        subCount++;
        if (subCount < 4) return;
        double m = (sub[0] + sub[1] + sub[2] + sub[3]) / 4;
        double lm = -0.691 + 10 * Math.log10(Math.max(m, 1e-12));
        stats.lufsM = (float) lm;
        if (lm >= -70) {
            int bin = (int) Math.floor((lm + 70) / 0.1);
            bin = Math.max(0, Math.min(799, bin));
            histN[bin]++;
            histE[bin] += m;
        }
        // integrated loudness with the absolute (-70) and relative (-10 LU) gates
        long n = 0;
        double sum = 0;
        for (int i = 0; i < 800; i++) {
            n += histN[i];
            sum += histE[i];
        }
        double li = Double.NaN;
        if (n > 0) {
            double rel = -0.691 + 10 * Math.log10(sum / n) - 10;
            long n2 = 0;
            double s2 = 0;
            int from = (int) Math.ceil((rel + 70) / 0.1);
            for (int i = Math.max(0, from); i < 800; i++) {
                n2 += histN[i];
                s2 += histE[i];
            }
            if (n2 > 0) li = -0.691 + 10 * Math.log10(s2 / n2);
        }
        blocksSeen++;
        stats.lufsI = (float) li;
        double tgt = 0;
        if (!Float.isNaN(target) && !Double.isNaN(li)) {
            tgt = Math.max(-12, Math.min(9, target - li));
            if (blocksSeen < 30) tgt = Math.max(-6, Math.min(6, tgt));   // careful until ~3 s have been heard
        } else if (!Float.isNaN(target)) {
            tgt = normDb;       // nothing measured yet: keep the current gain
        }
        normDb += (tgt - normDb) * (1 - Math.exp(-0.1 / 1.0));
        normLin = Math.pow(10, normDb / 20);
    }
}
